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
        val name: String,
        val kind: String,
        val tempKelvin: Int,
        val galaxyId: Int
    )

    data class OrbitBody(
        val name: String,
        val radius: Float,
        val speed: Float,
        val size: Float,
        val color: FloatArray,
        val tilt: Float,
        val kind: String,
        val description: String
    )

    var yaw = 0.35f
    var pitch = 0.2f
    var cameraDistance = 12f

    var statusCallback: ((String) -> Unit)? = null
    var infoCallback: ((String) -> Unit)? = null

    private val stars = mutableListOf<Star>()
    private var selectedStarIndex = -1
    private var selectedLocalIndex = -2 // -2 none, -1 center star, >=0 orbit body

    private lateinit var starBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var starCount = 0

    private lateinit var nebulaBuffer: FloatBuffer
    private lateinit var nebulaColorBuffer: FloatBuffer
    private var nebulaCount = 0

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var pointSizeHandle = 0
    private var timeHandle = 0

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
    private var exoticMode = false

    private val systemBodies = mutableListOf<OrbitBody>()
    private var localStarColor = floatArrayOf(1.0f, 0.9f, 0.5f, 1.0f)
    private var localStarName = "Selected Star"
    private var localStarType = "Yellow star"
    private var localStarTemp = 5800

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0.02f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        buildUniverse(24000)
        buildNebula(2600)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")

        Matrix.setIdentityM(model, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width
        heightPx = height
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 60f, ratio, 0.1f, 220f)
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
        GLES20.glUniform1f(timeHandle, angle)

        if (mode == 0) {
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, angle * 8f, 0f, 1f, 0f)
            Matrix.multiplyMM(temp, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
            System.arraycopy(mvp, 0, currentMvp, 0, 16)

            drawPoints(nebulaBuffer, nebulaColorBuffer, nebulaCount, 18f, mvp)
            drawPoints(starBuffer, colorBuffer, starCount, 4.4f, mvp)

            if (selectedStarIndex >= 0 && selectedStarIndex < stars.size) {
                val s = stars[selectedStarIndex]
                drawSinglePoint(s.x, s.y, s.z, floatArrayOf(1f, 1f, 1f, 1f), 18f, mvp)
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

        if (!exoticMode) {
            drawSinglePoint(0f, 0f, 0f, localStarColor, 26f, mvp)
            drawSinglePoint(0f, 0f, 0f, floatArrayOf(localStarColor[0], localStarColor[1], localStarColor[2], 1f), 48f, mvp)
        } else {
            drawBlackHoleSystem(mvp)
        }

        if (selectedLocalIndex == -1) {
            drawSinglePoint(0f, 0f, 0f, floatArrayOf(1f, 1f, 1f, 1f), 54f, mvp)
        }

        val orbitLinePositions = ArrayList<Float>()
        val orbitLineColors = ArrayList<Float>()
        val localPositions = ArrayList<Float>()
        val localColors = ArrayList<Float>()
        val selectedPositions = ArrayList<Float>()
        val selectedColors = ArrayList<Float>()

        for ((i, b) in systemBodies.withIndex()) {
            val a = angle * b.speed + i * 1.15f
            val px = cos(a.toDouble()).toFloat() * b.radius
            val py = sin((a * 0.35f).toDouble()).toFloat() * b.tilt
            val pz = sin(a.toDouble()).toFloat() * b.radius

            localPositions.add(px)
            localPositions.add(py)
            localPositions.add(pz)

            localColors.add(b.color[0])
            localColors.add(b.color[1])
            localColors.add(b.color[2])
            localColors.add(1f)

            if (selectedLocalIndex == i) {
                selectedPositions.add(px)
                selectedPositions.add(py)
                selectedPositions.add(pz)

                selectedColors.add(1f)
                selectedColors.add(1f)
                selectedColors.add(1f)
                selectedColors.add(1f)
            }

            if (b.kind == "Planet") {
                val moonAngle = angle * (b.speed * 2.4f) + i * 0.65f
                val mx = px + cos(moonAngle.toDouble()).toFloat() * 0.35f
                val my = py + sin((moonAngle * 0.5f).toDouble()).toFloat() * 0.04f
                val mz = pz + sin(moonAngle.toDouble()).toFloat() * 0.35f
                drawSinglePoint(mx, my, mz, floatArrayOf(0.8f, 0.82f, 0.88f, 1f), 5f, mvp)
            }

            val segments = 72
            for (s in 0..segments) {
                val t = (s.toFloat() / segments.toFloat()) * (Math.PI.toFloat() * 2f)
                val ox = cos(t.toDouble()).toFloat() * b.radius
                val oy = sin((t * 0.35f).toDouble()).toFloat() * b.tilt
                val oz = sin(t.toDouble()).toFloat() * b.radius
                orbitLinePositions.add(ox)
                orbitLinePositions.add(oy)
                orbitLinePositions.add(oz)

                orbitLineColors.add(0.3f)
                orbitLineColors.add(0.3f)
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

        if (localPositions.isNotEmpty()) {
            drawPoints(
                floatBufferOf(localPositions.toFloatArray()),
                floatBufferOf(localColors.toFloatArray()),
                localPositions.size / 3,
                10f,
                mvp
            )
        }

        if (selectedPositions.isNotEmpty()) {
            drawPoints(
                floatBufferOf(selectedPositions.toFloatArray()),
                floatBufferOf(selectedColors.toFloatArray()),
                selectedPositions.size / 3,
                18f,
                mvp
            )
        }
    }

    private fun drawBlackHoleSystem(mvpMatrix: FloatArray) {
        val ringPositions = ArrayList<Float>()
        val ringColors = ArrayList<Float>()

        for (i in 0..180) {
            val t = (i.toFloat() / 180f) * (Math.PI.toFloat() * 2f)
            val r = 0.55f + 0.08f * sin((angle * 6f + t * 3f).toDouble()).toFloat()
            val x = cos(t.toDouble()).toFloat() * r
            val y = sin((t * 0.6f).toDouble()).toFloat() * 0.09f
            val z = sin(t.toDouble()).toFloat() * r

            ringPositions.add(x)
            ringPositions.add(y)
            ringPositions.add(z)

            ringColors.add(1.0f)
            ringColors.add(0.6f + 0.2f * sin((t * 2f).toDouble()).toFloat())
            ringColors.add(0.18f)
            ringColors.add(1.0f)
        }

        drawLines(
            floatBufferOf(ringPositions.toFloatArray()),
            floatBufferOf(ringColors.toFloatArray()),
            ringPositions.size / 3,
            mvpMatrix
        )

        drawSinglePoint(0f, 0f, 0f, floatArrayOf(0.08f, 0.08f, 0.12f, 1f), 36f, mvpMatrix)
        drawSinglePoint(0f, 0f, 0f, floatArrayOf(0.35f, 0.45f, 0.95f, 1f), 52f, mvpMatrix)
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

                val typeRoll = Random.nextFloat()
                val kind: String
                val tempKelvin: Int
                val rgb: FloatArray

                if (typeRoll < 0.1f) {
                    kind = "Blue giant"
                    tempKelvin = 12000
                    rgb = floatArrayOf(0.7f, 0.8f, 1.0f)
                } else if (typeRoll < 0.35f) {
                    kind = "White star"
                    tempKelvin = 9000
                    rgb = floatArrayOf(1.0f, 1.0f, 1.0f)
                } else if (typeRoll < 0.78f) {
                    kind = "Yellow star"
                    tempKelvin = 5800
                    rgb = floatArrayOf(1.0f, 0.9f, 0.65f)
                } else {
                    kind = "Red giant"
                    tempKelvin = 3500
                    rgb = floatArrayOf(1.0f, 0.65f, 0.55f)
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
                        name = "Star-" + gi + "-" + i,
                        kind = kind,
                        tempKelvin = tempKelvin,
                        galaxyId = gi
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

    private fun buildNebula(count: Int) {
        val positions = FloatArray(count * 3)
        val colors = FloatArray(count * 4)

        for (i in 0 until count) {
            val cluster = i % 3
            val base = when (cluster) {
                0 -> floatArrayOf(0f, 0f, 0f)
                1 -> floatArrayOf(10f, 2f, -12f)
                else -> floatArrayOf(-13f, -1.5f, 8f)
            }

            val radius = Random.nextFloat() * 2.4f
            val theta = Random.nextFloat() * Math.PI.toFloat() * 2f
            val phi = Random.nextFloat() * Math.PI.toFloat() * 2f

            val x = cos(theta.toDouble()).toFloat() * radius + base[0]
            val y = sin(phi.toDouble()).toFloat() * 0.8f * radius + base[1]
            val z = sin(theta.toDouble()).toFloat() * radius + base[2]

            positions[i * 3] = x
            positions[i * 3 + 1] = y
            positions[i * 3 + 2] = z

            val nebType = Random.nextFloat()
            val c = if (nebType < 0.5f) {
                floatArrayOf(0.25f, 0.35f, 0.8f, 0.25f)
            } else {
                floatArrayOf(0.8f, 0.28f, 0.65f, 0.22f)
            }

            colors[i * 4] = c[0]
            colors[i * 4 + 1] = c[1]
            colors[i * 4 + 2] = c[2]
            colors[i * 4 + 3] = c[3]
        }

        nebulaCount = count
        nebulaBuffer = floatBufferOf(positions)
        nebulaColorBuffer = floatBufferOf(colors)
    }

    fun pickAt(screenX: Float, screenY: Float) {
        if (mode == 0) {
            pickGalaxyStar(screenX, screenY)
        } else {
            pickLocalBody(screenX, screenY)
        }
    }

    private fun pickGalaxyStar(screenX: Float, screenY: Float) {
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
            val s = stars[best]
            localStarColor = floatArrayOf(s.r, s.g, s.b, 1f)
            localStarName = s.name
            localStarType = s.kind
            localStarTemp = s.tempKelvin

            statusCallback?.invoke(
                "Selected " + s.name + " | " + s.kind + " | double tap local mode | long press exotic mode"
            )
            infoCallback?.invoke(
                "Name: " + s.name +
                    "\nType: " + s.kind +
                    "\nTemperature: " + s.tempKelvin + " K" +
                    "\nGalaxy cluster: " + s.galaxyId +
                    "\nPosition: (" +
                    String.format("%.2f", s.x) + ", " +
                    String.format("%.2f", s.y) + ", " +
                    String.format("%.2f", s.z) + ")"
            )
        } else {
            statusCallback?.invoke("No star selected")
        }
    }

    private fun pickLocalBody(screenX: Float, screenY: Float) {
        var best = -2
        var bestDist = Float.MAX_VALUE

        run {
            val center = projectToScreen(0f, 0f, 0f)
            val dx = screenX - center[0]
            val dy = screenY - center[1]
            val d = sqrt(dx * dx + dy * dy)
            if (d < 55f) {
                best = -1
                bestDist = d
            }
        }

        for ((i, b) in systemBodies.withIndex()) {
            val p = localBodyPosition(i, b)
            val s = projectToScreen(p[0], p[1], p[2])
            val dx = screenX - s[0]
            val dy = screenY - s[1]
            val d = sqrt(dx * dx + dy * dy)
            if (d < 35f && d < bestDist) {
                best = i
                bestDist = d
            }
        }

        selectedLocalIndex = best

        if (best == -1) {
            statusCallback?.invoke("Selected local star")
            infoCallback?.invoke(
                "Name: " + localStarName +
                    "\nType: " + if (exoticMode) "Exotic singularity mode" else localStarType +
                    "\nTemperature: " + localStarTemp + " K" +
                    "\nLocal bodies: " + systemBodies.size +
                    "\nMode: " + if (exoticMode) "Accretion disk / black-hole style" else "Standard star system"
            )
        } else if (best >= 0 && best < systemBodies.size) {
            val b = systemBodies[best]
            statusCallback?.invoke("Selected " + b.name + " | " + b.kind)
            infoCallback?.invoke(
                "Name: " + b.name +
                    "\nKind: " + b.kind +
                    "\nOrbit radius: " + String.format("%.2f", b.radius) +
                    "\nOrbit speed factor: " + String.format("%.2f", b.speed) +
                    "\nSize: " + String.format("%.1f", b.size) +
                    "\nNote: " + b.description
            )
        } else {
            statusCallback?.invoke("No local body selected")
        }
    }

    private fun projectToScreen(x: Float, y: Float, z: Float): FloatArray {
        val vec = floatArrayOf(x, y, z, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, currentMvp, 0, vec, 0)

        if (out[3] == 0f) return floatArrayOf(-9999f, -9999f)

        val ndcX = out[0] / out[3]
        val ndcY = out[1] / out[3]

        val sx = (ndcX * 0.5f + 0.5f) * widthPx
        val sy = (1f - (ndcY * 0.5f + 0.5f)) * heightPx
        return floatArrayOf(sx, sy)
    }

    fun toggleSystemMode() {
        if (mode == 0) {
            if (selectedStarIndex < 0 || selectedStarIndex >= stars.size) {
                statusCallback?.invoke("Tap a star first, then double tap")
                return
            }
            buildSystemForSelectedStar()
            mode = 1
            selectedLocalIndex = -1
            cameraDistance = 7.5f
            yaw = 0.35f
            pitch = 0.18f

            infoCallback?.invoke(
                "Name: " + localStarName +
                    "\nType: " + if (exoticMode) "Exotic singularity mode" else localStarType +
                    "\nTemperature: " + localStarTemp + " K" +
                    "\nLocal bodies: " + systemBodies.size +
                    "\nMode: " + if (exoticMode) "Accretion disk / black-hole style" else "Standard star system"
            )

            statusCallback?.invoke(
                if (exoticMode) {
                    "Exotic local mode | tap bodies for info | double tap to return"
                } else {
                    "Local system mode | tap bodies for info | double tap to return"
                }
            )
        } else {
            mode = 0
            selectedLocalIndex = -2
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

    fun toggleExoticMode() {
        exoticMode = !exoticMode
        statusCallback?.invoke(
            if (exoticMode) {
                "Exotic mode ON | double tap local mode for black-hole style system"
            } else {
                "Exotic mode OFF | normal star system mode"
            }
        )
    }

    private fun buildSystemForSelectedStar() {
        systemBodies.clear()

        val colors = listOf(
            floatArrayOf(0.4f, 0.9f, 1.0f, 1f),
            floatArrayOf(0.4f, 1.0f, 0.5f, 1f),
            floatArrayOf(1.0f, 0.45f, 0.35f, 1f),
            floatArrayOf(0.9f, 0.8f, 0.35f, 1f),
            floatArrayOf(0.75f, 0.65f, 1.0f, 1f)
        )

        val descs = listOf(
            "Inner rocky world",
            "Temperate atmosphere candidate",
            "Dense volcanic body",
            "Ringed gas giant analogue",
            "Outer ice giant analogue"
        )

        for (i in 0 until 5) {
            systemBodies.add(
                OrbitBody(
                    name = "Planet-" + (i + 1),
                    radius = 1.4f + i * 1.2f,
                    speed = 0.7f / (1f + i * 0.35f),
                    size = 7f + i,
                    color = colors[i % colors.size],
                    tilt = (if (i % 2 == 0) 0.12f else -0.12f) * (i + 1),
                    kind = if (i < 3) "Planet" else "Gas giant",
                    description = descs[i % descs.size]
                )
            )
        }
    }

    private fun localBodyPosition(index: Int, b: OrbitBody): FloatArray {
        val a = angle * b.speed + index * 1.15f
        val px = cos(a.toDouble()).toFloat() * b.radius
        val py = sin((a * 0.35f).toDouble()).toFloat() * b.tilt
        val pz = sin(a.toDouble()).toFloat() * b.radius
        return floatArrayOf(px, py, pz)
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

    private fun drawSinglePoint(
        x: Float,
        y: Float,
        z: Float,
        color: FloatArray,
        pointSize: Float,
        mvpMatrix: FloatArray
    ) {
        val p = floatBufferOf(floatArrayOf(x, y, z))
        val c = floatBufferOf(floatArrayOf(color[0], color[1], color[2], color[3]))
        drawPoints(p, c, 1, pointSize, mvpMatrix)
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
            uniform float uTime;

            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;
            uniform float uTime;

            void main() {
                vec2 c = gl_PointCoord - vec2(0.5);
                float d = length(c);
                if (d > 0.5) discard;
                float glow = 1.0 - smoothstep(0.0, 0.5, d);
                float shimmer = 0.94 + 0.06 * sin(uTime * 8.0 + d * 18.0);
                gl_FragColor = vec4(vColor.rgb * (0.55 + glow * 1.45) * shimmer, glow * vColor.a);
            }
        """
    }
}
