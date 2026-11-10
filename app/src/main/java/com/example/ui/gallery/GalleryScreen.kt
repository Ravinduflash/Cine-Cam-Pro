package com.example.ui.gallery

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storage.CinemaClip
import com.example.storage.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GalleryScreen
 *
 * Professional cinema media vault displaying recorded H.265 master files.
 * Provides high-speed thumbnail indexing, metadata inspection, and playback launching.
 */
@Composable
fun GalleryScreen(
    repository: MediaRepository,
    onBackToCamera: () -> Unit,
    onClipSelected: (CinemaClip) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var clips by remember { mutableStateOf<List<CinemaClip>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var clipToDelete by remember { mutableStateOf<CinemaClip?>(null) }

    // Intercept system back to return to live camera HUD
    BackHandler {
        onBackToCamera()
    }

    fun loadClips() {
        scope.launch {
            isLoading = true
            clips = repository.refreshClips()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadClips()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Cinema Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12141C))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E212D))
                        .clickable(onClick = onBackToCamera)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Camera",
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CAMERA",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "CINEMA MEDIA VAULT",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${clips.size} Recorded Master Clips (Scoped Storage)",
                        color = Color(0xFF8E90A0),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { loadClips() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E212D))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body: Loading, Empty State, or Clip Grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFB800))
                }
            } else if (clips.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF181B26)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Cinema Clips Recorded Yet",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the Record button on the camera HUD to record master H.265 footage.",
                            color = Color(0xFF7B7D8C),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBackToCamera,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800))
                        ) {
                            Text("Open Camera", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(clips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            onClick = { onClipSelected(clip) },
                            onDelete = { clipToDelete = clip }
                        )
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        clipToDelete?.let { clip ->
            AlertDialog(
                onDismissRequest = { clipToDelete = null },
                title = { Text("Delete Master Clip?", color = Color.White) },
                text = {
                    Text(
                        "Are you sure you want to permanently delete '${clip.title}'? This action cannot be undone.",
                        color = Color(0xFFC0C2D0)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                repository.deleteClip(clip)
                                loadClips()
                                clipToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clipToDelete = null }) {
                        Text("Cancel", color = Color(0xFFB0B2C0))
                    }
                },
                containerColor = Color(0xFF181B24),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun ClipCard(
    clip: CinemaClip,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(clip.id) {
        withContext(Dispatchers.IO) {
            try {
                if (clip.uri.scheme == "content") {
                    val size = Size(320, 180)
                    thumbnailBitmap = context.contentResolver.loadThumbnail(clip.uri, size, null)
                } else if (clip.filePath.isNotEmpty()) {
                    val file = File(clip.filePath)
                    if (file.exists()) {
                        thumbnailBitmap = ThumbnailUtils.createVideoThumbnail(
                            file,
                            Size(320, 180),
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore thumbnail fallback
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF161822))
            .border(1.dp, Color(0xFF262938), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail container (16:9 cinema ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0F1017))
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = clip.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF323545),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Play Overlay Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Duration Badge (Top Right)
                if (clip.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xD9000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = clip.formattedDuration,
                            color = Color(0xFFFFB800),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Codec & Resolution Badge (Bottom Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${clip.resolutionLabel} • HEVC",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Info details row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = clip.formattedSize,
                        color = Color(0xFF858898),
                        fontSize = 9.sp
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF6B6E7E),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
