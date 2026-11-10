package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.model.FrameGuide
import com.example.ui.components.AudioDrawer
import com.example.ui.components.BottomTransportBar
import com.example.ui.components.CinemaViewfinderOverlay
import com.example.ui.components.ClipsDrawer
import com.example.ui.components.ExposureDrawer
import com.example.ui.components.FocusDrawer
import com.example.ui.components.TopInfoBar
import com.example.ui.components.WhiteBalanceDrawer

import com.example.ui.gallery.GalleryScreen
import com.example.ui.playback.PlaybackScreen

@Composable
fun CineCamScreen(
    viewModel: CineCamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (hasCameraPermission && viewModel.glRenderer.surfaceTexture != null) {
            viewModel.cameraManager.startCamera(viewModel.glRenderer.surfaceTexture!!)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    when (state.currentScreen) {
        AppScreen.GALLERY -> {
            GalleryScreen(
                repository = viewModel.mediaRepository,
                onBackToCamera = { viewModel.navigateToCamera() },
                onClipSelected = { clip -> viewModel.navigateToPlayback(clip) },
                modifier = modifier
            )
        }
        AppScreen.PLAYBACK -> {
            state.selectedPlaybackClip?.let { clip ->
                PlaybackScreen(
                    clip = clip,
                    onBack = { viewModel.navigateToGallery() },
                    modifier = modifier
                )
            } ?: run {
                viewModel.navigateToGallery()
            }
        }
        AppScreen.CAMERA -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Fullscreen Cinema Viewfinder using CinemaTextureView
        AndroidView(
            factory = { ctx ->
                com.example.ui.components.CinemaTextureView(
                    context = ctx,
                    onSurfaceTextureReady = { surfaceTexture, width, height ->
                        viewModel.onSurfaceTextureAvailable(surfaceTexture, width, height)
                    },
                    onSurfaceDestroyedCallback = {
                        viewModel.cameraDeviceManager.stopCamera()
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Viewfinder Overlay (Grid, Frame Guides, Scopes, VU meters, Assist toggles)
        CinemaViewfinderOverlay(
            state = state,
            onAssistToggle = { viewModel.setAssistMode(it) },
            onFrameGuideToggle = {
                val nextGuide = when (state.frameGuide) {
                    FrameGuide.OFF -> FrameGuide.RATIO_2_39
                    FrameGuide.RATIO_2_39 -> FrameGuide.RATIO_16_9
                    FrameGuide.RATIO_16_9 -> FrameGuide.RATIO_17_9
                    FrameGuide.RATIO_17_9 -> FrameGuide.RATIO_4_3
                    FrameGuide.RATIO_4_3 -> FrameGuide.OFF
                    else -> FrameGuide.OFF
                }
                viewModel.setFrameGuide(nextGuide)
            },
            onOpenAudioDrawer = { viewModel.toggleDrawer(DrawerType.AUDIO) }
        )

        // 3. Top Cinema Parameters Info Bar
        TopInfoBar(
            state = state,
            onFpsSelected = { viewModel.setFps(it) },
            onDrawerClick = { viewModel.toggleDrawer(it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 4. Bottom Transport Bar (Lens, Cinema Record Button, Drawer toggles)
        BottomTransportBar(
            state = state,
            onRecordToggle = { viewModel.toggleRecording() },
            onDrawerClick = { viewModel.toggleDrawer(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 5. Slide-in Manual Drawers
        AnimatedVisibility(
            visible = state.activeDrawer != DrawerType.NONE,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            when (state.activeDrawer) {
                DrawerType.EXPOSURE -> ExposureDrawer(viewModel, state)
                DrawerType.FOCUS -> FocusDrawer(viewModel, state)
                DrawerType.WHITE_BALANCE -> WhiteBalanceDrawer(viewModel, state)
                DrawerType.AUDIO -> AudioDrawer(viewModel, state)
                DrawerType.CLIPS -> ClipsDrawer(viewModel, state)
                else -> {}
            }
        }

        // 6. Permission Reminder Banner (if denied)
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE62A1414))
                    .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Camera permission needed for live sensor feed",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Grant", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 7. Temporary Status Toast/Snackbar
        state.statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE61B1D24))
                    .border(1.dp, Color(0xFFFFB800), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg,
                        color = Color(0xFFFFD54F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DISMISS",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF2C2E3B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            }
        }
    }
}
