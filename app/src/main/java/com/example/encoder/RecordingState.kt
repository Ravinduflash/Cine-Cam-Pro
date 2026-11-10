package com.example.encoder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.RecordedClip
import com.example.model.VideoFps
import com.example.model.VideoResolution
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * RecordingState
 *
 * Thread-safe lifecycle and state manager for cinema recording.
 * Coordinates output file allocation and insertion into Android MediaStore.
 */
class RecordingState(private val context: Context) {

    private val tag = "RecordingState"

    enum class Status {
        IDLE,
        PREPARING,
        RECORDING,
        STOPPING,
        FINALIZING,
        ERROR
    }

    private val currentStatus = AtomicReference(Status.IDLE)
    var status: Status
        get() = currentStatus.get()
        private set(value) = currentStatus.set(value)

    var currentFile: File? = null
        private set
    var currentUri: Uri? = null
        private set
    var startTimeMs: Long = 0L
        private set

    /**
     * Attempts to transition to PREPARING and creates a new destination MP4 file.
     */
    fun beginSession(): File {
        if (!currentStatus.compareAndSet(Status.IDLE, Status.PREPARING)) {
            throw IllegalStateException("Cannot begin recording from state: $status")
        }

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "CINECAM_${timestampStr}.mp4"

        // 1. Primary destination in Scoped Movies/CineCam folder
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val cineCamDir = File(moviesDir, "CineCam")
        if (!cineCamDir.exists()) cineCamDir.mkdirs()

        val file = File(cineCamDir, fileName)
        currentFile = file
        currentUri = null
        startTimeMs = System.currentTimeMillis()

        status = Status.RECORDING
        Log.i(tag, "Recording session begun: ${file.absolutePath}")
        return file
    }

    /**
     * Transitions to STOPPING state.
     */
    fun beginStopping() {
        currentStatus.set(Status.STOPPING)
    }

    /**
     * Finalizes recording and indexes the MP4 into Android MediaStore.
     */
    fun finalizeSession(
        fps: VideoFps,
        resolution: VideoResolution,
        durationSeconds: Long
    ): RecordedClip? {
        status = Status.FINALIZING
        val file = currentFile

        if (file == null || !file.exists() || file.length() == 0L) {
            Log.w(tag, "No recorded file or file is empty upon finalization")
            status = Status.IDLE
            return null
        }

        // Index in Android MediaStore using MediaStoreHelper with Scoped Storage IS_PENDING handling
        val mediaStoreUri = com.example.storage.MediaStoreHelper.publishFileToMediaStore(
            context = context,
            sourceFile = file,
            width = resolution.width,
            height = resolution.height,
            durationSeconds = durationSeconds
        )
        currentUri = mediaStoreUri

        val clip = RecordedClip(
            filePath = file.absolutePath,
            fileName = file.name,
            durationSeconds = durationSeconds,
            resolution = resolution.label,
            fps = fps.fps,
            timestamp = file.lastModified()
        )

        status = Status.IDLE
        Log.i(tag, "Recording session successfully finalized: ${clip.fileName} (${clip.durationSeconds}s)")
        return clip
    }

    fun markError(message: String) {
        status = Status.ERROR
        Log.e(tag, "RecordingState error: $message")
    }

    fun reset() {
        status = Status.IDLE
        currentFile = null
        currentUri = null
        startTimeMs = 0L
    }
}
