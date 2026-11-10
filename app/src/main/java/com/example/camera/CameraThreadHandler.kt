package com.example.camera

import android.os.Handler
import android.os.HandlerThread

class CameraThreadHandler {
    private var backgroundThread: HandlerThread? = null
    var backgroundHandler: Handler? = null
        private set

    fun startThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("Camera2Background").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
