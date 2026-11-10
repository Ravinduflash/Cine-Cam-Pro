package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeakingColor
import com.example.model.WhiteBalancePreset
import com.example.model.ZebraThreshold
import com.example.ui.CineCamUiState
import com.example.ui.CineCamViewModel
import com.example.ui.DrawerType

@Composable
fun DrawerContainer(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color(0xF513141A))
            .border(1.dp, Color(0xFF2C2E3B))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = Color(0xFFFFB800),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = Color(0xFF9E9EA8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun ExposureDrawer(viewModel: CineCamViewModel, state: CineCamUiState) {
    DrawerContainer(
        title = "EXPOSURE CONTROL",
        onClose = { viewModel.toggleDrawer(DrawerType.NONE) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Shutter Angle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SHUTTER ANGLE", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${state.shutterAngle.toInt()}° (${state.calculatedShutterSpeedStr})",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val standardAngles = listOf(11.25f, 45f, 90f, 144f, 172.8f, 180f, 270f, 360f)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(standardAngles) { angle ->
                    SelectionChip(
                        label = "${angle.toInt()}°",
                        isSelected = Math.abs(state.shutterAngle - angle) < 1f,
                        onClick = { viewModel.setShutterAngle(angle) }
                    )
                }
            }

            Slider(
                value = state.shutterAngle,
                onValueChange = { viewModel.setShutterAngle(it) },
                valueRange = 11.25f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB800),
                    activeTrackColor = Color(0xFFFFB800)
                ),
                modifier = Modifier.testTag("shutter_slider")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ISO Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ISO SENSITIVITY", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "ISO ${state.iso}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val standardIsos = listOf(50, 100, 200, 400, 800, 1250, 1600, 3200, 6400)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(standardIsos) { isoVal ->
                    SelectionChip(
                        label = "$isoVal",
                        isSelected = state.iso == isoVal,
                        onClick = { viewModel.setIso(isoVal) }
                    )
                }
            }

            Slider(
                value = state.iso.toFloat(),
                onValueChange = { viewModel.setIso(it.toInt()) },
                valueRange = 50f..6400f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB800),
                    activeTrackColor = Color(0xFFFFB800)
                ),
                modifier = Modifier.testTag("iso_slider")
            )

            // Manual Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MANUAL EXPOSURE LOCK", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                SelectionChip(
                    label = if (state.isManualExposure) "MANUAL (LOCKED)" else "AUTO (AE)",
                    isSelected = state.isManualExposure,
                    onClick = { viewModel.toggleManualExposure() }
                )
            }
        }
    }
}

@Composable
fun FocusDrawer(viewModel: CineCamViewModel, state: CineCamUiState) {
    DrawerContainer(
        title = "FOCUS & PEAKING",
        onClose = { viewModel.toggleDrawer(DrawerType.NONE) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FOCUS MODE", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectionChip(
                        label = "AF-C",
                        isSelected = !state.isManualFocus,
                        onClick = { if (state.isManualFocus) viewModel.toggleManualFocus() }
                    )
                    SelectionChip(
                        label = "MANUAL",
                        isSelected = state.isManualFocus,
                        onClick = { if (!state.isManualFocus) viewModel.toggleManualFocus() }
                    )
                }
            }

            // Lens Diopter Wheel Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DISTANCE (DIOPTERS)", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                    Text(
                        if (state.focusDiopter < 0.1f) "∞ INFINITY" else String.format("%.2f m⁻¹", state.focusDiopter),
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = state.focusDiopter,
                    onValueChange = { viewModel.setFocusDiopter(it) },
                    valueRange = 0.0f..10.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676)
                    ),
                    modifier = Modifier.testTag("focus_slider")
                )
            }

            // Peaking Color selection
            Column {
                Text("PEAKING OVERLAY COLOR", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeakingColor.values().forEach { pColor ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(pColor.r, pColor.g, pColor.b))
                                .border(
                                    2.dp,
                                    if (state.peakingColor == pColor) Color.White else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { viewModel.setPeakingColor(pColor) }
                        )
                    }
                }
            }

            // Peaking Sensitivity
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("PEAKING SENSITIVITY", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                    Text("${(state.peakingThreshold * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                }
                Slider(
                    value = state.peakingThreshold,
                    onValueChange = { viewModel.setPeakingThreshold(it) },
                    valueRange = 0.05f..0.35f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676)
                    )
                )
            }
        }
    }
}

