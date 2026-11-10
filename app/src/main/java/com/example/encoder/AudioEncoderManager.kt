package com.example.encoder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * AudioEncoderManager
 *
 * Manages raw audio capture with AudioRecord (16-bit stereo PCM, 48 kHz),
 * manual microphone input routing (Built-in mic vs USB-C/External interface),
 * real-time software digital gain processing,
 * and high-fidelity AAC audio encoding (320 kbps) via MediaCodec.
 */
class AudioEncoderManager(
    private val context: Context,
    private val muxerManager: MediaMuxerManager,
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 2,
    private val bitrate: Int = 320_000,
    var digitalGainDb: Float = 0.0f
) {
    private val tag = "AudioEncoderManager"

    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null

    private var recordThread: Thread? = null
    private var drainThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    private var audioTrackIndex = -1
    private var bufferSizeInBytes: Int = 0

    // Preferred input device type (e.g. "USB", "INTERNAL")
    @Volatile var preferredSourceType: String = "INTERNAL"

    init {
        setupCodec()
        setupAudioRecord()
    }

    private fun setupCodec() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        try {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec = codec
            Log.i(tag, "Audio encoder initialized: AAC-LC @ ${sampleRate}Hz, $channelCount ch, ${bitrate / 1000}kbps")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AAC audio encoder", e)
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioRecord() {
        val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        bufferSizeInBytes = (minBufSize * 2).coerceAtLeast(8192)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                sampleRate,
                channelConfig,
                audioEncoding,
                bufferSizeInBytes
            )

            applyMicrophoneRouting()
            Log.i(tag, "AudioRecord initialized. Buffer size: $bufferSizeInBytes bytes")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioRecord", e)
        }
    }

    /**
     * Inspects attached hardware devices and routes AudioRecord to USB-C or Built-in mic.
     */
    fun applyMicrophoneRouting(sourceType: String = preferredSourceType) {
        this.preferredSourceType = sourceType
        val record = audioRecord ?: return

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            var selectedDevice: AudioDeviceInfo? = null
            if (sourceType.contains("USB", ignoreCase = true)) {
                selectedDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
            } else if (sourceType.contains("WIRED", ignoreCase = true)) {
                selectedDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
            }

            if (selectedDevice == null) {
                // Default to built-in microphone
                selectedDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                }
            }

            if (selectedDevice != null) {
                record.setPreferredDevice(selectedDevice)
                Log.i(tag, "Audio routed to microphone: ${selectedDevice.productName} (type: ${selectedDevice.type})")
            }
        } catch (e: Exception) {
            Log.w(tag, "Microphone routing failed or unsupported: ${e.message}")
        }
    }

    fun start() {
        val codec = mediaCodec ?: throw IllegalStateException("Audio MediaCodec is null")
        val record = audioRecord ?: throw IllegalStateException("AudioRecord is null")

        if (isRunning.getAndSet(true)) return

        codec.start()
        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord recording", e)
            isRunning.set(false)
            return
        }

        // 1. Thread reading raw PCM and feeding into MediaCodec input buffers
        recordThread = Thread({
            captureAndFeedAudio()
        }, "CineCamAudioRecordThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        // 2. Thread draining encoded AAC frames into MediaMuxer
        drainThread = Thread({
            drainAudioEncoder()
        }, "CineCamAudioDrainThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        Log.i(tag, "Audio recording and encoding pipeline active")
    }

    private fun captureAndFeedAudio() {
        val record = audioRecord ?: return
        val codec = mediaCodec ?: return
        val shortBuffer = ShortArray(bufferSizeInBytes / 2)
        val byteBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes)

        val startTimeUs = SystemClock.elapsedRealtimeNanos() / 1000L
        var totalSamplesRead = 0L

        while (isRunning.get()) {
            val shortsRead = record.read(shortBuffer, 0, shortBuffer.size)
            if (shortsRead <= 0) continue

            // Apply digital software gain with soft-clipping saturation
            val gainMultiplier = 10.0.pow(digitalGainDb / 20.0).toFloat()
            byteBuffer.clear()

            for (i in 0 until shortsRead) {
                var sample = (shortBuffer[i] * gainMultiplier).toInt()
                // Clamp within 16-bit integer bounds
                if (sample > 32767) sample = 32767
                else if (sample < -32768) sample = -32768
                byteBuffer.putShort(sample.toShort())
            }
            byteBuffer.flip()

            val bytesToWrite = shortsRead * 2
            val inputBufferIndex = codec.dequeueInputBuffer(10_000L)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(byteBuffer)

                    // Accurate presentation timestamp calculated from continuous audio clock
                    val ptsUs = startTimeUs + (totalSamplesRead * 1_000_000L / (sampleRate * channelCount))
                    totalSamplesRead += shortsRead

                    codec.queueInputBuffer(inputBufferIndex, 0, bytesToWrite, ptsUs, 0)
                }
            }
        }

        // Submit End of Stream signal
        try {
            val eosIndex = codec.dequeueInputBuffer(10_000L)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(eosIndex, 0, 0, SystemClock.elapsedRealtimeNanos() / 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to queue audio EOS", e)
        }
    }

    private fun drainAudioEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        while (isRunning.get()) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when (outputBufferIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(tag, "Audio encoder output format changed: $newFormat")
                        audioTrackIndex = muxerManager.addTrack(newFormat, isVideo = false)
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedData = codec.getOutputBuffer(outputBufferIndex)
                            if (encodedData != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size != 0 && muxerManager.isStarted) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxerManager.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                                }

                                codec.releaseOutputBuffer(outputBufferIndex, false)

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    Log.i(tag, "Audio encoder reached END_OF_STREAM")
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception in audio drain loop", e)
                break
            }
        }
    }

    fun stopAndRelease() {
        if (!isRunning.getAndSet(false)) return

        try {
            recordThread?.join(1000)
        } catch (e: Exception) {
            Log.w(tag, "Error awaiting recordThread", e)
        }
        recordThread = null

        try {
            drainThread?.join(1000)
        } catch (e: Exception) {
            Log.w(tag, "Error awaiting drainThread", e)
        }
        drainThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping audioRecord", e)
        }
        audioRecord = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing audio codec", e)
        }
        mediaCodec = null

        Log.i(tag, "Audio pipeline released")
    }
}
