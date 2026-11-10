package com.example.encoder

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.example.model.RecordedClip
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoResolution
import java.io.File
import java.util.Locale

/**
 * CinemaVideoRecorder
 *
 * Coordinates the professional dual-surface recording pipeline:
 * - Directs clean sensor frames into VideoEncoderManager (H.265 MediaCodec).
 * - Directs processed audio with digital gain and mic routing into AudioEncoderManager (AAC MediaCodec).
 * - Asynchronously multiplexes streams via MediaMuxerManager into an MP4 container.
 * - Releases finished files into Android MediaStore via RecordingState.
 */
class CinemaVideoRecorder(private val context: Context) {

    private val tag = "CinemaVideoRecorder"

    val recordingState = RecordingState(context)

    private var muxerManager: MediaMuxerManager? = null
    private var videoEncoder: VideoEncoderManager? = null
    private var audioEncoder: AudioEncoderManager? = null

    val isRecording: Boolean
        get() = recordingState.status == RecordingState.Status.RECORDING

    var recordingStartTimeMs: Long = 0L
        private set

    var onRecordingStarted: ((String) -> Unit)? = null
    var onRecordingFinished: ((RecordedClip) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Starts the full recording pipeline.
     * Returns the MediaCodec input Surface that must be registered with CameraCaptureSession.
     */
    fun startRecording(
        codec: VideoCodec = VideoCodec.HEVC,
        fps: VideoFps = VideoFps.FPS_24,
        resolution: VideoResolution = VideoResolution.FHD_1080P,
        bitrateMbps: Int = 80,
        micGainDb: Float = 0f,
        micSource: String = "Internal Stereo (Built-in)"
    ): Surface? {
        if (isRecording) return null

        try {
            val outputFile = recordingState.beginSession()
            recordingStartTimeMs = SystemClock.uptimeMillis()

            // 1. Initialize MediaMuxer for Video + Audio tracks
            val muxer = MediaMuxerManager(outputFile, expectedTrackCount = 2)
            muxerManager = muxer

            // 2. Initialize Video Encoder (H.265 / HEVC with input Surface)
            val vEncoder = VideoEncoderManager(
                muxerManager = muxer,
                resolution = resolution,
                fps = fps,
                bitrateMbps = bitrateMbps,
                preferredCodec = codec
            )
            videoEncoder = vEncoder

            // 3. Initialize Audio Encoder (AAC with microphone routing & digital gain)
            val aEncoder = AudioEncoderManager(
                context = context,
                muxerManager = muxer,
                digitalGainDb = micGainDb
            ).apply {
                applyMicrophoneRouting(micSource)
            }
            audioEncoder = aEncoder

            // 4. Start Encoders
            vEncoder.start()
            aEncoder.start()

            onRecordingStarted?.invoke(outputFile.absolutePath)
            Log.i(tag, "Started CineCam recording pipeline: ${outputFile.name}, ${resolution.label} @ ${fps.fps}fps, ${vEncoder.activeMimeType}")

            return vEncoder.inputSurface
        } catch (e: Exception) {
            Log.e(tag, "Failed to start recording pipeline", e)
            recordingState.markError(e.message ?: "Encoder initialization error")
            onError?.invoke("Failed to initialize recording: ${e.message}")
            stopRecording(fps, resolution)
            return null
        }
    }

    /**
     * Gracefully stops the encoding pipeline, drains remaining frames, closes the muxer,
     * and finalizes the MP4 in MediaStore.
     */
    fun stopRecording(
        fps: VideoFps = VideoFps.FPS_24,
        resolution: VideoResolution = VideoResolution.FHD_1080P
    ): RecordedClip? {
        if (recordingState.status != RecordingState.Status.RECORDING &&
            recordingState.status != RecordingState.Status.PREPARING) {
            return null
        }

        recordingState.beginStopping()
        val durationMs = (SystemClock.uptimeMillis() - recordingStartTimeMs).coerceAtLeast(0L)
        val durationSeconds = (durationMs / 1000L).coerceAtLeast(1L)

        // Stop video & audio encoders (signals EOS)
        try {
            videoEncoder?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping video encoder", e)
        }
        videoEncoder = null

        try {
            audioEncoder?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping audio encoder", e)
        }
        audioEncoder = null

        // Stop MediaMuxer
        try {
            muxerManager?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping muxer", e)
        }
        muxerManager = null

        // Finalize and register in MediaStore
        val clip = recordingState.finalizeSession(fps, resolution, durationSeconds)
        if (clip != null) {
            onRecordingFinished?.invoke(clip)
        }
        return clip
    }

    fun formatTimecode(durationMs: Long, fps: Int): String {
        val totalSeconds = durationMs / 1000
        val frames = ((durationMs % 1000) * fps / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }
}
