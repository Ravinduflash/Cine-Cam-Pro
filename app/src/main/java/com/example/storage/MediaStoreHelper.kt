package com.example.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * MediaStoreHelper
 *
 * Implements Android 11+ (API 30+) Scoped Storage integration for MediaStore.Video.
 * Safely manages IS_PENDING flags during MediaMuxer file creation and writes.
 */
object MediaStoreHelper {

    private const val TAG = "MediaStoreHelper"
    const val RELATIVE_SUBDIRECTORY = "Movies/CineCam"

    /**
     * Inserts a new video entry into MediaStore with IS_PENDING = 1.
     * MediaMuxer can write to the returned URI via its FileDescriptor without system scanner interference.
     */
    fun createPendingVideo(
        context: Context,
        displayName: String,
        mimeType: String = "video/mp4",
        width: Int = 1920,
        height: Int = 1080
    ): Uri? {
        val resolver = context.contentResolver
        val nowSec = System.currentTimeMillis() / 1000L

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.WIDTH, width)
            put(MediaStore.Video.Media.HEIGHT, height)
            put(MediaStore.Video.Media.DATE_ADDED, nowSec)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_SUBDIRECTORY)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        return try {
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            Log.i(TAG, "Created pending MediaStore video entry: $uri (IS_PENDING=1)")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert pending video in MediaStore", e)
            null
        }
    }

    /**
     * Opens a read/write ParcelFileDescriptor for the MediaStore video URI.
     */
    fun openFileDescriptor(context: Context, uri: Uri, mode: String = "rw"): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, mode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ParcelFileDescriptor for: $uri", e)
            null
        }
    }

    /**
     * Updates IS_PENDING to 0 and writes final duration and file size to MediaStore.
     * The video becomes immediately visible across gallery apps and system video players.
     */
    fun finishPendingVideo(
        context: Context,
        uri: Uri,
        durationMs: Long,
        fileSizeBytes: Long
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
            if (durationMs > 0) {
                put(MediaStore.Video.Media.DURATION, durationMs)
            }
            if (fileSizeBytes > 0) {
                put(MediaStore.Video.Media.SIZE, fileSizeBytes)
            }
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
        }

        return try {
            val rows = resolver.update(uri, values, null, null)
            Log.i(TAG, "Updated MediaStore record $uri: IS_PENDING=0 (rows: $rows, size: $fileSizeBytes bytes)")
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize MediaStore video: $uri", e)
            false
        }
    }

    /**
     * Publishes a completed temporary file into MediaStore with IS_PENDING safety.
     */
    fun publishFileToMediaStore(
        context: Context,
        sourceFile: File,
        width: Int,
        height: Int,
        durationSeconds: Long
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.w(TAG, "Source file missing or empty: ${sourceFile.absolutePath}")
            return null
        }

        val pendingUri = createPendingVideo(
            context = context,
            displayName = sourceFile.name,
            mimeType = "video/mp4",
            width = width,
            height = height
        ) ?: return null

        try {
            context.contentResolver.openOutputStream(pendingUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            finishPendingVideo(
                context = context,
                uri = pendingUri,
                durationMs = durationSeconds * 1000L,
                fileSizeBytes = sourceFile.length()
            )
            Log.i(TAG, "Successfully copied and published file to MediaStore: $pendingUri")
            return pendingUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish file to MediaStore", e)
            // Cleanup pending URI on failure
            deleteVideo(context, pendingUri)
            return null
        }
    }

    /**
     * Deletes a video URI from MediaStore.
     */
    fun deleteVideo(context: Context, uri: Uri): Boolean {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            Log.i(TAG, "Deleted MediaStore video: $uri (rows: $rows)")
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete video URI: $uri", e)
            false
        }
    }
}