@Composable
fun WhiteBalanceDrawer(viewModel: CineCamViewModel, state: CineCamUiState) {
    DrawerContainer(
        title = "WHITE BALANCE & TINT",
        onClose = { viewModel.toggleDrawer(DrawerType.NONE) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Presets
            Text("CINEMA PRESETS", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(WhiteBalancePreset.PRESETS) { preset ->
                    SelectionChip(
                        label = "${preset.name} (${preset.kelvin}K)",
                        isSelected = state.kelvin == preset.kelvin,
                        onClick = { viewModel.applyWbPreset(preset) }
                    )
                }
            }

            // Kelvin slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("COLOR TEMPERATURE", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                    Text("${state.kelvin}K", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = state.kelvin.toFloat(),
                    onValueChange = { viewModel.setKelvin(it.toInt()) },
                    valueRange = 2500f..10000f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFB800),
                        activeTrackColor = Color(0xFFFFB800)
                    )
                )
            }

            // Tint slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TINT (GREEN / MAGENTA)", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                    Text(
                        if (state.tint > 0) "+${state.tint}" else "${state.tint}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.tint.toFloat(),
                    onValueChange = { viewModel.setTint(it.toInt()) },
                    valueRange = -50f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF4081),
                        activeTrackColor = Color(0xFFFF4081)
                    )
                )
            }
        }
    }
}

@Composable
fun AudioDrawer(viewModel: CineCamViewModel, state: CineCamUiState) {
    DrawerContainer(
        title = "AUDIO & MICROPHONE ROUTING",
        onClose = { viewModel.toggleDrawer(DrawerType.NONE) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("AUDIO SOURCE", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val sources = listOf("Internal Stereo (Built-in)", "USB-C Audio In", "Studio Wireless Receiver")
            sources.forEach { src ->
                SelectionChip(
                    label = src,
                    isSelected = state.micSource == src,
                    onClick = { viewModel.setMicSource(src) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Digital Gain Staging
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DIGITAL GAIN", color = Color(0xFFA0A0B0), fontSize = 11.sp)
                    Text(
                        "${if (state.micGainDb > 0) "+${state.micGainDb.toInt()}" else state.micGainDb.toInt()} dB",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.micGainDb,
                    onValueChange = { viewModel.setMicGain(it) },
                    valueRange = -12f..12f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF)
                    )
                )
            }

            // Real-time Level readouts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E2028))
                    .padding(10.dp)
            ) {
                Text("REAL-TIME LEVELS", color = Color(0xFFA0A0B0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("L: ${state.audioMeter.leftPeakDb.toInt()} dBFS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("R: ${state.audioMeter.rightPeakDb.toInt()} dBFS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (state.audioMeter.isClipping) {
                    Text("CLIPPING DETECTED", color = Color(0xFFFF1744), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ClipsDrawer(viewModel: CineCamViewModel, state: CineCamUiState) {
    DrawerContainer(
        title = "RECORDED CLIPS",
        onClose = { viewModel.toggleDrawer(DrawerType.NONE) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.navigateToGallery() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800)),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "OPEN FULL MEDIA VAULT",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            if (state.recordedClips.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recorded clips yet. Tap the red button to record.", color = Color(0xFF7E808C), fontSize = 12.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recordedClips) { clip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2028))
                                .border(1.dp, Color(0xFF2C2E38), RoundedCornerShape(6.dp))
                                .clickable {
                                    val cinemaClip = com.example.storage.CinemaClip(
                                        id = clip.hashCode().toLong(),
                                        uri = android.net.Uri.fromFile(java.io.File(clip.filePath)),
                                        title = clip.fileName,
                                        durationMs = clip.durationSeconds * 1000L,
                                        width = 1920,
                                        height = 1080,
                                        sizeBytes = java.io.File(clip.filePath).length(),
                                        dateAddedSec = clip.timestamp / 1000L,
                                        filePath = clip.filePath
                                    )
                                    viewModel.navigateToPlayback(cinemaClip)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(clip.fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${clip.resolution} • ${clip.fps} fps • ${clip.durationSeconds}s", color = Color(0xFFA0A0B0), fontSize = 10.sp)
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF332912) else Color(0xFF1E2028))
            .border(
                1.dp,
                if (isSelected) Color(0xFFFFB800) else Color(0xFF2C2E3A),
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color(0xFFFFD54F) else Color(0xFFD0D0DE)
        )
    }
}
