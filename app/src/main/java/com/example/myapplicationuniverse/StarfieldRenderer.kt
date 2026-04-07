package com.example.myapplicationuniverse

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StarfieldRenderer : GLSurfaceView.Renderer {

    var yaw = 0.4f
    var pitch = 0.25f
    var cameraDistance = 10f

    private lateinit var starBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var starCount = 0

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var pointSizeHandle = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        buildGalaxy(12000)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")

        Matrix.setIdentityM(model, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 60f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val camX = (sin(yaw.toDouble()) * cos(pitch.toDouble()) * cameraDistance).toFloat()
        val camY = (sin(pitch.toDouble()) * cameraDistance).toFloat()
        val camZ = (cos(yaw.toDouble()) * cos(pitch.toDouble()) * cameraDistance).toFloat()

        Matrix.setLookAtM(
            view, 0,
            camX, camY, camZ,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        Matrix.multiplyMM(temp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)

        GLES20.glUseProgram(program)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform1f(pointSizeHandle, 4.0f)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, starBuffer)

        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun buildGalaxy(count: Int) {
        val positions = FloatArray(count * 3)
        val colors = FloatArray(count * 4)

        for (i in 0 until count) {
            val arm = i % 4
            val radius = Random.nextFloat() * 4.5f + 0.1f
            val baseAngle = radius * 1.8f + arm * (Math.PI.toFloat() / 2f)
            val angle = baseAngle + (Random.nextFloat() - 0.5f) * 0.65f

            val x = cos(angle.toDouble()).toFloat() * radius
            val z = sin(angle.toDouble()).toFloat() * radius
            val y = (Random.nextFloat() - 0.5f) * 0.25f * (1f / (0.25f + radius))

            positions[i * 3] = x
            positions[i * 3 + 1] = y
            positions[i * 3 + 2] = z

            val temp = Random.nextFloat()
            val r: Float
            val g: Float
            val b: Float

            when {
                temp < 0.18f -> {
                    r = 0.7f; g = 0.8f; b = 1.0f
                }
                temp < 0.45f -> {
                    r = 1.0f; g = 1.0f; b = 1.0f
                }
                temp < 0.75f -> {
                    r = 1.0f; g = 0.9f; b = 0.65f
                }
                else -> {
                    r = 1.0f; g = 0.65f; b = 0.55f
                }
            }

            val brightness = 0.35f + Random.nextFloat() * 0.65f

            colors[i * 4] = r * brightness
            colors[i * 4 + 1] = g * brightness
            colors[i * 4 + 2] = b * brightness
            colors[i * 4 + 3] = 1.0f
        }

        starCount = count

        starBuffer = ByteBuffer
            .allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        starBuffer.put(positions).position(0)

        colorBuffer = ByteBuffer
            .allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        colorBuffer.put(colors).position(0)
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec4 aColor;
            uniform mat4 uMVP;
            uniform float uPointSize;
            varying vec4 vColor;

            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;

            void main() {
                vec2 c = gl_PointCoord - vec2(0.5);
                float d = length(c);
                if (d > 0.5) discard;
                float glow = 1.0 - smoothstep(0.0, 0.5, d);
                gl_FragColor = vec4(vColor.rgb * (0.55 + glow * 1.45), glow);
            }
        """
    }
}
