package com.example.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaMuxerManager
 *
 * Thread-safe manager for Android MediaMuxer.
 * Coordinates asynchronous writes from the H.265 video encoder and AAC audio encoder
 * into a single synchronized MP4 container.
 */
class MediaMuxerManager(
    private val outputFile: File,
    private val expectedTrackCount: Int = 2
) {
    private val tag = "MediaMuxerManager"

    private var mediaMuxer: MediaMuxer? = null
    private val lock = Any()

    @Volatile
    var isStarted: Boolean = false
        private set

    private val isClosed = AtomicBoolean(false)
    private var registeredTrackCount = 0

    var videoTrackIndex: Int = -1
        private set
    var audioTrackIndex: Int = -1
        private set

    // Presentation timestamp trackers to guarantee strictly increasing timestamps
    private var lastVideoPtsUs: Long = -1L
    private var lastAudioPtsUs: Long = -1L

    init {
        try {
            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(tag, "Failed to create MediaMuxer for: ${outputFile.absolutePath}", e)
            throw e
        }
    }

    /**
     * Adds an encoded track (Video or Audio).
     * Once all expected tracks are added, MediaMuxer starts automatically.
     */
    fun addTrack(format: MediaFormat, isVideo: Boolean): Int {
        synchronized(lock) {
            if (isStarted || isClosed.get()) {
                Log.w(tag, "Cannot add track: Muxer already started or closed")
                return if (isVideo) videoTrackIndex else audioTrackIndex
            }

            val muxer = mediaMuxer ?: throw IllegalStateException("MediaMuxer is null")
            val trackIndex = muxer.addTrack(format)

            if (isVideo) {
                videoTrackIndex = trackIndex
                Log.i(tag, "Registered Video Track at index $trackIndex: $format")
            } else {
                audioTrackIndex = trackIndex
                Log.i(tag, "Registered Audio Track at index $trackIndex: $format")
            }

            registeredTrackCount++
            if (registeredTrackCount >= expectedTrackCount) {
                startMuxer()
            }

            return trackIndex
        }
    }

    private fun startMuxer() {
        try {
            mediaMuxer?.start()
            isStarted = true
            Log.i(tag, "MediaMuxer started with $registeredTrackCount tracks. Writing to: ${outputFile.name}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start MediaMuxer", e)
            throw e
        }
    }

    /**
     * Writes encoded sample data safely into the MP4 container.
     */
    fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted || isClosed.get() || mediaMuxer == null) {
                return
            }

            if (trackIndex < 0) {
                Log.w(tag, "Invalid track index: $trackIndex")
                return
            }

            // Reject invalid sample sizes
            if (bufferInfo.size < 0) {
                return
            }

            // Ensure strictly monotonically increasing PTS
            var presentationTimeUs = bufferInfo.presentationTimeUs
            if (trackIndex == videoTrackIndex) {
                if (presentationTimeUs <= lastVideoPtsUs) {
                    presentationTimeUs = lastVideoPtsUs + 1000L // Ensure 1ms increment
                    bufferInfo.presentationTimeUs = presentationTimeUs
                }
                lastVideoPtsUs = presentationTimeUs
            } else if (trackIndex == audioTrackIndex) {
                if (presentationTimeUs <= lastAudioPtsUs) {
                    presentationTimeUs = lastAudioPtsUs + 1000L
                    bufferInfo.presentationTimeUs = presentationTimeUs
                }
                lastAudioPtsUs = presentationTimeUs
            }

            try {
                mediaMuxer?.writeSampleData(trackIndex, byteBuffer, bufferInfo)
            } catch (e: Exception) {
                Log.e(tag, "Error writing sample data to track $trackIndex", e)
            }
        }
    }

    /**
     * Stops the MediaMuxer and safely finalizes the MP4 file.
     */
    fun stopAndRelease(): Boolean {
        synchronized(lock) {
            if (isClosed.getAndSet(true)) {
                return true
            }

            var success = false
            try {
                if (isStarted) {
                    isStarted = false
                    mediaMuxer?.stop()
                    Log.i(tag, "MediaMuxer stopped successfully: ${outputFile.name} (${outputFile.length()} bytes)")
                    success = true
                }
            } catch (e: IllegalStateException) {
                // Occurs if zero frames were written before stop was called
                Log.w(tag, "MediaMuxer stopped with insufficient data: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "Error stopping MediaMuxer", e)
            } finally {
                try {
                    mediaMuxer?.release()
                } catch (e: Exception) {
                    Log.w(tag, "Error releasing MediaMuxer", e)
                }
                mediaMuxer = null
            }
            return success
        }
    }
}
