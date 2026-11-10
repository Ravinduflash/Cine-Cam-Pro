package com.example.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 shader program specifically designed to render GL_TEXTURE_EXTERNAL_OES
 * frames from SurfaceTexture with the 4x4 orientation & scaling transform matrix.
 */
class OESShaderProgram {

    private val tag = "OESShaderProgram"

    companion object {
        const val VERTEX_SHADER_CODE = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec2 aTexCoord;

            uniform mat4 uTransformMatrix;
            out vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                // Correct camera sensor orientation and aspect ratio from SurfaceTexture
                vTexCoord = (uTransformMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_SHADER_CODE = """
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

        // Fallback for GLES 2.0 / legacy emulator drivers
        const val VERTEX_SHADER_CODE_LEGACY = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTransformMatrix;
            varying vec2 vTexCoord;

            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTransformMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_SHADER_CODE_LEGACY = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;

            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }

    var programId: Int = 0
        private set

    private var uTransformMatrixLocation: Int = -1
    private var uTextureLocation: Int = -1
    private var aPositionLocation: Int = -1
    private var aTexCoordLocation: Int = -1

    init {
        compileAndLink()
    }

    private fun compileAndLink() {
        try {
            // Attempt GLES 3.0 compilation first
            val vertexShader = GlShaderUtil.loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_CODE.trimIndent())
            val fragmentShader = GlShaderUtil.loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE.trimIndent())

            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vertexShader)
            GLES30.glAttachShader(prog, fragmentShader)
            GLES30.glLinkProgram(prog)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != 0) {
                programId = prog
            } else {
                Log.w(tag, "GLES 3.0 program link failed, falling back to legacy OES shader")
                GLES30.glDeleteProgram(prog)
            }
        } catch (e: Exception) {
            Log.w(tag, "GLES 3.0 compilation exception, attempting legacy OES shader", e)
        }

        // Fallback to standard GLES 2.0 / 3.0 compatible OES shader if needed
        if (programId == 0) {
            val vShader = GlShaderUtil.loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_CODE_LEGACY.trimIndent())
            val fShader = GlShaderUtil.loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE_LEGACY.trimIndent())
            programId = GLES30.glCreateProgram().also {
                GLES30.glAttachShader(it, vShader)
                GLES30.glAttachShader(it, fShader)
                GLES30.glLinkProgram(it)
            }
        }

        uTransformMatrixLocation = GLES30.glGetUniformLocation(programId, "uTransformMatrix")
        uTextureLocation = GLES30.glGetUniformLocation(programId, "uTexture")
        aPositionLocation = GLES30.glGetAttribLocation(programId, "aPosition")
        aTexCoordLocation = GLES30.glGetAttribLocation(programId, "aTexCoord")
    }

    /**
     * Draws the OES external texture applying the 4x4 SurfaceTexture transform matrix.
     */
    fun draw(
        textureId: Int,
        vertexBuffer: FloatBuffer,
        texCoordBuffer: FloatBuffer,
        transformMatrix: FloatArray
    ) {
        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Upload transform matrix
        GLES30.glUniformMatrix4fv(uTransformMatrixLocation, 1, false, transformMatrix, 0)

        // Bind OES texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(uTextureLocation, 0)

        // Bind vertex coordinates (location 0 or queried)
        val posLoc = if (aPositionLocation >= 0) aPositionLocation else 0
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        // Bind texture coordinates (location 1 or queried)
        val texLoc = if (aTexCoordLocation >= 0) aTexCoordLocation else 1
        GLES30.glEnableVertexAttribArray(texLoc)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        // Render full quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(texLoc)
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
