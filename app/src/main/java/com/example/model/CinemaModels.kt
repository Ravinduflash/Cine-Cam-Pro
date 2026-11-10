package com.example.model

enum class AssistTool {
    NONE,
    FOCUS_PEAKING,
    FALSE_COLOR,
    ZEBRA_STRIPES
}

enum class PeakingColor(val label: String, val r: Float, val g: Float, val b: Float) {
    GREEN("Green", 0.0f, 1.0f, 0.2f),
    RED("Red", 1.0f, 0.1f, 0.2f),
    CYAN("Cyan", 0.0f, 0.9f, 1.0f),
    YELLOW("Yellow", 1.0f, 0.9f, 0.0f)
}

enum class ZebraThreshold(val label: String, val threshold: Float) {
    SEVENTY_FIVE("75%", 0.75f),
    EIGHTY_FIVE("85%", 0.85f),
    NINETY("90%", 0.90f),
    NINETY_FIVE("95%", 0.95f),
    ONE_HUNDRED("100%", 1.00f)
}

enum class FrameGuide(val label: String, val aspectRatio: Float?) {
    OFF("Off", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_17_9("17:9 DCI", 17f / 9f),
    RATIO_2_39("2.39:1 Scope", 2.39f),
    RATIO_4_3("4:3 Academy", 4f / 3f),
    RATIO_1_1("1:1 Square", 1f)
}

enum class VideoCodec(val label: String, val mimeType: String) {
    HEVC("H.265 / HEVC", "video/hevc"),
    AVC("H.264 / AVC", "video/avc")
}

enum class VideoFps(val fps: Int) {
    FPS_24(24),
    FPS_25(25),
    FPS_30(30),
    FPS_60(60)
}

enum class VideoResolution(val label: String, val width: Int, val height: Int) {
    UHD_4K("4K UHD", 3840, 2160),
    DCI_4K("4K DCI", 4096, 2160),
    FHD_1080P("1080p FHD", 1920, 1080),
    HD_720P("720p HD", 1280, 720)
}

data class WhiteBalancePreset(
    val name: String,
    val kelvin: Int,
    val tint: Int
) {
    companion object {
        val TUNGSTEN = WhiteBalancePreset("Tungsten", 3200, 0)
        val FLUORESCENT = WhiteBalancePreset("Fluorescent", 4000, 10)
        val DAYLIGHT = WhiteBalancePreset("Daylight", 5600, 0)
        val CLOUDY = WhiteBalancePreset("Cloudy", 6500, 5)
        val SHADE = WhiteBalancePreset("Shade", 7500, 10)

        val PRESETS = listOf(TUNGSTEN, FLUORESCENT, DAYLIGHT, CLOUDY, SHADE)
    }
}

data class AudioMeterState(
    val leftRmsDb: Float = -60f,
    val leftPeakDb: Float = -60f,
    val rightRmsDb: Float = -60f,
    val rightPeakDb: Float = -60f,
    val isClipping: Boolean = false
)

data class RecordedClip(
    val filePath: String,
    val fileName: String,
    val durationSeconds: Long,
    val resolution: String,
    val fps: Int,
    val timestamp: Long
)
