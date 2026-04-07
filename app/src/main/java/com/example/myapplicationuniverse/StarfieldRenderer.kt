package com.example.myapplicationuniverse

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
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
        val description: String,
        val atmosphere: Boolean,
        val ringed: Boolean,
        val moonCount: Int
    )

    var yaw = 0.35f
    var pitch = 0.2f
    var cameraDistance = 12f

    var statusCallback: ((String) -> Unit)? = null
    var infoCallback: ((String) -> Unit)? = null

    private val stars = mutableListOf<Star>()
    private var selectedStarIndex = -1
    private var selectedLocalIndex = -2

    private lateinit var starBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private var starCount = 0

    private lateinit var nebulaBuffer: FloatBuffer
    private lateinit var nebulaColorBuffer: FloatBuffer
    private var nebulaCount = 0

    private lateinit var sphereMesh: SphereMesh

    private var pointProgram = 0
    private var pointPositionHandle = 0
    private var pointColorHandle = 0
    private var pointMvpHandle = 0
    private var pointSizeHandle = 0
    private var pointTimeHandle = 0

    private var meshProgram = 0
    private var meshPositionHandle = 0
    private var meshNormalHandle = 0
    private var meshModelHandle = 0
    private var meshMvpHandle = 0
    private var meshColorHandle = 0
    private var meshLightHandle = 0

    private var widthPx = 1
    private var heightPx = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val currentMvp = FloatArray(16)
    private val modelLocal = FloatArray(16)
    private val tempLocal = FloatArray(16)
    private val mvpLocal = FloatArray(16)

    private var angle = 0f
    private var mode = 0
    private var exoticMode = false

    private val systemBodies = mutableListOf<OrbitBody>()
    private var localStarColor = floatArrayOf(1.0f, 0.9f, 0.5f, 1.0f)
    private var localStarName = "Selected Star"
    private var localStarType = "Yellow star"
    private var localStarTemp = 5800

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0.02f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        buildUniverse(24000)
        buildNebula(2600)
        sphereMesh = SphereMesh()

        pointProgram = createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
        pointPositionHandle = GLES20.glGetAttribLocation(pointProgram, "aPosition")
        pointColorHandle = GLES20.glGetAttribLocation(pointProgram, "aColor")
        pointMvpHandle = GLES20.glGetUniformLocation(pointProgram, "uMVP")
        pointSizeHandle = GLES20.glGetUniformLocation(pointProgram, "uPointSize")
        pointTimeHandle = GLES20.glGetUniformLocation(pointProgram, "uTime")

        meshProgram = createProgram(MESH_VERTEX_SHADER, MESH_FRAGMENT_SHADER)
        meshPositionHandle = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        meshNormalHandle = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        meshModelHandle = GLES20.glGetUniformLocation(meshProgram, "uModel")
        meshMvpHandle = GLES20.glGetUniformLocation(meshProgram, "uMVP")
        meshColorHandle = GLES20.glGetUniformLocation(meshProgram, "uColor")
        meshLightHandle = GLES20.glGetUniformLocation(meshProgram, "uLightDir")

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
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
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
                drawSinglePoint(s.x, s.y, s.z, floatArrayOf(1f, 1f, 1f, 0.5f), 28f, mvp)
            }
        } else {
            Matrix.setIdentityM(model, 0)
            Matrix.multiplyMM(temp, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
            System.arraycopy(mvp, 0, currentMvp, 0, 16)
            drawLocalSystem()
        }
    }

    private fun drawLocalSystem() {
        if (!exoticMode) {
            drawStarVisual(0f, 0f, 0f, localStarColor)
        } else {
            drawBlackHoleSystem()
        }

        if (selectedLocalIndex == -1) {
            drawSelectionHalo(0f, 0f, 0f, 0.72f)
        }

        val orbitLinePositions = ArrayList<Float>()
        val orbitLineColors = ArrayList<Float>()

        for ((i, b) in systemBodies.withIndex()) {
            val p = localBodyPosition(i, b)
            val px = p[0]
            val py = p[1]
            val pz = p[2]

            drawOrbitBody(px, py, pz, b, selectedLocalIndex == i)

            repeat(b.moonCount) { mi ->
                val moonP = localMoonPosition(px, py, pz, i, mi)
                drawMoonVisual(moonP[0], moonP[1], moonP[2])
            }

            val segments = 72
            for (s in 0..segments) {
                val t = (s.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
                val ox = cos(t.toDouble()).toFloat() * b.radius
                val oy = sin((t * 0.35f).toDouble()).toFloat() * b.tilt
                val oz = sin(t.toDouble()).toFloat() * b.radius
                orbitLinePositions.add(ox)
                orbitLinePositions.add(oy)
                orbitLinePositions.add(oz)

                orbitLineColors.add(0.28f)
                orbitLineColors.add(0.30f)
                orbitLineColors.add(0.42f)
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
    }

    private fun drawStarVisual(x: Float, y: Float, z: Float, color: FloatArray) {
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.22f), 74f, mvp)
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.42f), 50f, mvp)
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.75f), 30f, mvp)
        drawMeshSphere(x, y, z, 0.42f, floatArrayOf(color[0], color[1], color[2], 1f))
    }

    private fun drawMoonVisual(x: Float, y: Float, z: Float) {
        drawSinglePoint(x, y, z, floatArrayOf(0.84f, 0.86f, 0.92f, 0.18f), 9f, mvp)
        drawMeshSphere(x, y, z, 0.11f, floatArrayOf(0.84f, 0.86f, 0.92f, 1f))
    }

    private fun drawOrbitBody(
        x: Float,
        y: Float,
        z: Float,
        body: OrbitBody,
        selected: Boolean
    ) {
        val sphereRadius = when {
            body.kind.contains("Gas", ignoreCase = true) -> body.size * 0.028f
            else -> body.size * 0.022f
        }

        if (body.atmosphere) {
            drawMeshSphere(
                x, y, z,
                sphereRadius * 1.18f,
                floatArrayOf(body.color[0], body.color[1], body.color[2], 0.22f)
            )
        }

        drawMeshSphere(x, y, z, sphereRadius, floatArrayOf(body.color[0], body.color[1], body.color[2], 1f))

        if (body.ringed) {
            drawPlanetRing(x, y, z, sphereRadius * 1.9f)
        }

        if (selected) {
            drawSelectionHalo(x, y, z, sphereRadius * 2.3f)
        }
    }

    private fun drawMeshSphere(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        color: FloatArray
    ) {
        GLES20.glUseProgram(meshProgram)

        Matrix.setIdentityM(modelLocal, 0)
        Matrix.translateM(modelLocal, 0, x, y, z)
        Matrix.scaleM(modelLocal, 0, radius, radius, radius)

        Matrix.multiplyMM(tempLocal, 0, view, 0, modelLocal, 0)
        Matrix.multiplyMM(mvpLocal, 0, projection, 0, tempLocal, 0)

        GLES20.glUniformMatrix4fv(meshModelHandle, 1, false, modelLocal, 0)
        GLES20.glUniformMatrix4fv(meshMvpHandle, 1, false, mvpLocal, 0)
        GLES20.glUniform4f(meshColorHandle, color[0], color[1], color[2], color[3])
        GLES20.glUniform3f(meshLightHandle, 0.4f, 0.8f, 0.6f)

        GLES20.glEnableVertexAttribArray(meshPositionHandle)
        GLES20.glVertexAttribPointer(meshPositionHandle, 3, GLES20.GL_FLOAT, false, 0, sphereMesh.positionBuffer)

        GLES20.glEnableVertexAttribArray(meshNormalHandle)
        GLES20.glVertexAttribPointer(meshNormalHandle, 3, GLES20.GL_FLOAT, false, 0, sphereMesh.normalBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sphereMesh.vertexCount)

        GLES20.glDisableVertexAttribArray(meshPositionHandle)
        GLES20.glDisableVertexAttribArray(meshNormalHandle)
    }

    private fun drawSelectionHalo(x: Float, y: Float, z: Float, radius: Float) {
        val positions = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val segments = 40

        for (i in 0..segments) {
            val t = (i.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
            positions.add(x + cos(t.toDouble()).toFloat() * radius)
            positions.add(y)
            positions.add(z + sin(t.toDouble()).toFloat() * radius)

            colors.add(1f)
            colors.add(1f)
            colors.add(1f)
            colors.add(1f)
        }

        drawLines(
            floatBufferOf(positions.toFloatArray()),
            floatBufferOf(colors.toFloatArray()),
            positions.size / 3,
            mvp
        )
    }

    private fun drawPlanetRing(x: Float, y: Float, z: Float, radius: Float) {
        val positions = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val segments = 80

        for (i in 0..segments) {
            val t = (i.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
            val rx = x + cos(t.toDouble()).toFloat() * radius
            val ry = y + sin((t * 0.55f).toDouble()).toFloat() * 0.06f
            val rz = z + sin(t.toDouble()).toFloat() * radius

            positions.add(rx)
            positions.add(ry)
            positions.add(rz)

            colors.add(0.92f)
            colors.add(0.82f)
            colors.add(0.55f)
            colors.add(1f)
        }

        drawLines(
            floatBufferOf(positions.toFloatArray()),
            floatBufferOf(colors.toFloatArray()),
            positions.size / 3,
            mvp
        )
    }

    private fun drawBlackHoleSystem() {
        val ringPositions = ArrayList<Float>()
        val ringColors = ArrayList<Float>()

        for (i in 0..180) {
            val t = (i.toFloat() / 180f) * (PI.toFloat() * 2f)
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
            mvp
        )

        drawMeshSphere(0f, 0f, 0f, 0.25f, floatArrayOf(0.08f, 0.08f, 0.12f, 1f))
        drawSinglePoint(0f, 0f, 0f, floatArrayOf(0.35f, 0.45f, 0.95f, 0.55f), 54f, mvp)
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
                val baseAngle = radius * 1.9f + arm * (PI.toFloat() / 2f)
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
            val theta = Random.nextFloat() * PI.toFloat() * 2f
            val phi = Random.nextFloat() * PI.toFloat() * 2f

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
            if (d < 38f && d < bestDist) {
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
                    "\nAtmosphere: " + if (b.atmosphere) "yes" else "no" +
                    "\nRinged: " + if (b.ringed) "yes" else "no" +
                    "\nMoons: " + b.moonCount +
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

    fun pickAt(screenX: Float, screenY: Float) {
        if (mode == 0) pickGalaxyStar(screenX, screenY) else pickLocalBody(screenX, screenY)
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

        systemBodies.add(
            OrbitBody(
                name = "Aurelia",
                radius = 1.3f,
                speed = 0.82f,
                size = 7f,
                color = floatArrayOf(0.4f, 0.9f, 1.0f, 1f),
                tilt = 0.08f,
                kind = "Rocky planet",
                description = "Hot inner rocky world with thin atmosphere",
                atmosphere = false,
                ringed = false,
                moonCount = 0
            )
        )

        systemBodies.add(
            OrbitBody(
                name = "Pelagia",
                radius = 2.3f,
                speed = 0.56f,
                size = 9f,
                color = floatArrayOf(0.5f, 1.0f, 0.6f, 1f),
                tilt = -0.11f,
                kind = "Temperate planet",
                description = "Atmospheric world with ocean-like color balance",
                atmosphere = true,
                ringed = false,
                moonCount = 1
            )
        )

        systemBodies.add(
            OrbitBody(
                name = "Pyra",
                radius = 3.6f,
                speed = 0.39f,
                size = 8f,
                color = floatArrayOf(1.0f, 0.45f, 0.35f, 1f),
                tilt = 0.16f,
                kind = "Volcanic planet",
                description = "Hot fractured crust and unstable surface conditions",
                atmosphere = true,
                ringed = false,
                moonCount = 0
            )
        )

        systemBodies.add(
            OrbitBody(
                name = "Titanis",
                radius = 5.1f,
                speed = 0.28f,
                size = 14f,
                color = floatArrayOf(0.92f, 0.82f, 0.38f, 1f),
                tilt = -0.18f,
                kind = "Gas giant",
                description = "Massive ringed atmosphere-dominated giant",
                atmosphere = true,
                ringed = true,
                moonCount = 2
            )
        )

        systemBodies.add(
            OrbitBody(
                name = "Nyx",
                radius = 6.8f,
                speed = 0.22f,
                size = 10f,
                color = floatArrayOf(0.75f, 0.65f, 1.0f, 1f),
                tilt = 0.22f,
                kind = "Ice giant",
                description = "Cold outer planet with deep haze and icy upper layers",
                atmosphere = true,
                ringed = false,
                moonCount = 1
            )
        )
    }

    private fun localBodyPosition(index: Int, b: OrbitBody): FloatArray {
        val a = angle * b.speed + index * 1.15f
        val px = cos(a.toDouble()).toFloat() * b.radius
        val py = sin((a * 0.35f).toDouble()).toFloat() * b.tilt
        val pz = sin(a.toDouble()).toFloat() * b.radius
        return floatArrayOf(px, py, pz)
    }

    private fun localMoonPosition(
        parentX: Float,
        parentY: Float,
        parentZ: Float,
        bodyIndex: Int,
        moonIndex: Int
    ): FloatArray {
        val moonAngle = angle * (1.35f + moonIndex * 0.22f) + bodyIndex * 0.9f + moonIndex * 1.7f
        val moonRadius = 0.35f + moonIndex * 0.18f

        val mx = parentX + cos(moonAngle.toDouble()).toFloat() * moonRadius
        val my = parentY + sin((moonAngle * 0.5f).toDouble()).toFloat() * (0.04f + moonIndex * 0.01f)
        val mz = parentZ + sin(moonAngle.toDouble()).toFloat() * moonRadius

        return floatArrayOf(mx, my, mz)
    }

    private fun drawPoints(
        positions: FloatBuffer,
        colors: FloatBuffer,
        count: Int,
        pointSize: Float,
        mvpMatrix: FloatArray
    ) {
        GLES20.glUseProgram(pointProgram)

        GLES20.glUniformMatrix4fv(pointMvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, pointSize)
        GLES20.glUniform1f(pointTimeHandle, angle)

        GLES20.glEnableVertexAttribArray(pointPositionHandle)
        GLES20.glVertexAttribPointer(pointPositionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(pointColorHandle)
        GLES20.glVertexAttribPointer(pointColorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(pointPositionHandle)
        GLES20.glDisableVertexAttribArray(pointColorHandle)
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
        GLES20.glUseProgram(pointProgram)

        GLES20.glUniformMatrix4fv(pointMvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, 1f)
        GLES20.glUniform1f(pointTimeHandle, angle)

        GLES20.glEnableVertexAttribArray(pointPositionHandle)
        GLES20.glVertexAttribPointer(pointPositionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(pointColorHandle)
        GLES20.glVertexAttribPointer(pointColorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count)

        GLES20.glDisableVertexAttribArray(pointPositionHandle)
        GLES20.glDisableVertexAttribArray(pointColorHandle)
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
        private const val POINT_VERTEX_SHADER = """
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

        private const val POINT_FRAGMENT_SHADER = """
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

        private const val MESH_VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uModel;
            uniform mat4 uMVP;
            varying vec3 vNormal;

            void main() {
                vNormal = mat3(uModel) * aNormal;
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val MESH_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vNormal;
            uniform vec4 uColor;
            uniform vec3 uLightDir;

            void main() {
                vec3 n = normalize(vNormal);
                float diff = max(0.18, dot(normalize(n), normalize(uLightDir)));
                vec3 col = uColor.rgb * (0.35 + 0.65 * diff);
                gl_FragColor = vec4(col, uColor.a);
            }
        """
    }
}
