package com.example.camera

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.SizeF

class CameraCharacteristicsHelper(private val cameraManager: CameraManager) {

    fun getManualRearCameraId(): String? {
        var fallbackBackCameraId: String? = null
        for (cameraId in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)

            if (facing != CameraCharacteristics.LENS_FACING_BACK) continue
            if (fallbackBackCameraId == null) fallbackBackCameraId = cameraId

            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val hasManualControl = capabilities?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            ) == true

            if (hasManualControl) return cameraId
        }
        // Fallback to back camera or any available camera on emulators lacking MANUAL_SENSOR flag
        return fallbackBackCameraId ?: cameraManager.cameraIdList.firstOrNull()
    }

    fun isManualSensorSupported(cameraId: String): Boolean {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        return capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true
    }

    fun getHardwareLevel(cameraId: String): Int {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
    }

    fun getHardwareLevelName(cameraId: String): String {
        return when (getHardwareLevel(cameraId)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "UNKNOWN"
        }
    }

    /**
     * Absolute hardware sensitivity (ISO) limit range: SENSOR_INFO_SENSITIVITY_RANGE
     */
    fun getSensitivityRange(cameraId: String): Range<Int> {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
    }

    fun getIsoRange(cameraId: String): Range<Int> = getSensitivityRange(cameraId)

    /**
     * Absolute hardware exposure time (shutter duration in nanoseconds) limit range: SENSOR_INFO_EXPOSURE_TIME_RANGE
     */
    fun getExposureTimeRange(cameraId: String): Range<Long> {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 1_000_000_000L)
    }

    fun getExposureTimeRangeNs(cameraId: String): Range<Long> = getExposureTimeRange(cameraId)

    /**
     * Minimum focus distance in diopters (1 / distance in meters): LENS_INFO_MINIMUM_FOCUS_DISTANCE
     * 0.0 indicates a fixed focus lens (or infinity); higher values indicate close macro capability.
     */
    fun getMinimumFocusDistance(cameraId: String): Float {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    }

    fun getHyperfocalDistance(cameraId: String): Float {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: 0f
    }

    fun getSensorActiveArraySize(cameraId: String): Rect? {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    }

    fun getSensorPhysicalSize(cameraId: String): SizeF? {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    }

    fun getSupportedFpsRanges(cameraId: String): Array<Range<Int>> {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        return chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
    }
}
