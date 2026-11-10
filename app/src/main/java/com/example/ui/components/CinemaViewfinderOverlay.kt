package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistTool
import com.example.model.AudioMeterState
import com.example.model.FrameGuide
import com.example.ui.CineCamUiState
import com.example.ui.DrawerType

@Composable
fun CinemaViewfinderOverlay(
    state: CineCamUiState,
    onAssistToggle: (AssistTool) -> Unit,
    onFrameGuideToggle: () -> Unit,
    onOpenAudioDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = constraints.maxWidth.toFloat()
        val totalHeight = constraints.maxHeight.toFloat()

        // 1. Viewfinder Calibration Chart (displays when hardware sensor is initializing or in emulator)
        if (!state.isCameraHardwareConnected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Dark cinema background
                drawRect(Color(0xFF101217), Offset.Zero, size)

                // 10 IRE Zone Gradient Bars
                val numBars = 10
                val barW = w / numBars
                for (i in 0 until numBars) {
                    val lum = i.toFloat() / (numBars - 1)
                    val baseColor = when (state.assistMode) {
                        AssistTool.FALSE_COLOR -> {
                            // IRE false color mapping
                            when {
                                lum < 0.05f -> Color(0xFF6A0DAD) // < 2 IRE Purple
                                lum < 0.20f -> Color(0xFF0033CC) // Blue
                                lum in 0.38f..0.43f -> Color(0xFF808080) // 18% Grey
                                lum in 0.50f..0.58f -> Color(0xFFF06292) // 54 IRE Pink Skin
                                lum in 0.70f..0.76f -> Color(0xFF00E676) // Green
                                lum in 0.84f..0.94f -> Color(0xFFFFEB3B) // Yellow
                                else -> Color(0xFFFF1744) // Red clipping
                            }
                        }
                        else -> Color(lum, lum, lum)
                    }
                    drawRect(baseColor, Offset(i * barW, 45f), Size(barW, 60f))
                }

                // Swatches: Skin (Pink), Foliage (Green), 18% Grey, White, Red, Clipping Highlight
                val swatches = listOf(
                    Color(0xFFC58C78), // Light skin
                    Color(0xFF5A9650), // Green foliage
                    Color(0xFF808080), // 18% Middle Grey
                    Color(0xFFE0E0E0), // 90% White
                    Color(0xFFD32F2F), // Cinema Red
                    Color(0xFFFAFAFA)  // 100% Highlight
                )
                val swW = w / swatches.size
                for ((idx, col) in swatches.withIndex()) {
                    val finalCol = if (state.assistMode == AssistTool.FALSE_COLOR) {
                        when (idx) {
                            0 -> Color(0xFFF06292) // Pink for skin
                            1 -> Color(0xFF00E676) // Green
                            2 -> Color(0xFF808080) // Grey
                            3 -> Color(0xFFFFEB3B) // Yellow
                            4 -> Color(0xFFD32F2F) // Red
                            5 -> Color(0xFFFF1744) // Pure Red clipping
                            else -> col
                        }
                    } else col
                    drawRect(finalCol, Offset(idx * swW, 115f), Size(swW, 75f))
                }

                // Focus Peaking Sharpness Target (Concentric Circles & Spoke Lines)
                val cx = w * 0.5f
                val cy = h * 0.62f
                val strokeCol = if (state.assistMode == AssistTool.FOCUS_PEAKING) {
                    Color(state.peakingColor.r, state.peakingColor.g, state.peakingColor.b)
                } else Color(0x99FFFFFF)

                for (r in 15..170 step 15) {
                    drawCircle(strokeCol, r.toFloat(), Offset(cx, cy), style = Stroke(width = 2f))
                }
                for (deg in 0 until 360 step 30) {
                    val rad = Math.toRadians(deg.toDouble())
                    val x2 = cx + (Math.cos(rad) * 170).toFloat()
                    val y2 = cy + (Math.sin(rad) * 170).toFloat()
                    drawLine(strokeCol, Offset(cx, cy), Offset(x2, y2), strokeWidth = 1.5f)
                }

                // Zebra Highlight Target (Right Box)
                val zebraBoxTop = h * 0.42f
                val zebraBoxLeft = w * 0.80f
                val zebraBoxW = w * 0.16f
                val zebraBoxH = h * 0.38f

                drawRect(Color(0xFFF8F8F8), Offset(zebraBoxLeft, zebraBoxTop), Size(zebraBoxW, zebraBoxH))

                if (state.assistMode == AssistTool.ZEBRA_STRIPES) {
                    // Draw diagonal zebra lines
                    var lineX = zebraBoxLeft
                    while (lineX < zebraBoxLeft + zebraBoxW + zebraBoxH) {
                        drawLine(
                            Color.Black,
                            Offset(lineX, zebraBoxTop),
                            Offset(lineX - 20f, zebraBoxTop + zebraBoxH),
                            strokeWidth = 4f
                        )
                        lineX += 16f
                    }
                }

                // Shadow Block (Left Box)
                drawRect(Color(0xFF030305), Offset(w * 0.04f, zebraBoxTop), Size(w * 0.14f, zebraBoxH))
            }
        }

        // 2. Framing Guide Overlay (Letterbox bars or outline)
        if (state.frameGuide.aspectRatio != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val targetRatio = state.frameGuide.aspectRatio!!
                val screenRatio = size.width / size.height

                if (screenRatio > targetRatio) {
                    // Pillarbox (black side bars)
                    val targetWidth = size.height * targetRatio
                    val barWidth = (size.width - targetWidth) / 2f
                    drawRect(Color(0x99000000), Offset.Zero, Size(barWidth, size.height))
                    drawRect(Color(0x99000000), Offset(size.width - barWidth, 0f), Size(barWidth, size.height))
                    drawLine(Color(0x80FFFFFF), Offset(barWidth, 0f), Offset(barWidth, size.height), 1f)
                    drawLine(Color(0x80FFFFFF), Offset(size.width - barWidth, 0f), Offset(size.width - barWidth, size.height), 1f)
                } else {
                    // Letterbox (top & bottom bars for 2.39:1 scope)
                    val targetHeight = size.width / targetRatio
                    val barHeight = (size.height - targetHeight) / 2f
                    drawRect(Color(0x99000000), Offset.Zero, Size(size.width, barHeight))
                    drawRect(Color(0x99000000), Offset(0f, size.height - barHeight), Size(size.width, barHeight))
                    drawLine(Color(0x80FFFFFF), Offset(0f, barHeight), Offset(size.width, barHeight), 1f)
                    drawLine(Color(0x80FFFFFF), Offset(0f, size.height - barHeight), Offset(size.width, size.height - barHeight), 1f)
                }

                // Center crosshair
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color(0x66FFFFFF), Offset(cx - 15f, cy), Offset(cx + 15f, cy), 1.5f)
                drawLine(Color(0x66FFFFFF), Offset(cx, cy - 15f), Offset(cx, cy + 15f), 1.5f)
            }
        }

        // 2. Left Edge: Cinematography Assist Quick-Toggles
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistButton(
                label = "PEAK",
                isActive = state.assistMode == AssistTool.FOCUS_PEAKING,
                accentColor = Color(0xFF00E676),
                onClick = {
                    onAssistToggle(
                        if (state.assistMode == AssistTool.FOCUS_PEAKING) AssistTool.NONE else AssistTool.FOCUS_PEAKING
                    )
                },
                tag = "assist_peaking"
            )

            AssistButton(
                label = "FALSE",
                isActive = state.assistMode == AssistTool.FALSE_COLOR,
                accentColor = Color(0xFFFF4081),
                onClick = {
                    onAssistToggle(
                        if (state.assistMode == AssistTool.FALSE_COLOR) AssistTool.NONE else AssistTool.FALSE_COLOR
                    )
                },
                tag = "assist_false_color"
            )

            AssistButton(
                label = "ZEBRA",
                isActive = state.assistMode == AssistTool.ZEBRA_STRIPES,
                accentColor = Color(0xFFFFEB3B),
                onClick = {
                    onAssistToggle(
                        if (state.assistMode == AssistTool.ZEBRA_STRIPES) AssistTool.NONE else AssistTool.ZEBRA_STRIPES
                    )
                },
                tag = "assist_zebra"
            )

            AssistButton(
                label = state.frameGuide.label.split(" ")[0],
                isActive = state.frameGuide != FrameGuide.OFF,
                accentColor = Color(0xFF00E5FF),
                onClick = onFrameGuideToggle,
                tag = "assist_guide"
            )
        }

        // 3. Top-Right: Real-time Histogram Scope
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
                .size(width = 120.dp, height = 54.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC0E0F14))
                .border(1.dp, Color(0xFF2E303D), RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            HistogramView(bins = state.histogramBins)
            Text(
                text = "HISTOGRAM",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0x99FFFFFF),
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        // 4. Bottom-Left: Stereo Audio Level Meter (Horizontal or Compact)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 74.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC0E0F14))
                .border(1.dp, Color(0xFF2E303D), RoundedCornerShape(4.dp))
                .clickable(onClick = onOpenAudioDrawer)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MIC",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8A8B95)
                )
                Spacer(modifier = Modifier.width(6.dp))
                CompactStereoMeter(audioState = state.audioMeter)
            }
        }
    }
}

