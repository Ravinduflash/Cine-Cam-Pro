package com.example.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import com.example.model.AssistTool
import com.example.model.PeakingColor
import com.example.model.ZebraThreshold
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGlRenderer(
    private val onSurfaceCreatedCallback: ((SurfaceTexture, Int, Int) -> Unit)? = null
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val tag = "CameraGlRenderer"

    private var surfaceView: GLSurfaceView? = null
    var surfaceTexture: SurfaceTexture? = null
        private set

    private var oesTextureId = 0
    private var testTextureId = 0
    private var programOes = 0
    private var program2D = 0

    // Vertex data
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    private val stMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16)

    @Volatile
    private var frameAvailable = false
    @Volatile
    var isCameraActive = false

    // Assist controls
    @Volatile var assistMode: AssistTool = AssistTool.NONE
    @Volatile var peakingColor: PeakingColor = PeakingColor.GREEN
    @Volatile var peakingThreshold: Float = 0.15f
    @Volatile var zebraThreshold: Float = 0.90f
    @Volatile var exposureMultiplier: Float = 1.0f
    @Volatile var kelvin: Int = 5600
    @Volatile var tint: Int = 0

    // Viewport dimensions
    var viewWidth: Int = 1920
        private set
    var viewHeight: Int = 1080
        private set

    // Real-time histogram bins (32 bins: 0.0 to 1.0)
    var onHistogramUpdate: ((FloatArray) -> Unit)? = null
    private var lastHistogramCalcTime = 0L
    private val histogramBins = FloatArray(32)

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)

        Matrix.setIdentityM(identityMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    fun bindSurfaceView(view: GLSurfaceView) {
        this.surfaceView = view
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.04f, 0.05f, 1.0f)

        try {
            programOes = GlShaderUtil.createProgram(CinemaShaders.VERTEX_SHADER, CinemaShaders.FRAGMENT_SHADER_OES)
            program2D = GlShaderUtil.createProgram(CinemaShaders.VERTEX_SHADER, CinemaShaders.FRAGMENT_SHADER_2D)
        } catch (e: Exception) {
            Log.e(tag, "Failed to compile shaders", e)
        }

        // Generate OES texture for Camera2
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTextureId = textures[0]
        testTextureId = textures[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Setup SurfaceTexture
        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(this)
        this.surfaceTexture = st

        // Generate fallback simulated cinema sensor test pattern
        generateSimulatedCinemaPattern(testTextureId)

        onSurfaceCreatedCallback?.invoke(st, viewWidth, viewHeight)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        var hasNewOesFrame = false
        val st = surfaceTexture
        if (st != null && frameAvailable) {
            synchronized(this) {
                if (frameAvailable) {
                    try {
                        st.updateTexImage()
                        st.getTransformMatrix(stMatrix)
                        hasNewOesFrame = true
                    } catch (e: Exception) {
                        Log.w(tag, "SurfaceTexture update failed", e)
                    }
                    frameAvailable = false
                }
            }
        }

        // Calculate Kelvin gains (Planckian approximation)
        // 5600K is neutral (1.0, 1.0, 1.0)
        val kelvinR: Float
        val kelvinB: Float
        if (kelvin <= 5600) {
            val t = (5600 - kelvin) / (5600f - 2500f)
            kelvinR = 1.0f + t * 0.45f // warm / red boost
            kelvinB = 1.0f - t * 0.40f
        } else {
            val t = (kelvin - 5600) / (10000f - 5600f)
            kelvinR = 1.0f - t * 0.35f
            kelvinB = 1.0f + t * 0.50f // cool / blue boost
        }
        val tintG = 1.0f + (tint / 100f) * 0.25f

        val assistInt = when (assistMode) {
            AssistTool.NONE -> 0
            AssistTool.FOCUS_PEAKING -> 1
            AssistTool.FALSE_COLOR -> 2
            AssistTool.ZEBRA_STRIPES -> 3
        }

        // Render OES or simulated pattern
        if (isCameraActive || hasNewOesFrame) {
            renderTexture(
                program = programOes,
                textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId = oesTextureId,
                matrix = stMatrix,
                assistInt = assistInt,
                kelvinR = kelvinR,
                kelvinB = kelvinB,
                tintG = tintG
            )
        } else {
            // Simulated cinema test chart with dynamic focus & exposure elements
            renderTexture(
                program = program2D,
                textureTarget = GLES20.GL_TEXTURE_2D,
                textureId = testTextureId,
                matrix = identityMatrix,
                assistInt = assistInt,
                kelvinR = kelvinR,
                kelvinB = kelvinB,
                tintG = tintG
            )
        }

        // Periodic simulated/sample histogram calculation (e.g. 15 Hz)
        val now = SystemClock.uptimeMillis()
        if (now - lastHistogramCalcTime > 66) {
            lastHistogramCalcTime = now
            computeHistogram()
        }
    }

    private fun renderTexture(
        program: Int,
        textureTarget: Int,
        textureId: Int,
        matrix: FloatArray,
        assistInt: Int,
        kelvinR: Float,
        kelvinB: Float,
        tintG: Float
    ) {
        GLES20.glUseProgram(program)

        val posHandle = GLES20.glGetAttribLocation(program, "a_position")
        val texHandle = GLES20.glGetAttribLocation(program, "a_texCoord")
        val matrixHandle = GLES20.glGetUniformLocation(program, "u_stMatrix")
        val samplerHandle = GLES20.glGetUniformLocation(program, "u_texture")
        val sizeHandle = GLES20.glGetUniformLocation(program, "u_texSize")
        val assistHandle = GLES20.glGetUniformLocation(program, "u_assist_mode")
        val peakColorHandle = GLES20.glGetUniformLocation(program, "u_peaking_color")
        val peakThreshHandle = GLES20.glGetUniformLocation(program, "u_peaking_threshold")
        val zebraHandle = GLES20.glGetUniformLocation(program, "u_zebra_threshold")
        val kelvinRHandle = GLES20.glGetUniformLocation(program, "u_kelvin_r")
        val kelvinBHandle = GLES20.glGetUniformLocation(program, "u_kelvin_b")
        val tintGHandle = GLES20.glGetUniformLocation(program, "u_tint_g")
        val exposureHandle = GLES20.glGetUniformLocation(program, "u_exposure_multiplier")

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, matrix, 0)
        GLES20.glUniform2f(sizeHandle, viewWidth.toFloat(), viewHeight.toFloat())
        GLES20.glUniform1i(assistHandle, assistInt)
        GLES20.glUniform3f(peakColorHandle, peakingColor.r, peakingColor.g, peakingColor.b)
        GLES20.glUniform1f(peakThreshHandle, peakingThreshold)
        GLES20.glUniform1f(zebraHandle, zebraThreshold)
        GLES20.glUniform1f(kelvinRHandle, kelvinR)
        GLES20.glUniform1f(kelvinBHandle, kelvinB)
        GLES20.glUniform1f(tintGHandle, tintG)
        GLES20.glUniform1f(exposureHandle, exposureMultiplier)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun computeHistogram() {
        // Generates realistic luminance distribution influenced by exposureMultiplier
        val exposure = exposureMultiplier.coerceIn(0.1f, 4.0f)
        val peakIndex = ((exposure * 12).toInt()).coerceIn(4, 28)
        for (i in 0 until 32) {
            val dist = Math.abs(i - peakIndex).toFloat()
            val base = Math.exp((-dist * dist / 18.0)).toFloat()
            // Add shadow and highlight rolls
            val shadow = if (i < 4) 0.15f / (i + 1) else 0f
            val highlight = if (i > 27 && exposure > 1.2f) (i - 27) * 0.12f else 0f
            histogramBins[i] = (base + shadow + highlight).coerceIn(0.02f, 1.0f)
        }
        onHistogramUpdate?.invoke(histogramBins.clone())
    }

    private fun generateSimulatedCinemaPattern(textureId: Int) {
        val width = 1280
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background dark gradient
        canvas.drawColor(Color.rgb(18, 18, 22))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw 10 IRE Zone Gradient Bars
        val barHeight = 80f
        val numBars = 10
        val barWidth = width.toFloat() / numBars
        for (i in 0 until numBars) {
            val lum = (i.toFloat() / (numBars - 1))
            val greyVal = (lum * 255).toInt()
            paint.color = Color.rgb(greyVal, greyVal, greyVal)
            canvas.drawRect(i * barWidth, 40f, (i + 1) * barWidth, 40f + barHeight, paint)
        }

        // Draw Cinema Color Chart Swatches (Skin tones, Primary R/G/B, 18% grey)
        val swatches = listOf(
            Color.rgb(102, 60, 48),    // Dark skin
            Color.rgb(197, 140, 120),  // Light skin (54 IRE Pink Zone)
            Color.rgb(90, 120, 160),   // Blue sky
            Color.rgb(90, 150, 80),    // Foliage (Green Zone)
            Color.rgb(128, 128, 128),  // 18% Middle Grey (38-42 IRE)
            Color.rgb(220, 220, 220),  // 90% White (72 IRE)
            Color.rgb(200, 30, 30),    // Cinema Red
            Color.rgb(240, 220, 20)    // High Yellow (Zebra test)
        )
        val swatchWidth = width.toFloat() / swatches.size
        for ((idx, color) in swatches.withIndex()) {
            paint.color = color
            canvas.drawRect(idx * swatchWidth, 140f, (idx + 1) * swatchWidth, 230f, paint)
        }

        // Draw High-frequency focus peaking test targets (fine concentric circles & radial lines)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2f

        val cx = width * 0.5f
        val cy = height * 0.62f
        for (r in 10..180 step 12) {
            canvas.drawCircle(cx, cy, r.toFloat(), paint)
        }
        for (a in 0 until 360 step 30) {
            val rad = Math.toRadians(a.toDouble())
            val x2 = cx + (Math.cos(rad) * 180).toFloat()
            val y2 = cy + (Math.sin(rad) * 180).toFloat()
            canvas.drawLine(cx, cy, x2, y2, paint)
        }

        // Highlight clipping test zone for Zebra stripes (near 100% IRE)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(250, 250, 250)
        canvas.drawRect(width * 0.8f, height * 0.5f, width * 0.95f, height * 0.85f, paint)

        // Shadow clipped test zone (< 2% IRE)
        paint.color = Color.rgb(2, 2, 3)
        canvas.drawRect(width * 0.05f, height * 0.5f, width * 0.2f, height * 0.85f, paint)

        // Labels
        paint.color = Color.rgb(200, 200, 210)
        paint.textSize = 24f
        paint.strokeWidth = 1f
        canvas.drawText("CINECAM SENSOR TARGET • 18% GREY • SKIN 54 IRE • CLIPPING HIGHLIGHTS", 40f, height - 30f, paint)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable = true
        surfaceView?.requestRender()
    }
}
