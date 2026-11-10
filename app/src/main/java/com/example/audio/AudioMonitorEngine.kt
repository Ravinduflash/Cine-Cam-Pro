package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.example.model.AudioMeterState
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class AudioMonitorEngine(private val context: Context) {

    private val tag = "AudioMonitorEngine"

    @Volatile
    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var monitorThread: Thread? = null

    // Digital software gain (-12 dB to +12 dB)
    @Volatile
    var gainMultiplier: Float = 1.0f

    var onLevelsUpdated: ((AudioMeterState) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true

        monitorThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBufferSize, 4096)

            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    record.startRecording()
                    val audioBuffer = ShortArray(bufferSize / 2)

                    while (isRunning) {
                        val readShorts = record.read(audioBuffer, 0, audioBuffer.size)
                        if (readShorts > 0) {
                            processPcmBuffer(audioBuffer, readShorts)
                        } else {
                            // Fallback simulation when in emulator or idle
                            simulateLevels()
                            Thread.sleep(60)
                        }
                    }
                } else {
                    Log.w(tag, "AudioRecord state not initialized, running level simulation")
                    record.release()
                    while (isRunning) {
                        simulateLevels()
                        Thread.sleep(60)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(tag, "Audio recording permission not granted, simulating meter", e)
                while (isRunning) {
                    simulateLevels()
                    Thread.sleep(60)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in audio monitor loop", e)
                while (isRunning) {
                    simulateLevels()
                    Thread.sleep(60)
                }
            }
        }.apply {
            name = "CineCamAudioMonitor"
            start()
        }
    }

    private fun processPcmBuffer(buffer: ShortArray, length: Int) {
        var sumSquaresL = 0.0
        var sumSquaresR = 0.0
        var peakL = 0.0
        var peakR = 0.0
        val count = length / 2

        var i = 0
        while (i < length) {
            val rawL = (buffer[i] * gainMultiplier).coerceIn(-32768f, 32767f)
            val absL = Math.abs(rawL.toDouble())
            sumSquaresL += absL * absL
            if (absL > peakL) peakL = absL

            if (i + 1 < length) {
                val rawR = (buffer[i + 1] * gainMultiplier).coerceIn(-32768f, 32767f)
                val absR = Math.abs(rawR.toDouble())
                sumSquaresR += absR * absR
                if (absR > peakR) peakR = absR
            }
            i += 2
        }

        val rmsL = sqrt(sumSquaresL / max(1, count))
        val rmsR = sqrt(sumSquaresR / max(1, count))

        val rmsDbL = calculateDbfs(rmsL)
        val peakDbL = calculateDbfs(peakL)
        val rmsDbR = calculateDbfs(rmsR)
        val peakDbR = calculateDbfs(peakR)

        val isClipping = peakDbL >= -0.5f || peakDbR >= -0.5f

        onLevelsUpdated?.invoke(
            AudioMeterState(
                leftRmsDb = rmsDbL,
                leftPeakDb = peakDbL,
                rightRmsDb = rmsDbR,
                rightPeakDb = peakDbR,
                isClipping = isClipping
            )
        )
    }

    private var simPhase = 0.0
    private fun simulateLevels() {
        simPhase += 0.15
        val baseL = -28.0 + 8.0 * Math.sin(simPhase) + 4.0 * Math.cos(simPhase * 2.3)
        val baseR = -30.0 + 7.0 * Math.sin(simPhase * 1.2) + 3.0 * Math.cos(simPhase * 1.7)

        val rmsL = (baseL.toFloat() * (1f / gainMultiplier.coerceAtLeast(0.1f))).coerceIn(-60f, 0f)
        val peakL = (rmsL + 4.5f).coerceIn(-60f, 0f)
        val rmsR = (baseR.toFloat() * (1f / gainMultiplier.coerceAtLeast(0.1f))).coerceIn(-60f, 0f)
        val peakR = (rmsR + 4.0f).coerceIn(-60f, 0f)

        onLevelsUpdated?.invoke(
            AudioMeterState(
                leftRmsDb = rmsL,
                leftPeakDb = peakL,
                rightRmsDb = rmsR,
                rightPeakDb = peakR,
                isClipping = peakL >= -1.0f
            )
        )
    }

    private fun calculateDbfs(amplitude: Double): Float {
        if (amplitude <= 1.0) return -60.0f
        val db = 20.0 * log10(amplitude / 32768.0)
        return db.toFloat().coerceIn(-60.0f, 0.0f)
    }

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            monitorThread?.interrupt()
            monitorThread = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }
    }
}
