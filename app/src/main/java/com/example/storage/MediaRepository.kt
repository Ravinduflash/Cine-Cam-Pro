package com.example.storage

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaRepository
 *
 * Coroutines-based repository that queries Android 11 MediaStore and internal storage
 * for cinema video clips created by CineCam.
 */
class MediaRepository(private val context: Context) {

    private val tag = "MediaRepository"

    private val _clips = MutableStateFlow<List<CinemaClip>>(emptyList())
    val clips: Flow<List<CinemaClip>> = _clips.asStateFlow()

    /**
     * Observes real-time changes to the MediaStore video collection via ContentObserver.
     */
    val mediaStoreFlow: Flow<List<CinemaClip>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(queryClipsSync())
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        // Initial emission
        trySend(queryClipsSync())

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun refreshClips(): List<CinemaClip> {
        return withContext(Dispatchers.IO) {
            val list = queryClipsSync()
            _clips.value = list
            list
        }
    }

    /**
     * Queries MediaStore.Video.Media for all CineCam recordings,
     * merging with local scoped storage directory for full reliability.
     */
    fun queryClipsSync(): List<CinemaClip> {
        val result = mutableListOf<CinemaClip>()
        val seenFileNames = mutableSetOf<String>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        // Filter for CineCam clips or files with CINECAM prefix
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%CineCam%", "CINECAM%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "CINECAM_CLIP"
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    seenFileNames.add(name)

                    result.add(
                        CinemaClip(
                            id = id,
                            uri = uri,
                            title = name,
                            durationMs = duration,
                            width = if (width > 0) width else 1920,
                            height = if (height > 0) height else 1080,
                            sizeBytes = size,
                            dateAddedSec = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed querying MediaStore video library", e)
        }

        // Secondary check: query local Scoped Storage Movies/CineCam folder
        try {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val cineCamDir = File(moviesDir, "CineCam")
            if (cineCamDir.exists()) {
                val files = cineCamDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".mp4", ignoreCase = true) || file.name.startsWith("CINECAM"))
                } ?: emptyArray()

                for (file in files) {
                    if (!seenFileNames.contains(file.name)) {
                        seenFileNames.add(file.name)
                        result.add(
                            CinemaClip(
                                id = file.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                title = file.name,
                                durationMs = 0L,
                                width = 1920,
                                height = 1080,
                                sizeBytes = file.length(),
                                dateAddedSec = file.lastModified() / 1000L,
                                filePath = file.absolutePath
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed scanning local CineCam directory", e)
        }

        return result.sortedByDescending { it.dateAddedSec }
    }

    /**
     * Deletes a clip from MediaStore and filesystem.
     */
    suspend fun deleteClip(clip: CinemaClip): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        if (clip.uri.scheme == "content") {
            deleted = MediaStoreHelper.deleteVideo(context, clip.uri)
        } else if (clip.uri.scheme == "file" || clip.filePath.isNotEmpty()) {
            val file = if (clip.filePath.isNotEmpty()) File(clip.filePath) else clip.uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                deleted = file.delete()
            }
        }
        refreshClips()
        deleted
    }
}
