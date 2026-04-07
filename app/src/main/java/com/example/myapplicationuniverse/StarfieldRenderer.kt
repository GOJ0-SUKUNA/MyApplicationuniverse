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
import kotlin.math.sqrt
import kotlin.random.Random

class StarfieldRenderer : GLSurfaceView.Renderer {

    data class Star(
        val x: Float,
        val y: Float,
        val z: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val size: Float,
        val name: String
    )

    data class OrbitBody(
        val name: String,
        val radius: Float,
        val speed: Float,
        val size: Float,
        val color: FloatArray,
        val tilt: Float
    )

    var yaw = 0.35f
    var pitch = 0.2f
    var cameraDistance = 12f

    var statusCallback: ((String) -> Unit)? = null

    private val stars = mutableListOf<Star>()
    private var selectedStarIndex = -1

    private lateinit var starBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var starCount = 0

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var pointSizeHandle = 0

    private var widthPx = 1
    private var heightPx = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val currentMvp = FloatArray(16)

    private var angle = 0f
    private var mode = 0 // 0 galaxy, 1 local system

    private val systemBodies = mutableListOf<OrbitBody>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0.02f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        buildUniverse(22000)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")

        Matrix.setIdentityM(model, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width
        heightPx = height
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 60f, ratio, 0.1f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        angle += 0.002f

        val camX = (sin(yaw.toDouble()) * cos(pitch.toDouble()) * cameraDistance).toFloat()
        val camY = (sin(pitch.toDouble()) * cameraDistance).toFloat()
        val camZ = (cos(yaw.toDouble()) * cos(pitch.toDouble()) * cameraDistance).toFloat()

        Matrix.setLookAtM(
            view, 0,
            camX, camY, camZ,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        GLES20.glUseProgram(program)

        if (mode == 0) {
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, angle * 8f, 0f, 1f, 0f)
            Matrix.multiplyMM(temp, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
            System.arraycopy(mvp, 0, currentMvp, 0, 16)

            drawPoints(starBuffer, colorBuffer, starCount, 4.2f, mvp)

            if (selectedStarIndex >= 0 && selectedStarIndex < stars.size) {
                val s = stars[selectedStarIndex]
                val selPos = floatArrayOf(s.x, s.y, s.z)
                val selColor = floatArrayOf(1f, 1f, 1f, 1f)
                drawPoints(
                    floatBufferOf(selPos),
                    floatBufferOf(selColor),
                    1,
                    16f,
                    mvp
                )
            }
        } else {
            drawLocalSystem()
        }
    }

    private fun drawLocalSystem() {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(temp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
        System.arraycopy(mvp, 0, currentMvp, 0, 16)

        val starPos = floatArrayOf(0f, 0f, 0f)
        val starColor = floatArrayOf(1.0f, 0.9f, 0.5f, 1.0f)
        drawPoints(floatBufferOf(starPos), floatBufferOf(starColor), 1, 24f, mvp)

        val orbitLinePositions = ArrayList<Float>()
        val orbitLineColors = ArrayList<Float>()
        val bodyPositions = ArrayList<Float>()
        val bodyColors = ArrayList<Float>()

        for ((i, b) in systemBodies.withIndex()) {
            val a = angle * b.speed + i * 1.15f
            val px = cos(a.toDouble()).toFloat() * b.radius
            val py = sin((a * 0.35f).toDouble()).toFloat() * b.tilt
            val pz = sin(a.toDouble()).toFloat() * b.radius

            bodyPositions.add(px)
            bodyPositions.add(py)
            bodyPositions.add(pz)

            bodyColors.add(b.color[0])
            bodyColors.add(b.color[1])
            bodyColors.add(b.color[2])
            bodyColors.add(1f)

            val segments = 72
            for (s in 0..segments) {
                val t = (s.toFloat() / segments.toFloat()) * (Math.PI.toFloat() * 2f)
                val ox = cos(t.toDouble()).toFloat() * b.radius
                val oy = sin((t * 0.35f).toDouble()).toFloat() * b.tilt
                val oz = sin(t.toDouble()).toFloat() * b.radius
                orbitLinePositions.add(ox)
                orbitLinePositions.add(oy)
                orbitLinePositions.add(oz)

                orbitLineColors.add(0.35f)
                orbitLineColors.add(0.35f)
                orbitLineColors.add(0.45f)
                orbitLineColors.add(1f)
            }
        }

        if (orbitLinePositions.isNotEmpty()) {
            drawLines(
                floatBufferOf(orbitLinePositions.toFloatArray()),
                floatBufferOf(orbitLineColors.toFloatArray()),
                orbitLinePositions.size / 3,
                mvp
            )
        }

        if (bodyPositions.isNotEmpty()) {
            drawPoints(
                floatBufferOf(bodyPositions.toFloatArray()),
                floatBufferOf(bodyColors.toFloatArray()),
                bodyPositions.size / 3,
                10f,
                mvp
            )
        }
    }

    private fun buildUniverse(count: Int) {
        stars.clear()

        val galaxies = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(10f, 2f, -12f),
            floatArrayOf(-13f, -1.5f, 8f)
        )

        val perGalaxy = count / galaxies.size

        galaxies.forEachIndexed { gi, offset ->
            for (i in 0 until perGalaxy) {
                val arm = i % 4
                val radius = Random.nextFloat() * (if (gi == 0) 4.8f else 2.8f) + 0.08f
                val baseAngle = radius * 1.9f + arm * (Math.PI.toFloat() / 2f)
                val a = baseAngle + (Random.nextFloat() - 0.5f) * 0.7f

                val x = cos(a.toDouble()).toFloat() * radius + offset[0]
                val z = sin(a.toDouble()).toFloat() * radius + offset[2]
                val y = (Random.nextFloat() - 0.5f) * 0.35f * (1f / (0.22f + radius)) + offset[1]

                val t = Random.nextFloat()
                val rgb = when {
                    t < 0.12f -> floatArrayOf(0.7f, 0.8f, 1.0f)
                    t < 0.42f -> floatArrayOf(1.0f, 1.0f, 1.0f)
                    t < 0.78f -> floatArrayOf(1.0f, 0.9f, 0.65f)
                    else -> floatArrayOf(1.0f, 0.65f, 0.55f)
                }
                val brightness = 0.35f + Random.nextFloat() * 0.65f

                stars.add(
                    Star(
                        x = x,
                        y = y,
                        z = z,
                        r = rgb[0] * brightness,
                        g = rgb[1] * brightness,
                        b = rgb[2] * brightness,
                        size = 2.5f + Random.nextFloat() * 2.5f,
                        name = "Star-" + gi + "-" + i
                    )
                )
            }
        }

        val positions = FloatArray(stars.size * 3)
        val colors = FloatArray(stars.size * 4)

        stars.forEachIndexed { i, s ->
            positions[i * 3] = s.x
            positions[i * 3 + 1] = s.y
            positions[i * 3 + 2] = s.z

            colors[i * 4] = s.r
            colors[i * 4 + 1] = s.g
            colors[i * 4 + 2] = s.b
            colors[i * 4 + 3] = 1.0f
        }

        starCount = stars.size
        starBuffer = floatBufferOf(positions)
        colorBuffer = floatBufferOf(colors)
    }

    fun pickAt(screenX: Float, screenY: Float) {
        if (mode != 0) return

        var best = -1
        var bestDist = Float.MAX_VALUE

        val vec = FloatArray(4)
        val out = FloatArray(4)

        for (i in stars.indices) {
            val s = stars[i]
            vec[0] = s.x
            vec[1] = s.y
            vec[2] = s.z
            vec[3] = 1f

            Matrix.multiplyMV(out, 0, currentMvp, 0, vec, 0)
            if (out[3] <= 0f) continue

            val ndcX = out[0] / out[3]
            val ndcY = out[1] / out[3]

            val sx = (ndcX * 0.5f + 0.5f) * widthPx
            val sy = (1f - (ndcY * 0.5f + 0.5f)) * heightPx

            val dx = screenX - sx
            val dy = screenY - sy
            val d = sqrt(dx * dx + dy * dy)

            if (d < 42f && d < bestDist) {
                best = i
                bestDist = d
            }
        }

        if (best >= 0) {
            selectedStarIndex = best
            statusCallback?.invoke(
                "Selected " + stars[best].name + " | galaxy mode | double tap to enter local system"
            )
        } else {
            statusCallback?.invoke("No star selected")
        }
    }

    fun toggleSystemMode() {
        if (mode == 0) {
            if (selectedStarIndex < 0 || selectedStarIndex >= stars.size) {
                statusCallback?.invoke("Tap a star first, then double tap")
                return
            }
            buildSystemForSelectedStar()
            mode = 1
            cameraDistance = 7.5f
            yaw = 0.35f
            pitch = 0.18f
            statusCallback?.invoke(
                "Local system mode | drag rotate | pinch zoom | double tap to return"
            )
        } else {
            mode = 0
            cameraDistance = 12f
            statusCallback?.invoke(
                if (selectedStarIndex >= 0) {
                    "Returned to galaxy mode | selected " + stars[selectedStarIndex].name
                } else {
                    "Returned to galaxy mode"
                }
            )
        }
    }

    private fun buildSystemForSelectedStar() {
        systemBodies.clear()

        val colors = listOf(
            floatArrayOf(0.4f, 0.9f, 1.0f),
            floatArrayOf(0.4f, 1.0f, 0.5f),
            floatArrayOf(1.0f, 0.45f, 0.35f),
            floatArrayOf(0.9f, 0.8f, 0.35f),
            floatArrayOf(0.75f, 0.65f, 1.0f)
        )

        for (i in 0 until 5) {
            systemBodies.add(
                OrbitBody(
                    name = "Planet-" + (i + 1),
                    radius = 1.4f + i * 1.2f,
                    speed = 0.7f / (1f + i * 0.35f),
                    size = 7f + i,
                    color = colors[i % colors.size],
                    tilt = (if (i % 2 == 0) 0.12f else -0.12f) * (i + 1)
                )
            )
        }
    }

    private fun drawPoints(
        positions: FloatBuffer,
        colors: FloatBuffer,
        count: Int,
        pointSize: Float,
        mvpMatrix: FloatArray
    ) {
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, pointSize)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun drawLines(
        positions: FloatBuffer,
        colors: FloatBuffer,
        count: Int,
        mvpMatrix: FloatArray
    ) {
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, 1f)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun floatBufferOf(values: FloatArray): FloatBuffer {
        return ByteBuffer
            .allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
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
