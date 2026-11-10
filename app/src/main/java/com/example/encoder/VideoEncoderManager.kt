package com.example.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoResolution
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VideoEncoderManager
 *
 * Configures hardware-accelerated MediaCodec for H.265 (HEVC) encoding,
 * extracts the native input Surface for Camera2 recording target,
 * and asynchronously drains encoded NAL units to MediaMuxerManager.
 */
class VideoEncoderManager(
    private val muxerManager: MediaMuxerManager,
    private val resolution: VideoResolution = VideoResolution.FHD_1080P,
    private val fps: VideoFps = VideoFps.FPS_24,
    private val bitrateMbps: Int = 80,
    private val preferredCodec: VideoCodec = VideoCodec.HEVC
) {
    private val tag = "VideoEncoderManager"

    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private var drainThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private var videoTrackIndex = -1

    var activeMimeType: String = MediaFormat.MIMETYPE_VIDEO_HEVC
        private set

    init {
        setupCodec()
    }

    private fun setupCodec() {
        val width = resolution.width
        val height = resolution.height
        val frameRate = fps.fps
        val targetBitrate = (bitrateMbps.coerceIn(10, 150)) * 1_000_000

        // Select HEVC if supported by hardware encoder, otherwise fallback to AVC
        val isHevcSupported = hasEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC)
        activeMimeType = if (preferredCodec == VideoCodec.HEVC && isHevcSupported) {
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            Log.w(tag, "HEVC encoder unavailable or AVC requested; falling back to AVC (H.264)")
            MediaFormat.MIMETYPE_VIDEO_AVC
        }

        val format = MediaFormat.createVideoFormat(activeMimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1-second GOP for responsive scrubbing & cinema cuts
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)

            // High Profile configuration for cinematic color reproduction
            if (activeMimeType == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51)
            } else {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
            }
        }

        try {
            val codec = MediaCodec.createEncoderByType(activeMimeType)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            mediaCodec = codec

            Log.i(tag, "Video encoder configured: $activeMimeType, ${width}x${height} @ ${frameRate}fps, ${bitrateMbps}Mbps")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize video encoder for $activeMimeType", e)
            throw e
        }
    }

    /**
     * Starts the encoder and the background drain thread.
     */
    fun start() {
        val codec = mediaCodec ?: throw IllegalStateException("MediaCodec is not initialized")
        if (isRunning.getAndSet(true)) return

        codec.start()
        Log.i(tag, "Video encoder started")

        drainThread = Thread({
            drainEncoder()
        }, "CineCamVideoDrainThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        while (isRunning.get()) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when (outputBufferIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No buffer available right now
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(tag, "Encoder output format changed: $newFormat")
                        videoTrackIndex = muxerManager.addTrack(newFormat, isVideo = true)
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedData = codec.getOutputBuffer(outputBufferIndex)
                            if (encodedData != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    // Codec config data (SPS/PPS)
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size != 0 && muxerManager.isStarted) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxerManager.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                }

                                codec.releaseOutputBuffer(outputBufferIndex, false)

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    Log.i(tag, "Video encoder reached END_OF_STREAM")
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception in video drain loop", e)
                break
            }
        }
        Log.i(tag, "Video drain thread terminated")
    }

    /**
     * Signals End of Stream to the input surface and gracefully awaits drain completion.
     */
    fun stopAndRelease() {
        if (!isRunning.getAndSet(false)) return

        try {
            Log.i(tag, "Signaling EOS to video encoder")
            mediaCodec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(tag, "Failed to signal EOS to video encoder", e)
        }

        try {
            drainThread?.join(2000)
        } catch (e: InterruptedException) {
            Log.w(tag, "Interrupted while waiting for drain thread", e)
        }
        drainThread = null

        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping video codec", e)
        }

        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing video codec", e)
        }
        mediaCodec = null

        try {
            inputSurface?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing input surface", e)
        }
        inputSurface = null

        Log.i(tag, "Video encoder released")
    }

    private fun hasEncoder(mime: String): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) return true
            }
        }
        return false
    }
}
