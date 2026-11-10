package com.example.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView

class CinemaTextureView(
    context: Context,
    private val onSurfaceTextureReady: (SurfaceTexture, Int, Int) -> Unit,
    private val onSurfaceDestroyedCallback: () -> Unit = {}
) : TextureView(context), TextureView.SurfaceTextureListener {

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        onSurfaceTextureReady(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        onSurfaceDestroyedCallback()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
