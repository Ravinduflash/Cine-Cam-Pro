package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoFps
import com.example.ui.CineCamUiState
import com.example.ui.DrawerType

@Composable
fun TopInfoBar(
    state: CineCamUiState,
    onFpsSelected: (VideoFps) -> Unit,
    onDrawerClick: (DrawerType) -> Unit,
    modifier: Modifier = Modifier
) {
    var fpsMenuExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "tally_pulse")
    val tallyAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tally_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE60D0E11))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Group: FPS & Shutter
            Row(verticalAlignment = Alignment.CenterVertically) {
                // FPS Badge
                Box {
                    ParameterBadge(
                        label = "FPS",
                        value = "${state.fps.fps}",
                        isActive = state.activeDrawer == DrawerType.NONE,
                        onClick = { fpsMenuExpanded = true },
                        modifier = Modifier.testTag("fps_badge")
                    )

                    DropdownMenu(
                        expanded = fpsMenuExpanded,
                        onDismissRequest = { fpsMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E1F24))
                    ) {
                        VideoFps.values().forEach { fpsOpt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${fpsOpt.fps} FPS",
                                        color = if (state.fps == fpsOpt) Color(0xFFFFB800) else Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    onFpsSelected(fpsOpt)
                                    fpsMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Shutter Angle
                ParameterBadge(
                    label = "SHUTTER",
                    value = "${state.shutterAngle.toInt()}°",
                    subValue = state.calculatedShutterSpeedStr,
                    isActive = state.activeDrawer == DrawerType.EXPOSURE,
                    onClick = { onDrawerClick(DrawerType.EXPOSURE) },
                    modifier = Modifier.testTag("shutter_badge")
                )

                Spacer(modifier = Modifier.width(6.dp))

                // ISO Badge
                ParameterBadge(
                    label = "ISO",
                    value = "${state.iso}",
                    isActive = state.activeDrawer == DrawerType.EXPOSURE,
                    onClick = { onDrawerClick(DrawerType.EXPOSURE) },
                    modifier = Modifier.testTag("iso_badge")
                )

                Spacer(modifier = Modifier.width(6.dp))

                // White Balance Badge
                ParameterBadge(
                    label = "WB",
                    value = "${state.kelvin}K",
                    subValue = if (state.tint != 0) "T ${if (state.tint > 0) "+${state.tint}" else state.tint}" else null,
                    isActive = state.activeDrawer == DrawerType.WHITE_BALANCE,
                    onClick = { onDrawerClick(DrawerType.WHITE_BALANCE) },
                    modifier = Modifier.testTag("wb_badge")
                )
            }

            // Center: Timecode & Recording Tally
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF18191E))
                    .border(
                        1.dp,
                        if (state.isRecording) Color(0xFFFF1744) else Color(0xFF2A2B33),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                if (state.isRecording) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF1744))
                            .alpha(tallyAlpha)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = state.recordingTimecode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (state.isRecording) Color(0xFFFF1744) else Color(0xFFE2E4EC),
                    letterSpacing = 1.sp
                )
            }

            // Right Group: Codec & System Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Codec & Resolution
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = state.codec.label.split(" ")[0],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB800)
                    )
                    Text(
                        text = "${state.resolution.label} • ${state.bitrateMbps}M",
                        fontSize = 9.sp,
                        color = Color(0xFF9E9EA8)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Storage remaining
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "REC DISK",
                        fontSize = 9.sp,
                        color = Color(0xFF8E8E98)
                    )
                    Text(
                        text = "128 GB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ParameterBadge(
    label: String,
    value: String,
    subValue: String? = null,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color(0xFF2E2412) else Color(0xFF18191E))
            .border(
                1.dp,
                if (isActive) Color(0xFFFFB800) else Color(0xFF282932),
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFFFFB800) else Color(0xFF8A8B95)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isActive) Color(0xFFFFD54F) else Color.White
                )
                if (subValue != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = subValue,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB0B0BE)
                    )
                }
            }
        }
    }
}
