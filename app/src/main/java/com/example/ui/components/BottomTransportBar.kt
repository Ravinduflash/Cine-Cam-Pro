package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CineCamUiState
import com.example.ui.DrawerType

@Composable
fun BottomTransportBar(
    state: CineCamUiState,
    onRecordToggle: () -> Unit,
    onDrawerClick: (DrawerType) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_tally_scale")
    val recPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE60D0E11))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Lens focal length selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LensButton(label = "0.5x", isSelected = false, onClick = {})
                LensButton(label = "1.0x", isSelected = true, onClick = {})
                LensButton(label = "3.0x", isSelected = false, onClick = {})
            }

            // Center: Big Cinema Record Button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2026))
                    .border(2.dp, if (state.isRecording) Color(0xFFFF1744) else Color(0xFF4A4D59), CircleShape)
                    .clickable(onClick = onRecordToggle)
                    .testTag("record_button"),
                contentAlignment = Alignment.Center
            ) {
                if (state.isRecording) {
                    // Recording: Red square stop icon inside
                    Box(
                        modifier = Modifier
                            .size((22 * recPulse).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFF1744))
                    )
                } else {
                    // Standby: Solid vibrant red cinema circle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    )
                }
            }

            // Right: Quick Navigation to Manual Drawers
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DrawerNavButton(
                    label = "FOCUS",
                    isActive = state.activeDrawer == DrawerType.FOCUS,
                    onClick = { onDrawerClick(DrawerType.FOCUS) }
                )
                DrawerNavButton(
                    label = "EXP",
                    isActive = state.activeDrawer == DrawerType.EXPOSURE,
                    onClick = { onDrawerClick(DrawerType.EXPOSURE) }
                )
                DrawerNavButton(
                    label = "WB",
                    isActive = state.activeDrawer == DrawerType.WHITE_BALANCE,
                    onClick = { onDrawerClick(DrawerType.WHITE_BALANCE) }
                )
                DrawerNavButton(
                    label = "AUDIO",
                    isActive = state.activeDrawer == DrawerType.AUDIO,
                    onClick = { onDrawerClick(DrawerType.AUDIO) }
                )
                DrawerNavButton(
                    label = "MEDIA",
                    isActive = state.activeDrawer == DrawerType.CLIPS || state.currentScreen == com.example.ui.AppScreen.GALLERY,
                    onClick = { onDrawerClick(DrawerType.CLIPS) }
                )
            }
        }
    }
}

@Composable
fun LensButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF332912) else Color(0xFF1A1C22))
            .border(1.dp, if (isSelected) Color(0xFFFFB800) else Color(0xFF2C2E38), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color(0xFFFFD54F) else Color(0xFFA0A0B0)
        )
    }
}

@Composable
fun DrawerNavButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color(0xFF2E2412) else Color(0xFF1A1C22))
            .border(1.dp, if (isActive) Color(0xFFFFB800) else Color(0xFF2C2E38), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFFFFD54F) else Color(0xFFA0A0B0)
        )
    }
}
