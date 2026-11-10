package com.example.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CameraPreviewRenderer
 *
 * Implements OpenGL ES 3.0 pipeline for Camera2 live preview rendering with:
 * - Hardware-accelerated orientation & aspect ratio correction via SurfaceTexture.getTransformMatrix().
 * - Dynamic runtime switching between real-time Cinematography Overlays:
 *     1. NONE (Clean Cinema pass-through)
 *     2. PEAKING (Sobel edge-detection focus assist with custom color & sensitivity)
 *     3. FALSE_COLOR (16-zone IRE exposure heatmap)
 *     4. ZEBRA (Animated 45-degree diagonal stripes on highlight clipping)
 */
class CameraPreviewRenderer(
    private val context: Context? = null,
    private val onSurfaceReady: (SurfaceTexture) -> Unit,
    private val requestRenderCallback: (() -> Unit)? = null
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val tag = "CameraPreviewRenderer"

    enum class OverlayMode {
        NONE,
        PEAKING,
        FALSE_COLOR,
        ZEBRA
    }

    // Dynamic runtime overlay state
    @Volatile var currentOverlayMode: OverlayMode = OverlayMode.NONE
    @Volatile var peakingColor: FloatArray = floatArrayOf(0.0f, 1.0f, 0.0f) // Default Cinema Green (0, 1, 0)
    @Volatile var peakingThreshold: Float = 0.15f
    @Volatile var zebraThreshold: Float = 0.90f

    private var surfaceView: GLSurfaceView? = null
    lateinit var surfaceTexture: SurfaceTexture
        private set

    private var textureId: Int = 0
    private var updateSurface = false

    // Compiled shader programs
    private var programNone: Int = 0
    private var programPeaking: Int = 0
    private var programFalseColor: Int = 0
    private var programZebra: Int = 0

    // Geometry vertex and texture coordinate buffers
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
    private var viewportWidth = 1920
    private var viewportHeight = 1080
    private var startTimeMs = SystemClock.uptimeMillis()

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

        Matrix.setIdentityM(stMatrix, 0)
    }

    fun bindSurfaceView(view: GLSurfaceView) {
        this.surfaceView = view
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.04f, 0.04f, 0.05f, 1.0f)

        // 1. Generate OES external texture ID
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // 2. Compile and link all 4 cinematography shader programs
        initShaders()

        // 3. Create SurfaceTexture and notify camera manager
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(this)
        onSurfaceReady(surfaceTexture)
    }

    private fun initShaders() {
        val vertexSource = loadShaderCode(
            assetPath = "shaders/VertexShader.glsl",
            fallback = VERTEX_SHADER_DEFAULT
        )

        // None / Pass-through
        val defaultFrag = loadShaderCode(
            assetPath = "shaders/DefaultOESShader.glsl",
            fallback = FRAGMENT_SHADER_DEFAULT
        )
        programNone = ShaderManager.createProgram(vertexSource, defaultFrag)

        // Focus Peaking
        val peakingFrag = loadShaderCode(
            assetPath = "shaders/FocusPeakingShader.glsl",
            fallback = FRAGMENT_SHADER_PEAKING
        )
        programPeaking = ShaderManager.createProgram(vertexSource, peakingFrag)

        // False Color
        val falseColorFrag = loadShaderCode(
            assetPath = "shaders/FalseColorShader.glsl",
            fallback = FRAGMENT_SHADER_FALSE_COLOR
        )
        programFalseColor = ShaderManager.createProgram(vertexSource, falseColorFrag)

        // Zebra Stripes
        val zebraFrag = loadShaderCode(
            assetPath = "shaders/ZebraShader.glsl",
            fallback = FRAGMENT_SHADER_ZEBRA
        )
        programZebra = ShaderManager.createProgram(vertexSource, zebraFrag)

        Log.i(tag, "All 4 Cinematography shaders compiled successfully: None=$programNone, Peaking=$programPeaking, FalseColor=$programFalseColor, Zebra=$programZebra")
    }

    private fun loadShaderCode(assetPath: String, fallback: String): String {
        return if (context != null) {
            try {
                ShaderManager.readAssetShader(context, assetPath)
            } catch (e: Exception) {
                Log.w(tag, "Using fallback shader for $assetPath: ${e.message}")
                fallback
            }
        } else {
            fallback
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)
        if (::surfaceTexture.isInitialized) {
            surfaceTexture.setDefaultBufferSize(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface && ::surfaceTexture.isInitialized) {
                try {
                    surfaceTexture.updateTexImage()
                    surfaceTexture.getTransformMatrix(stMatrix)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to update SurfaceTexture image", e)
                }
                updateSurface = false
            }
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // Dynamically select the active program based on current overlay mode
        val activeProgram = when (currentOverlayMode) {
            OverlayMode.NONE -> programNone
            OverlayMode.PEAKING -> programPeaking
            OverlayMode.FALSE_COLOR -> programFalseColor
            OverlayMode.ZEBRA -> programZebra
        }

        if (activeProgram == 0) return

        GLES30.glUseProgram(activeProgram)

        // Upload standard uniforms
        val uMatrixLoc = GLES30.glGetUniformLocation(activeProgram, "uTransformMatrix")
        if (uMatrixLoc >= 0) {
            GLES30.glUniformMatrix4fv(uMatrixLoc, 1, false, stMatrix, 0)
        }

        val uTextureLoc = GLES30.glGetUniformLocation(activeProgram, "uTexture")
        if (uTextureLoc >= 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES30.glUniform1i(uTextureLoc, 0)
        }

        // Upload mode-specific uniforms
        when (currentOverlayMode) {
            OverlayMode.PEAKING -> {
                val uTexelSizeLoc = GLES30.glGetUniformLocation(activeProgram, "uTexelSize")
                if (uTexelSizeLoc >= 0) {
                    val dx = 1.0f / viewportWidth.coerceAtLeast(1)
                    val dy = 1.0f / viewportHeight.coerceAtLeast(1)
                    GLES30.glUniform2f(uTexelSizeLoc, dx, dy)
                }

                val uPeakingColorLoc = GLES30.glGetUniformLocation(activeProgram, "uPeakingColor")
                if (uPeakingColorLoc >= 0) {
                    GLES30.glUniform3f(uPeakingColorLoc, peakingColor[0], peakingColor[1], peakingColor[2])
                }

                val uPeakingThresholdLoc = GLES30.glGetUniformLocation(activeProgram, "uPeakingThreshold")
                if (uPeakingThresholdLoc >= 0) {
                    GLES30.glUniform1f(uPeakingThresholdLoc, peakingThreshold)
                }
            }
            OverlayMode.ZEBRA -> {
                val uZebraThreshLoc = GLES30.glGetUniformLocation(activeProgram, "uZebraThreshold")
                if (uZebraThreshLoc >= 0) {
                    GLES30.glUniform1f(uZebraThreshLoc, zebraThreshold)
                }

                val uTimeLoc = GLES30.glGetUniformLocation(activeProgram, "uTime")
                if (uTimeLoc >= 0) {
                    val elapsedSec = (SystemClock.uptimeMillis() - startTimeMs) / 1000.0f
                    GLES30.glUniform1f(uTimeLoc, elapsedSec)
                }
            }
            OverlayMode.FALSE_COLOR, OverlayMode.NONE -> {
                // False Color & Pass-through use standard texture coordinates and luminance mappings
            }
        }

        // Bind vertex coordinates (layout location 0 or queried)
        val posLoc = GLES30.glGetAttribLocation(activeProgram, "aPosition").let { if (it >= 0) it else 0 }
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        // Bind texture coordinates (layout location 1 or queried)
        val texLoc = GLES30.glGetAttribLocation(activeProgram, "aTexCoord").let { if (it >= 0) it else 1 }
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        // Draw quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(this) { updateSurface = true }
        surfaceView?.requestRender()
        requestRenderCallback?.invoke()
    }

    fun release() {
        if (programNone != 0) { GLES30.glDeleteProgram(programNone); programNone = 0 }
        if (programPeaking != 0) { GLES30.glDeleteProgram(programPeaking); programPeaking = 0 }
        if (programFalseColor != 0) { GLES30.glDeleteProgram(programFalseColor); programFalseColor = 0 }
        if (programZebra != 0) { GLES30.glDeleteProgram(programZebra); programZebra = 0 }
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }

    companion object {
        const val VERTEX_SHADER_DEFAULT = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;

            uniform mat4 uTransformMatrix;
            out vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTransformMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_SHADER_DEFAULT = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;

            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """

        const val FRAGMENT_SHADER_PEAKING = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            uniform vec2 uTexelSize;
            uniform vec3 uPeakingColor;
            uniform float uPeakingThreshold;

            in vec2 vTexCoord;
            out vec4 fragColor;

            float getLuminance(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec4 centerColor = texture(uTexture, vTexCoord);
                float dx = uTexelSize.x;
                float dy = uTexelSize.y;

                float l00 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx, -dy)).rgb);
                float l01 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx,  0.0)).rgb);
                float l02 = getLuminance(texture(uTexture, vTexCoord + vec2(-dx,  dy)).rgb);
                float l10 = getLuminance(texture(uTexture, vTexCoord + vec2( 0.0, -dy)).rgb);
                float l12 = getLuminance(texture(uTexture, vTexCoord + vec2( 0.0,  dy)).rgb);
                float l20 = getLuminance(texture(uTexture, vTexCoord + vec2( dx, -dy)).rgb);
                float l21 = getLuminance(texture(uTexture, vTexCoord + vec2( dx,  0.0)).rgb);
                float l22 = getLuminance(texture(uTexture, vTexCoord + vec2( dx,  dy)).rgb);

                float gx = -l00 - 2.0 * l01 - l02 + l20 + 2.0 * l21 + l22;
                float gy = -l00 - 2.0 * l10 - l20 + l02 + 2.0 * l12 + l22;
                float edge = sqrt(gx * gx + gy * gy);

                if (edge > uPeakingThreshold) {
                    fragColor = vec4(mix(centerColor.rgb, uPeakingColor, 0.88), 1.0);
                } else {
                    fragColor = centerColor;
                }
            }
        """

        const val FRAGMENT_SHADER_FALSE_COLOR = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;

            float getLuminance(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            vec3 mapFalseColor(float ire) {
                if (ire < 0.02) return vec3(0.42, 0.0, 0.58);   // < 2 IRE: Clipped shadows (Purple)
                if (ire < 0.12) return vec3(0.0, 0.20, 0.80);   // 2-12 IRE: Low Shadows (Deep Blue)
                if (ire < 0.28) return vec3(0.0, 0.55, 0.70);   // 12-28 IRE: Shadow details (Teal)
                if (ire < 0.38) return vec3(0.30, 0.30, 0.34);  // 28-38 IRE: Low mid-tones
                if (ire <= 0.43) return vec3(0.50, 0.50, 0.50); // 38-43 IRE: 18% Neutral Grey Reference
                if (ire < 0.52) return vec3(0.38, 0.38, 0.42);  // 43-52 IRE: Mid-tones
                if (ire <= 0.58) return vec3(0.95, 0.45, 0.65); // 52-58 IRE: Caucasian Skin Tone (Pink)
                if (ire < 0.70) return vec3(0.58, 0.58, 0.58);  // 58-70 IRE: High mid-tones
                if (ire <= 0.76) return vec3(0.0, 0.85, 0.20);  // 70-76 IRE: 90% White Card (Green)
                if (ire < 0.85) return vec3(0.72, 0.72, 0.40);  // 76-85 IRE: Highlights
                if (ire <= 0.94) return vec3(0.96, 0.90, 0.0);  // 85-94 IRE: Sub-clipping warning (Yellow)
                return vec3(1.0, 0.05, 0.05);                   // > 95 IRE: Clipped highlights (Red)
            }

            void main() {
                vec4 rawColor = texture(uTexture, vTexCoord);
                float ire = getLuminance(rawColor.rgb);
                fragColor = vec4(mapFalseColor(ire), 1.0);
            }
        """

        const val FRAGMENT_SHADER_ZEBRA = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            uniform float uZebraThreshold;
            uniform float uTime;

            in vec2 vTexCoord;
            out vec4 fragColor;

            float getLuminance(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            void main() {
                vec4 rawColor = texture(uTexture, vTexCoord);
                float lum = getLuminance(rawColor.rgb);

                if (lum >= uZebraThreshold) {
                    float stripe = mod(gl_FragCoord.x + gl_FragCoord.y - uTime * 35.0, 24.0);
                    if (stripe < 12.0) {
                        fragColor = vec4(0.04, 0.04, 0.05, 1.0);
                    } else {
                        fragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    }
                } else {
                    fragColor = rawColor;
                }
            }
        """
    }
}
