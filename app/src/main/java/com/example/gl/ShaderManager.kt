package com.example.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ShaderManager: Utility class to load, compile, and link OpenGL ES vertex and fragment shaders.
 * Supports both raw GLSL code strings and reading directly from Android assets.
 */
object ShaderManager {

    private const val TAG = "ShaderManager"

    /**
     * Reads the entire contents of a shader file located in the application's assets folder.
     */
    fun readAssetShader(context: Context, assetPath: String): String {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read shader asset: $assetPath", e)
            throw RuntimeException("Could not read shader asset $assetPath: ${e.message}")
        }
    }

    /**
     * Compiles a single shader of type [shaderType] (e.g. GLES30.GL_VERTEX_SHADER or GLES30.GL_FRAGMENT_SHADER).
     * Returns the OpenGL shader ID.
     */
    fun compileShader(shaderType: Int, shaderSource: String): Int {
        val shaderId = GLES30.glCreateShader(shaderType)
        if (shaderId == 0) {
            Log.e(TAG, "Could not create new shader for type: $shaderType")
            return 0
        }

        GLES30.glShaderSource(shaderId, shaderSource)
        GLES30.glCompileShader(shaderId)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val infoLog = GLES30.glGetShaderInfoLog(shaderId)
            GLES30.glDeleteShader(shaderId)
            val typeStr = if (shaderType == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
            Log.e(TAG, "Error compiling $typeStr shader:\n$infoLog\nShader source:\n$shaderSource")
            throw RuntimeException("Shader compilation failed ($typeStr): $infoLog")
        }

        return shaderId
    }

    /**
     * Compiles vertex and fragment shaders and links them into an executable GL program.
     * Returns the program ID, or throws a RuntimeException if compilation/linking fails.
     */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShaderId = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vertexShaderId == 0) return 0

        val fragmentShaderId = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShaderId == 0) {
            GLES30.glDeleteShader(vertexShaderId)
            return 0
        }

        val programId = GLES30.glCreateProgram()
        if (programId == 0) {
            Log.e(TAG, "Failed to create OpenGL program object")
            GLES30.glDeleteShader(vertexShaderId)
            GLES30.glDeleteShader(fragmentShaderId)
            return 0
        }

        GLES30.glAttachShader(programId, vertexShaderId)
        GLES30.glAttachShader(programId, fragmentShaderId)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val infoLog = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            GLES30.glDeleteShader(vertexShaderId)
            GLES30.glDeleteShader(fragmentShaderId)
            Log.e(TAG, "Error linking program:\n$infoLog")
            throw RuntimeException("Program linking failed: $infoLog")
        }

        // Shaders can safely be flagged for deletion once linked into the program
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)

        return programId
    }

    /**
     * Checks for any outstanding OpenGL errors and logs them with the operation tag.
     */
    fun checkGlError(operation: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL error in $operation: 0x${Integer.toHexString(error)}")
        }
    }
}
