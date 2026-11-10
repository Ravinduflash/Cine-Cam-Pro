package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface

class CameraDeviceManager(
    private val cameraManager: CameraManager,
    private val backgroundHandler: Handler
) {
    private val tag = "CameraDeviceManager"

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurface: Surface? = null

    var cameraId: String? = null
        private set

    var isManualSensorSupported: Boolean = false
        private set

    // Hardware ranges
    var isoRange: Range<Int> = Range(50, 6400)
        private set
    var exposureTimeRangeNs: Range<Long> = Range(100_000L, 1_000_000_000L)
        private set
    var minFocusDistanceDiopters: Float = 10.0f
        private set

    // Manual Exposure & Sensor Parameters
    var currentIso: Int = 400
        private set
    var currentExposureTimeNs: Long = 20_833_333L // 1/48s default for 180° at 24fps
        private set
    var currentShutterAngle: Float = 180.0f
        private set
    var currentFps: Int = 24
        private set

    // Manual Focus Diopter Parameter (0.0 = infinity, higher values = closer macro)
    var currentDiopter: Float = 0.0f
        private set

    // Manual White Balance Parameters
    var currentKelvin: Int = 5600
        private set
    var currentTint: Int = 0
        private set

    var isManualExposure: Boolean = true
    var isManualFocus: Boolean = true
    var isManualWb: Boolean = true

    var onCameraConnected: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    constructor(context: Context) : this(
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager,
        HandlerThread("CineCamDefaultBackground").apply { start() }.let { Handler(it.looper) }
    )

    init {
        inspectCapabilities()
    }

    private fun inspectCapabilities() {
        try {
            val ids = cameraManager.cameraIdList
            if (ids.isEmpty()) return

            for (id in ids) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
            if (cameraId == null) cameraId = ids[0]

            val chars = cameraManager.getCameraCharacteristics(cameraId!!)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            isManualSensorSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)

            chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { isoRange = it }
            chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { exposureTimeRangeNs = it }
            chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let {
                minFocusDistanceDiopters = if (it > 0f) it else 10.0f
            }

            // Calculate initial exposure time
            currentExposureTimeNs = calculateShutterNs(currentShutterAngle, currentFps)
        } catch (e: Exception) {
            Log.e(tag, "Error inspecting camera capabilities", e)
        }
    }

    fun startCamera(surfaceTexture: android.graphics.SurfaceTexture) {
        val targetId = cameraId ?: return
        openCamera(targetId, Surface(surfaceTexture))
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String, surface: Surface) {
        this.cameraId = cameraId
        this.previewSurface = surface

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview(surface)
                    onCameraConnected?.invoke(true)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    onCameraConnected?.invoke(false)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onCameraConnected?.invoke(false)
                    onError?.invoke("Camera error $error")
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(tag, "Camera permission not granted", e)
            onCameraConnected?.invoke(false)
        } catch (e: Exception) {
            Log.e(tag, "Exception opening camera", e)
            onCameraConnected?.invoke(false)
        }
    }

    fun startPreview(surface: Surface) {
        startPreview(listOf(surface))
    }

    fun startPreview(surfaces: List<Surface>) {
        val device = cameraDevice ?: return
        if (surfaces.isEmpty()) return
        this.previewSurface = surfaces[0]

        try {
            captureSession?.close()
            captureSession = null

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            for (s in surfaces) {
                builder.addTarget(s)
            }
            previewRequestBuilder = builder

            // Direct sensor manipulation: disable auto-routines (AE & AF OFF)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)

            applyParameters(builder)

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            Log.i(tag, "Capture session configured with ${surfaces.size} targets")
                        } catch (e: CameraAccessException) {
                            Log.e(tag, "Failed to start repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(tag, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Error starting preview session with ${surfaces.size} surfaces", e)
        }
    }

    /**
     * Recreates the CameraCaptureSession with TWO targets:
     * the OpenGL Surface (for HUD/monitoring/overlays) and the MediaCodec Surface (for clean recording).
     */
    fun startRecordingSession(recordSurface: Surface) {
        val preview = previewSurface ?: return
        Log.i(tag, "Switching to dual-target recording session (Preview + MediaCodec)")
        startPreview(listOf(preview, recordSurface))
    }

    /**
     * Restores the single-target preview session when recording stops.
     */
    fun stopRecordingSession() {
        val preview = previewSurface ?: return
        Log.i(tag, "Restoring single-target preview session")
        startPreview(listOf(preview))
    }

    /**
     * Calculates exposure time in nanoseconds from a cinema Shutter Angle (degrees) and FPS.
     * Formula: t_sec = ShutterAngle / (360° * FPS), t_ns = t_sec * 1,000,000,000 ns
     */
    fun calculateShutterNs(shutterAngle: Float, fps: Int): Long {
        val safeFps = fps.coerceAtLeast(1)
        val exposureSec = shutterAngle / (360.0f * safeFps)
        val ns = (exposureSec * 1_000_000_000.0).toLong()
        return ns.coerceIn(exposureTimeRangeNs.lower, exposureTimeRangeNs.upper)
    }

    /**
     * Sets manual ISO sensitivity and immediately updates repeating capture request.
     */
    fun setManualIso(iso: Int) {
        this.currentIso = iso.coerceIn(isoRange.lower, isoRange.upper)
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply manual ISO: $iso", e)
        }
    }

    /**
     * Sets manual shutter exposure time in nanoseconds and updates repeating capture request.
     */
    fun setManualShutter(exposureTimeNs: Long) {
        this.currentExposureTimeNs = exposureTimeNs.coerceIn(exposureTimeRangeNs.lower, exposureTimeRangeNs.upper)
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTimeNs)
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1_000_000_000L / currentFps.coerceAtLeast(1))
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply manual shutter: $exposureTimeNs ns", e)
        }
    }

    /**
     * Sets manual shutter angle and updates repeating capture request.
     */
    fun setManualShutterAngle(angle: Float, fps: Int = currentFps) {
        this.currentShutterAngle = angle
        this.currentFps = fps
        val ns = calculateShutterNs(angle, fps)
        setManualShutter(ns)
    }

    /**
     * Sets manual lens focus distance in diopters (0.0 = infinity, higher values = macro).
     */
    fun setManualFocus(distanceDiopters: Float) {
        this.currentDiopter = distanceDiopters.coerceIn(0f, minFocusDistanceDiopters)
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentDiopter)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply manual focus distance: $distanceDiopters", e)
        }
    }

    /**
     * Sets manual Kelvin white balance and Tint.
     */
    fun setManualWhiteBalance(kelvin: Int, tint: Int) {
        this.currentKelvin = kelvin
        this.currentTint = tint
        val builder = previewRequestBuilder ?: return
        val session = captureSession ?: return

        try {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)

            val rGain: Float
            val bGain: Float
            if (kelvin <= 5600) {
                val t = (5600 - kelvin) / (5600f - 2500f)
                rGain = 1.0f + t * 0.5f
                bGain = 1.0f - t * 0.4f
            } else {
                val t = (kelvin - 5600) / (10000f - 5600f)
                rGain = 1.0f - t * 0.3f
                bGain = 1.0f + t * 0.6f
            }
            val gGain = 1.0f + (tint / 100f) * 0.2f
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(rGain, gGain, gGain, bGain))
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply manual white balance", e)
        }
    }

    private fun applyParameters(builder: CaptureRequest.Builder) {
        if (isManualExposure) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTimeNs)
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1_000_000_000L / currentFps.coerceAtLeast(1))
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (isManualFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentDiopter)
        } else {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        }

        if (isManualWb) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)

            val rGain: Float
            val bGain: Float
            if (currentKelvin <= 5600) {
                val t = (5600 - currentKelvin) / (5600f - 2500f)
                rGain = 1.0f + t * 0.5f
                bGain = 1.0f - t * 0.4f
            } else {
                val t = (currentKelvin - 5600) / (10000f - 5600f)
                rGain = 1.0f - t * 0.3f
                bGain = 1.0f + t * 0.6f
            }
            val gGain = 1.0f + (currentTint / 100f) * 0.2f
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(rGain, gGain, gGain, bGain))
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
    }

    fun updateParameters() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            applyParameters(builder)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update camera parameters", e)
        }
    }

    fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing camera", e)
        }
    }

    fun release() {
        stopCamera()
    }
}