@Composable
fun AssistButton(
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(44.dp, 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) Color(0xFF22242D) else Color(0xCC111217))
            .border(
                1.5.dp,
                if (isActive) accentColor else Color(0xFF282933),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) accentColor else Color(0xFFB0B0C0)
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun HistogramView(bins: FloatArray, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier) {
        if (bins.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val step = w / (bins.size - 1)

        val path = Path()
        path.moveTo(0f, h)

        for (i in bins.indices) {
            val x = i * step
            val y = h - (bins[i] * h).coerceIn(0f, h)
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.close()

        // Gradient filled histogram
        drawPath(path, Color(0x80FFB800))

        // Stroke line
        val strokePath = Path()
        for (i in bins.indices) {
            val x = i * step
            val y = h - (bins[i] * h).coerceIn(0f, h)
            if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
        }
        drawPath(strokePath, Color(0xFFFFD54F), style = Stroke(width = 1.5f))
    }
}

@Composable
fun CompactStereoMeter(audioState: AudioMeterState) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ChannelBar(levelDb = audioState.leftRmsDb, peakDb = audioState.leftPeakDb, label = "L")
        ChannelBar(levelDb = audioState.rightRmsDb, peakDb = audioState.rightPeakDb, label = "R")
    }
}

@Composable
fun ChannelBar(levelDb: Float, peakDb: Float, label: String) {
    // Normalizing dBFS (-60 dB to 0 dB) to fraction (0.0 to 1.0)
    val normRms = ((levelDb + 60f) / 60f).coerceIn(0f, 1f)
    val normPeak = ((peakDb + 60f) / 60f).coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF787985),
            modifier = Modifier.width(10.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF22232B))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val filledWidth = size.width * normRms
                val greenEnd = size.width * (48f / 60f) // -12 dB
                val yellowEnd = size.width * (58f / 60f) // -2 dB

                // Draw green safe level
                val greenWidth = filledWidth.coerceAtMost(greenEnd)
                if (greenWidth > 0f) {
                    drawRect(Color(0xFF00E676), Offset.Zero, Size(greenWidth, size.height))
                }

                // Draw yellow cautionary level
                if (filledWidth > greenEnd) {
                    val yellowWidth = (filledWidth - greenEnd).coerceAtMost(yellowEnd - greenEnd)
                    drawRect(Color(0xFFFFD600), Offset(greenEnd, 0f), Size(yellowWidth, size.height))
                }

                // Draw red clipping level
                if (filledWidth > yellowEnd) {
                    val redWidth = filledWidth - yellowEnd
                    drawRect(Color(0xFFFF1744), Offset(yellowEnd, 0f), Size(redWidth, size.height))
                }

                // Peak hold indicator line
                val peakX = size.width * normPeak
                drawLine(
                    color = if (normPeak > (58f / 60f)) Color(0xFFFF1744) else Color.White,
                    start = Offset(peakX, 0f),
                    end = Offset(peakX, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}
