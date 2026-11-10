package com.example.ui

import android.app.Application
import android.graphics.SurfaceTexture
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioMonitorEngine
import com.example.camera.CameraDeviceManager
import com.example.camera.CameraThreadHandler
import com.example.encoder.CinemaVideoRecorder
import com.example.gl.CameraGlRenderer
import com.example.gl.CameraPreviewRenderer
import com.example.model.AssistTool
import com.example.model.AudioMeterState
import com.example.model.FrameGuide
import com.example.model.PeakingColor
import com.example.model.RecordedClip
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.model.WhiteBalancePreset
import com.example.model.ZebraThreshold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.storage.CinemaClip
import com.example.storage.MediaRepository
import java.util.Locale
import kotlin.math.pow

enum class AppScreen {
    CAMERA,
    GALLERY,
    PLAYBACK
}

enum class DrawerType {
    NONE,
    EXPOSURE,
    FOCUS,
    WHITE_BALANCE,
    AUDIO,
    ASSIST_SETTINGS,
    CLIPS
}

data class CineCamUiState(
    val fps: VideoFps = VideoFps.FPS_24,
    val shutterAngle: Float = 180.0f,
    val iso: Int = 400,
    val isManualExposure: Boolean = true,
    val focusDiopter: Float = 0.0f, // 0.0 = infinity
    val isManualFocus: Boolean = true,
    val kelvin: Int = 5600,
    val tint: Int = 0,
    val isManualWb: Boolean = true,
    val assistMode: AssistTool = AssistTool.NONE,
    val peakingColor: PeakingColor = PeakingColor.GREEN,
    val peakingThreshold: Float = 0.15f,
    val zebraThreshold: ZebraThreshold = ZebraThreshold.NINETY,
    val frameGuide: FrameGuide = FrameGuide.RATIO_2_39,
    val codec: VideoCodec = VideoCodec.HEVC,
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val bitrateMbps: Int = 50,
    val isRecording: Boolean = false,
    val recordingTimecode: String = "00:00:00:00",
    val activeDrawer: DrawerType = DrawerType.NONE,
    val audioMeter: AudioMeterState = AudioMeterState(),
    val micGainDb: Float = 0f,
    val micSource: String = "Internal Stereo (Built-in)",
    val isCameraHardwareConnected: Boolean = false,
    val statusMessage: String? = null,
    val recordedClips: List<RecordedClip> = emptyList(),
    val histogramBins: FloatArray = FloatArray(32) { 0.1f },
    val currentScreen: AppScreen = AppScreen.CAMERA,
    val selectedPlaybackClip: CinemaClip? = null
) {
    // Shutter speed in fractional seconds: 1 / (360 * fps / angle)
    val calculatedShutterSpeedStr: String
        get() {
            val denom = (360.0f * fps.fps / shutterAngle).toInt()
            return "1/${denom}s"
        }
}

class CineCamViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CineCamUiState())
    val uiState: StateFlow<CineCamUiState> = _uiState.asStateFlow()

    val cameraThread = CameraThreadHandler().apply { startThread() }
    private val systemCameraManager = application.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    val characteristicsHelper = com.example.camera.CameraCharacteristicsHelper(systemCameraManager)

    val cameraDeviceManager = com.example.camera.CameraDeviceManager(
        systemCameraManager,
        cameraThread.backgroundHandler!!
    )

    // Alias for compatibility
    val cameraManager: com.example.camera.CameraDeviceManager
        get() = cameraDeviceManager

    val cameraPreviewRenderer = CameraPreviewRenderer(
        context = application,
        onSurfaceReady = { surfaceTexture ->
            onSurfaceTextureAvailable(surfaceTexture)
        }
    )

    val glRenderer = CameraGlRenderer { surfaceTexture, width, height ->
        onGlSurfaceTextureReady(surfaceTexture)
    }
    val audioMonitor = AudioMonitorEngine(application)
    val videoRecorder = CinemaVideoRecorder(application)
    val mediaRepository = MediaRepository(application)

    private var timecodeJob: Job? = null

    init {
        // Sync initial parameters to renderer & camera
        updateGlRendererExposure()
        glRenderer.kelvin = _uiState.value.kelvin
        glRenderer.tint = _uiState.value.tint

        glRenderer.onHistogramUpdate = { bins ->
            _uiState.update { it.copy(histogramBins = bins) }
        }

        audioMonitor.onLevelsUpdated = { levels ->
            _uiState.update { it.copy(audioMeter = levels) }
        }

        cameraDeviceManager.onCameraConnected = { connected ->
            glRenderer.isCameraActive = connected
            _uiState.update { it.copy(isCameraHardwareConnected = connected) }
        }

        audioMonitor.start()
    }

    fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int = 1920, height: Int = 1080) {
        val cameraId = characteristicsHelper.getManualRearCameraId()
        if (cameraId != null) {
            cameraDeviceManager.openCamera(cameraId, android.view.Surface(surfaceTexture))
        }
    }

    private fun onGlSurfaceTextureReady(surfaceTexture: SurfaceTexture) {
        onSurfaceTextureAvailable(surfaceTexture)
    }

    fun setFps(fps: VideoFps) {
        _uiState.update { it.copy(fps = fps) }
        cameraManager.setManualShutterAngle(_uiState.value.shutterAngle, fps.fps)
        updateGlRendererExposure()
    }

    fun setShutterAngle(angle: Float) {
        _uiState.update { it.copy(shutterAngle = angle) }
        cameraManager.setManualShutterAngle(angle, _uiState.value.fps.fps)
        updateGlRendererExposure()
    }

    fun setIso(iso: Int) {
        _uiState.update { it.copy(iso = iso) }
        cameraManager.setManualIso(iso)
        updateGlRendererExposure()
    }

    fun toggleManualExposure() {
        val next = !_uiState.value.isManualExposure
        _uiState.update { it.copy(isManualExposure = next) }
        cameraManager.isManualExposure = next
        cameraManager.updateParameters()
    }

    fun setFocusDiopter(diopter: Float) {
        _uiState.update { it.copy(focusDiopter = diopter) }
        cameraManager.setManualFocus(diopter)
    }

    fun toggleManualFocus() {
        val next = !_uiState.value.isManualFocus
        _uiState.update { it.copy(isManualFocus = next) }
        cameraManager.isManualFocus = next
        cameraManager.updateParameters()
    }

    fun setKelvin(kelvin: Int) {
        _uiState.update { it.copy(kelvin = kelvin) }
        cameraManager.setManualWhiteBalance(kelvin, _uiState.value.tint)
        glRenderer.kelvin = kelvin
    }

    fun setTint(tint: Int) {
        _uiState.update { it.copy(tint = tint) }
        cameraManager.setManualWhiteBalance(_uiState.value.kelvin, tint)
        glRenderer.tint = tint
    }

    fun applyWbPreset(preset: WhiteBalancePreset) {
        setKelvin(preset.kelvin)
        setTint(preset.tint)
    }

    fun toggleManualWb() {
        val next = !_uiState.value.isManualWb
        _uiState.update { it.copy(isManualWb = next) }
        cameraManager.isManualWb = next
        cameraManager.updateParameters()
    }

    fun setAssistMode(mode: AssistTool) {
        _uiState.update { it.copy(assistMode = mode) }
        val overlayMode = when (mode) {
            AssistTool.NONE -> CameraPreviewRenderer.OverlayMode.NONE
            AssistTool.FOCUS_PEAKING -> CameraPreviewRenderer.OverlayMode.PEAKING
            AssistTool.FALSE_COLOR -> CameraPreviewRenderer.OverlayMode.FALSE_COLOR
            AssistTool.ZEBRA_STRIPES -> CameraPreviewRenderer.OverlayMode.ZEBRA
        }
        cameraPreviewRenderer.currentOverlayMode = overlayMode
        glRenderer.assistMode = mode
    }

    fun setPeakingColor(color: PeakingColor) {
        _uiState.update { it.copy(peakingColor = color) }
        cameraPreviewRenderer.peakingColor = floatArrayOf(color.r, color.g, color.b)
        glRenderer.peakingColor = color
    }

    fun setPeakingThreshold(threshold: Float) {
        _uiState.update { it.copy(peakingThreshold = threshold) }
        cameraPreviewRenderer.peakingThreshold = threshold
        glRenderer.peakingThreshold = threshold
    }

    fun setZebraThreshold(threshold: ZebraThreshold) {
        _uiState.update { it.copy(zebraThreshold = threshold) }
        cameraPreviewRenderer.zebraThreshold = threshold.threshold
        glRenderer.zebraThreshold = threshold.threshold
    }

    fun setFrameGuide(guide: FrameGuide) {
        _uiState.update { it.copy(frameGuide = guide) }
    }

    fun setCodec(codec: VideoCodec) {
        _uiState.update { it.copy(codec = codec) }
    }

    fun setResolution(res: VideoResolution) {
        _uiState.update { it.copy(resolution = res) }
    }

    fun setBitrate(bitrate: Int) {
        _uiState.update { it.copy(bitrateMbps = bitrate) }
    }

    fun setMicGain(gainDb: Float) {
        _uiState.update { it.copy(micGainDb = gainDb) }
        val multiplier = 10.0.pow((gainDb / 20.0)).toFloat()
        audioMonitor.gainMultiplier = multiplier
    }

    fun setMicSource(source: String) {
        _uiState.update { it.copy(micSource = source) }
    }

    fun navigateToGallery() {
        _uiState.update { it.copy(currentScreen = AppScreen.GALLERY, activeDrawer = DrawerType.NONE) }
        viewModelScope.launch {
            mediaRepository.refreshClips()
        }
    }

    fun navigateToPlayback(clip: CinemaClip) {
        _uiState.update { it.copy(currentScreen = AppScreen.PLAYBACK, selectedPlaybackClip = clip) }
    }

    fun navigateToCamera() {
        _uiState.update { it.copy(currentScreen = AppScreen.CAMERA, selectedPlaybackClip = null) }
    }

    fun toggleDrawer(type: DrawerType) {
        if (type == DrawerType.CLIPS) {
            navigateToGallery()
            return
        }
        _uiState.update {
            it.copy(activeDrawer = if (it.activeDrawer == type) DrawerType.NONE else type)
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            // 1. Revert camera capture session back to single preview surface
            cameraDeviceManager.stopRecordingSession()

            // 2. Stop encoding pipeline and finalize MP4
            val clip = videoRecorder.stopRecording(
                fps = _uiState.value.fps,
                resolution = _uiState.value.resolution
            )
            timecodeJob?.cancel()
            timecodeJob = null

            viewModelScope.launch {
                mediaRepository.refreshClips()
            }

            _uiState.update { state ->
                val newClips = if (clip != null) listOf(clip) + state.recordedClips else state.recordedClips
                state.copy(
                    isRecording = false,
                    recordingTimecode = "00:00:00:00",
                    recordedClips = newClips,
                    statusMessage = clip?.let { "Saved: ${it.fileName} (${it.durationSeconds}s)" }
                )
            }
        } else {
            // 1. Start encoder pipeline and retrieve MediaCodec input surface
            val recordSurface = videoRecorder.startRecording(
                codec = _uiState.value.codec,
                fps = _uiState.value.fps,
                resolution = _uiState.value.resolution,
                bitrateMbps = _uiState.value.bitrateMbps,
                micGainDb = _uiState.value.micGainDb,
                micSource = _uiState.value.micSource
            )

            // 2. If encoder surface ready, switch camera session to dual-surface (Preview + Clean Encoder)
            if (recordSurface != null) {
                cameraDeviceManager.startRecordingSession(recordSurface)
            }

            _uiState.update { it.copy(isRecording = true, statusMessage = "Recording active (Clean H.265)") }

            timecodeJob = viewModelScope.launch {
                val start = SystemClock.uptimeMillis()
                while (isActive) {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val tc = videoRecorder.formatTimecode(elapsed, _uiState.value.fps.fps)
                    _uiState.update { it.copy(recordingTimecode = tc) }
                    delay(40)
                }
            }
        }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun updateGlRendererExposure() {
        val s = _uiState.value
        // Baseline exposure: 400 ISO, 180° Shutter Angle is 1.0 multiplier
        val isoFactor = s.iso / 400.0f
        val shutterFactor = s.shutterAngle / 180.0f
        val totalExposure = (isoFactor * shutterFactor).coerceIn(0.1f, 5.0f)
        glRenderer.exposureMultiplier = totalExposure
    }

    override fun onCleared() {
        super.onCleared()
        timecodeJob?.cancel()
        audioMonitor.stop()
        cameraPreviewRenderer.release()
        cameraDeviceManager.release()
        cameraThread.stopThread()
    }
}
