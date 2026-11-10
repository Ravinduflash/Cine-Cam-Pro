package com.example.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

class EglCore(
    sharedContext: EGLContext? = null,
    flags: Int = 0
) {
    private val tag = "EglCore"

    var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private set
    var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private set
    var eglConfig: EGLConfig? = null
        private set

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("Unable to initialize EGL14")
        }

        // Try GLES 3.0 context first
        val config = getConfig(flags, 3)
        if (config != null) {
            val attrib3List = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(
                eglDisplay, config,
                sharedContext ?: EGL14.EGL_NO_CONTEXT,
                attrib3List, 0
            )

            if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                eglConfig = config
                eglContext = context
            }
        }

        // Fallback to GLES 2.0 if GLES 3.0 is unsupported on emulator
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            val config2 = getConfig(flags, 2)
                ?: throw RuntimeException("Unable to find suitable EGLConfig")
            val attrib2List = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context2 = EGL14.eglCreateContext(
                eglDisplay, config2,
                sharedContext ?: EGL14.EGL_NO_CONTEXT,
                attrib2List, 0
            )
            eglConfig = config2
            eglContext = context2
        }

        Log.i(tag, "EGL initialized successfully. Display: $eglDisplay, Context: $eglContext")
    }

    private fun getConfig(flags: Int, version: Int): EGLConfig? {
        val renderableType = if (version >= 3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            Log.w(tag, "Unable to find RGBA8888 / $version EGLConfig")
            return null
        }
        return configs[0]
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        if (surface !is Surface && surface !is android.graphics.SurfaceTexture) {
            throw RuntimeException("Invalid surface object: $surface")
        }
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surface, surfaceAttribs, 0
        )
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun releaseSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }
}
