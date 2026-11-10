package com.example.storage

import android.net.Uri
import java.util.Locale

/**
 * Domain model representing a cinema clip stored in MediaStore / Scoped Storage.
 */
data class CinemaClip(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val filePath: String = ""
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hours = min / 60
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, min % 60, sec)
            } else {
                String.format(Locale.US, "%02d:%02d", min, sec)
            }
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1000) {
                val gb = mb / 1024
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }

    val resolutionLabel: String
        get() = when {
            width >= 3840 || height >= 2160 -> "4K 2160p"
            width >= 2560 || height >= 1440 -> "2K 1440p"
            width >= 1920 || height >= 1080 -> "FHD 1080p"
            width >= 1280 || height >= 720 -> "HD 720p"
            else -> "${width}x${height}"
        }

    val codecLabel: String
        get() = "H.265 HEVC"
}
