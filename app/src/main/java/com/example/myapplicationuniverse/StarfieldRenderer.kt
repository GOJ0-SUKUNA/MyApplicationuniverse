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
        val orbitAu: Float,
        val displayOrbit: Float,
        val periodYears: Float,
        val displayRadius: Float,
        val realRadiusEarths: Float,
        val color: FloatArray,
        val inclination: Float,
        val kind: String,
        val description: String,
        val atmosphere: Boolean,
        val ringed: Boolean,
        val moonCount: Int,
        val style: Float,
        val phase: Float
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

    private var lineProgram = 0
    private var linePositionHandle = 0
    private var lineColorHandle = 0
    private var lineMvpHandle = 0

    private var meshProgram = 0
    private var meshPositionHandle = 0
    private var meshNormalHandle = 0
    private var meshModelHandle = 0
    private var meshMvpHandle = 0
    private var meshColorHandle = 0
    private var meshLightHandle = 0
    private var meshViewPosHandle = 0
    private var meshStyleHandle = 0
    private var meshAtmosphereHandle = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val currentMvp = FloatArray(16)

    private val modelLocal = FloatArray(16)
    private val tempLocal = FloatArray(16)
    private val mvpLocal = FloatArray(16)

    private var widthPx = 1
    private var heightPx = 1

    private var simTimeSeconds = 0f
    private var lastFrameNanos = 0L
    private var timeScale = 1f
    private var paused = false

    private var mode = 0
    private var exoticMode = false

    private val systemBodies = mutableListOf<OrbitBody>()
    private var localStarColor = floatArrayOf(1.0f, 0.9f, 0.5f, 1.0f)
    private var localStarName = "Selected Star"
    private var localStarType = "Yellow star"
    private var localStarTemp = 5800

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.01f, 0.03f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        sphereMesh = SphereMesh()
        buildUniverse(14000)
        buildNebula(1200)

        pointProgram = createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
        pointPositionHandle = GLES20.glGetAttribLocation(pointProgram, "aPosition")
        pointColorHandle = GLES20.glGetAttribLocation(pointProgram, "aColor")
        pointMvpHandle = GLES20.glGetUniformLocation(pointProgram, "uMVP")
        pointSizeHandle = GLES20.glGetUniformLocation(pointProgram, "uPointSize")
        pointTimeHandle = GLES20.glGetUniformLocation(pointProgram, "uTime")

        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        linePositionHandle = GLES20.glGetAttribLocation(lineProgram, "aPosition")
        lineColorHandle = GLES20.glGetAttribLocation(lineProgram, "aColor")
        lineMvpHandle = GLES20.glGetUniformLocation(lineProgram, "uMVP")

        meshProgram = createProgram(MESH_VERTEX_SHADER, MESH_FRAGMENT_SHADER)
        meshPositionHandle = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        meshNormalHandle = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        meshModelHandle = GLES20.glGetUniformLocation(meshProgram, "uModel")
        meshMvpHandle = GLES20.glGetUniformLocation(meshProgram, "uMVP")
        meshColorHandle = GLES20.glGetUniformLocation(meshProgram, "uBaseColor")
        meshLightHandle = GLES20.glGetUniformLocation(meshProgram, "uLightDir")
        meshViewPosHandle = GLES20.glGetUniformLocation(meshProgram, "uViewPos")
        meshStyleHandle = GLES20.glGetUniformLocation(meshProgram, "uStyle")
        meshAtmosphereHandle = GLES20.glGetUniformLocation(meshProgram, "uAtmosphere")

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
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = (((now - lastFrameNanos).toDouble() / 1_000_000_000.0).coerceIn(0.0, 0.05)).toFloat()
        lastFrameNanos = now

        if (!paused) {
            simTimeSeconds += dt * timeScale
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

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
            drawGalaxyMode()
        } else {
            drawLocalMode(floatArrayOf(camX, camY, camZ))
        }
    }

    private fun drawGalaxyMode() {
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, simTimeSeconds * 6f, 0f, 1f, 0f)
        Matrix.multiplyMM(temp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
        System.arraycopy(mvp, 0, currentMvp, 0, 16)

        drawPoints(nebulaBuffer, nebulaColorBuffer, nebulaCount, 14f, mvp)
        drawPoints(starBuffer, colorBuffer, starCount, 4.0f, mvp)

        if (selectedStarIndex in stars.indices) {
            val s = stars[selectedStarIndex]
            drawSinglePoint(s.x, s.y, s.z, floatArrayOf(1f, 1f, 1f, 1f), 16f, mvp)
            drawSinglePoint(s.x, s.y, s.z, floatArrayOf(1f, 1f, 1f, 0.35f), 26f, mvp)
        }
    }

    private fun drawLocalMode(cam: FloatArray) {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(temp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0)
        System.arraycopy(mvp, 0, currentMvp, 0, 16)

        if (!exoticMode) {
            drawStarVisual(0f, 0f, 0f, localStarColor, cam)
        } else {
            drawBlackHoleSystem(cam)
        }

        if (selectedLocalIndex == -1) {
            drawSelectionHalo(0f, 0f, 0f, 0.72f)
        }

        val orbitLinePositions = ArrayList<Float>()
        val orbitLineColors = ArrayList<Float>()

        for ((i, body) in systemBodies.withIndex()) {
            val pos = localBodyPosition(i, body)
            drawOrbitBody(pos[0], pos[1], pos[2], body, selectedLocalIndex == i, cam)

            repeat(body.moonCount) { mi ->
                val moon = localMoonPosition(pos[0], pos[1], pos[2], i, mi)
                drawMoonVisual(moon[0], moon[1], moon[2], cam)
            }

            val segments = 72
            for (s in 0..segments) {
                val t = (s.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
                orbitLinePositions.add(cos(t.toDouble()).toFloat() * body.displayOrbit)
                orbitLinePositions.add(sin((t * 0.35f).toDouble()).toFloat() * body.inclination)
                orbitLinePositions.add(sin(t.toDouble()).toFloat() * body.displayOrbit)

                orbitLineColors.add(0.18f)
                orbitLineColors.add(0.22f)
                orbitLineColors.add(0.30f)
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

    private fun drawStarVisual(x: Float, y: Float, z: Float, color: FloatArray, cam: FloatArray) {
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.16f), 95f, mvp)
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.30f), 58f, mvp)
        drawSinglePoint(x, y, z, floatArrayOf(color[0], color[1], color[2], 0.58f), 30f, mvp)
        drawMeshSphere(x, y, z, 0.42f, floatArrayOf(color[0], color[1], color[2], 1f), 8f, false, cam)
    }

    private fun drawMoonVisual(x: Float, y: Float, z: Float, cam: FloatArray) {
        drawSinglePoint(x, y, z, floatArrayOf(0.82f, 0.85f, 0.9f, 0.10f), 8f, mvp)
        drawMeshSphere(x, y, z, 0.10f, floatArrayOf(0.82f, 0.85f, 0.9f, 1f), 7f, false, cam)
    }

    private fun drawOrbitBody(
        x: Float,
        y: Float,
        z: Float,
        body: OrbitBody,
        selected: Boolean,
        cam: FloatArray
    ) {
        if (body.atmosphere) {
            drawMeshSphere(
                x, y, z,
                body.displayRadius * 1.16f,
                floatArrayOf(body.color[0], body.color[1], body.color[2], 0.22f),
                body.style,
                true,
                cam
            )
        }

        drawMeshSphere(
            x, y, z,
            body.displayRadius,
            floatArrayOf(body.color[0], body.color[1], body.color[2], 1f),
            body.style,
            false,
            cam
        )

        if (body.ringed) {
            drawPlanetRing(x, y, z, body.displayRadius * 1.9f)
            drawPlanetRing(x, y, z, body.displayRadius * 2.2f)
        }

        if (selected) {
            drawSelectionHalo(x, y, z, body.displayRadius * 2.2f)
        }
    }

    private fun drawMeshSphere(
        x: Float,
        y: Float,
        z: Float,
        radius: Float,
        color: FloatArray,
        style: Float,
        atmosphere: Boolean,
        cam: FloatArray
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
        GLES20.glUniform3f(meshLightHandle, 0.45f, 0.85f, 0.6f)
        GLES20.glUniform3f(meshViewPosHandle, cam[0], cam[1], cam[2])
        GLES20.glUniform1f(meshStyleHandle, style)
        GLES20.glUniform1f(meshAtmosphereHandle, if (atmosphere) 1f else 0f)

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

        drawLines(floatBufferOf(positions.toFloatArray()), floatBufferOf(colors.toFloatArray()), positions.size / 3, mvp)
    }

    private fun drawPlanetRing(x: Float, y: Float, z: Float, radius: Float) {
        val positions = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val segments = 90

        for (i in 0..segments) {
            val t = (i.toFloat() / segments.toFloat()) * (PI.toFloat() * 2f)
            positions.add(x + cos(t.toDouble()).toFloat() * radius)
            positions.add(y + sin((t * 0.55f).toDouble()).toFloat() * 0.035f)
            positions.add(z + sin(t.toDouble()).toFloat() * radius)

            colors.add(0.92f)
            colors.add(0.82f)
            colors.add(0.58f)
            colors.add(0.95f)
        }

        drawLines(floatBufferOf(positions.toFloatArray()), floatBufferOf(colors.toFloatArray()), positions.size / 3, mvp)
    }

    private fun drawBlackHoleSystem(cam: FloatArray) {
        val ringPositions = ArrayList<Float>()
        val ringColors = ArrayList<Float>()

        for (i in 0..180) {
            val t = (i.toFloat() / 180f) * (PI.toFloat() * 2f)
            val r = 0.55f + 0.08f * sin((simTimeSeconds * 4f + t * 3f).toDouble()).toFloat()

            ringPositions.add(cos(t.toDouble()).toFloat() * r)
            ringPositions.add(sin((t * 0.6f).toDouble()).toFloat() * 0.09f)
            ringPositions.add(sin(t.toDouble()).toFloat() * r)

            ringColors.add(1.0f)
            ringColors.add(0.6f + 0.2f * sin((t * 2f).toDouble()).toFloat())
            ringColors.add(0.18f)
            ringColors.add(1.0f)
        }

        drawLines(floatBufferOf(ringPositions.toFloatArray()), floatBufferOf(ringColors.toFloatArray()), ringPositions.size / 3, mvp)
        drawMeshSphere(0f, 0f, 0f, 0.25f, floatArrayOf(0.08f, 0.08f, 0.12f, 1f), 9f, false, cam)
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

            positions[i * 3] = cos(theta.toDouble()).toFloat() * radius + base[0]
            positions[i * 3 + 1] = sin(phi.toDouble()).toFloat() * 0.8f * radius + base[1]
            positions[i * 3 + 2] = sin(theta.toDouble()).toFloat() * radius + base[2]

            val c = if (Random.nextFloat() < 0.5f) {
                floatArrayOf(0.25f, 0.35f, 0.8f, 0.18f)
            } else {
                floatArrayOf(0.8f, 0.28f, 0.65f, 0.16f)
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

    private fun buildSystemForSelectedStar() {
        systemBodies.clear()

        systemBodies.add(OrbitBody("Mercury", 0.39f, 0.85f, 0.24f, 0.09f, 0.38f, floatArrayOf(0.72f, 0.70f, 0.66f, 1f), 0.02f, "Rocky planet", "Small dense inner planet with cratered terrain.", false, false, 0, 0f, 0.3f))
        systemBodies.add(OrbitBody("Venus", 0.72f, 1.15f, 0.62f, 0.14f, 0.95f, floatArrayOf(0.92f, 0.82f, 0.62f, 1f), -0.03f, "Cloud planet", "Dense cloud-shrouded world with a crushing atmosphere.", true, false, 0, 1f, 1.1f))
        systemBodies.add(OrbitBody("Earth", 1.0f, 1.55f, 1.0f, 0.15f, 1.0f, floatArrayOf(0.22f, 0.48f, 0.88f, 1f), 0.04f, "Temperate planet", "Ocean-land world with a nitrogen-oxygen atmosphere.", true, false, 1, 2f, 2.0f))
        systemBodies.add(OrbitBody("Mars", 1.52f, 1.95f, 1.88f, 0.11f, 0.53f, floatArrayOf(0.84f, 0.34f, 0.22f, 1f), 0.05f, "Desert planet", "Cold rocky world with dust, iron oxides, and thin air.", true, false, 2, 3f, 2.6f))
        systemBodies.add(OrbitBody("Jupiter", 5.20f, 2.95f, 11.86f, 0.33f, 11.2f, floatArrayOf(0.86f, 0.70f, 0.46f, 1f), -0.08f, "Gas giant", "Massive hydrogen-helium giant with strong banding and storms.", true, false, 4, 4f, 3.5f))
        systemBodies.add(OrbitBody("Saturn", 9.58f, 3.85f, 29.45f, 0.29f, 9.45f, floatArrayOf(0.92f, 0.82f, 0.52f, 1f), -0.11f, "Ringed gas giant", "Low-density gas giant with a broad ring system.", true, true, 3, 5f, 4.2f))
        systemBodies.add(OrbitBody("Uranus", 19.2f, 4.70f, 84.0f, 0.22f, 4.0f, floatArrayOf(0.58f, 0.92f, 0.94f, 1f), 0.13f, "Ice giant", "Methane-rich ice giant with pale cyan haze.", true, false, 2, 6f, 5.0f))
        systemBodies.add(OrbitBody("Neptune", 30.1f, 5.45f, 164.8f, 0.22f, 3.88f, floatArrayOf(0.24f, 0.46f, 0.96f, 1f), 0.15f, "Ice giant", "Cold deep-blue giant with strong winds and methane absorption.", true, false, 1, 7f, 5.8f))
    }

    private fun localBodyPosition(index: Int, body: OrbitBody): FloatArray {
        val omega = 0.30f / sqrt(body.orbitAu * body.orbitAu * body.orbitAu)
        val a = simTimeSeconds * omega + body.phase
        return floatArrayOf(
            cos(a.toDouble()).toFloat() * body.displayOrbit,
            sin((a * 0.45f).toDouble()).toFloat() * body.inclination,
            sin(a.toDouble()).toFloat() * body.displayOrbit
        )
    }

    private fun localMoonPosition(parentX: Float, parentY: Float, parentZ: Float, bodyIndex: Int, moonIndex: Int): FloatArray {
        val moonAngle = simTimeSeconds * (1.0f + moonIndex * 0.35f) + bodyIndex * 0.8f + moonIndex * 1.6f
        val moonRadius = 0.22f + moonIndex * 0.14f
        return floatArrayOf(
            parentX + cos(moonAngle.toDouble()).toFloat() * moonRadius,
            parentY + sin((moonAngle * 0.5f).toDouble()).toFloat() * (0.03f + moonIndex * 0.01f),
            parentZ + sin(moonAngle.toDouble()).toFloat() * moonRadius
        )
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

            val sx = (out[0] / out[3] * 0.5f + 0.5f) * widthPx
            val sy = (1f - (out[1] / out[3] * 0.5f + 0.5f)) * heightPx

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

            statusCallback?.invoke("Selected ${s.name} | ${s.kind} | double tap local mode | long press exotic mode")
            infoCallback?.invoke(
                "Name: ${s.name}\nType: ${s.kind}\nTemperature: ${s.tempKelvin} K\nGalaxy cluster: ${s.galaxyId}\nPosition: (" +
                    "${String.format("%.2f", s.x)}, ${String.format("%.2f", s.y)}, ${String.format("%.2f", s.z)})"
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

        for ((i, body) in systemBodies.withIndex()) {
            val p = localBodyPosition(i, body)
            val s = projectToScreen(p[0], p[1], p[2])
            val dx = screenX - s[0]
            val dy = screenY - s[1]
            val d = sqrt(dx * dx + dy * dy)
            if (d < 40f && d < bestDist) {
                best = i
                bestDist = d
            }
        }

        selectedLocalIndex = best

        if (best == -1) {
            statusCallback?.invoke("Selected local star")
            infoCallback?.invoke(
                "Name: $localStarName\nType: " +
                    (if (exoticMode) "Exotic singularity mode" else localStarType) +
                    "\nTemperature: $localStarTemp K\nLocal preset: Solar System style\nMode: " +
                    (if (exoticMode) "Accretion disk / black-hole style" else "Kepler-scaled visual system")
            )
        } else if (best in systemBodies.indices) {
            val b = systemBodies[best]
            statusCallback?.invoke("Selected ${b.name} | ${b.kind}")
            infoCallback?.invoke(
                "Name: ${b.name}\nKind: ${b.kind}\nSemi-major axis: ${String.format("%.2f", b.orbitAu)} AU\n" +
                    "Orbital period: ${String.format("%.2f", b.periodYears)} years\n" +
                    "Radius: ${String.format("%.2f", b.realRadiusEarths)} Earth radii\n" +
                    "Approx orbital speed: ${String.format("%.2f", orbitalSpeedKmS(b))} km/s\n" +
                    "Atmosphere: ${if (b.atmosphere) "yes" else "no"}\n" +
                    "Ringed: ${if (b.ringed) "yes" else "no"}\n" +
                    "Moons shown: ${b.moonCount}\nDescription: ${b.description}"
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

        val sx = (out[0] / out[3] * 0.5f + 0.5f) * widthPx
        val sy = (1f - (out[1] / out[3] * 0.5f + 0.5f)) * heightPx
        return floatArrayOf(sx, sy)
    }

    fun pickAt(screenX: Float, screenY: Float) {
        if (mode == 0) pickGalaxyStar(screenX, screenY) else pickLocalBody(screenX, screenY)
    }

    fun toggleSystemMode() {
        if (mode == 0) {
            if (selectedStarIndex !in stars.indices) {
                statusCallback?.invoke("Tap a star first, then double tap")
                return
            }

            buildSystemForSelectedStar()
            mode = 1
            selectedLocalIndex = -1
            cameraDistance = 7.0f
            yaw = 0.35f
            pitch = 0.18f

            infoCallback?.invoke(
                "Name: $localStarName\nType: " +
                    (if (exoticMode) "Exotic singularity mode" else localStarType) +
                    "\nTemperature: $localStarTemp K\nLocal preset: Solar System style\nMode: " +
                    (if (exoticMode) "Accretion disk / black-hole style" else "Kepler-scaled visual system")
            )

            emitStatus(
                if (exoticMode) "Entered local exotic mode"
                else "Entered local solar mode"
            )
        } else {
            mode = 0
            selectedLocalIndex = -2
            cameraDistance = 12f
            emitStatus("Returned to galaxy mode")
        }
    }

    fun toggleExoticMode() {
        exoticMode = !exoticMode
        emitStatus(if (exoticMode) "Exotic mode ON" else "Exotic mode OFF")
    }

    fun slowDownTime() {
        timeScale = (timeScale / 2f).coerceAtLeast(0.125f)
        emitStatus("Time slower")
    }

    fun speedUpTime() {
        timeScale = (timeScale * 2f).coerceAtMost(64f)
        emitStatus("Time faster")
    }

    fun togglePause() {
        paused = !paused
        emitStatus(if (paused) "Paused" else "Resumed")
    }

    fun resetView() {
        if (mode == 0) {
            yaw = 0.35f
            pitch = 0.2f
            cameraDistance = 12f
        } else {
            yaw = 0.35f
            pitch = 0.18f
            cameraDistance = 7f
        }
        emitStatus("View reset")
    }

    fun emitStatus(prefix: String) {
        val modeText = when {
            mode == 0 -> "Galaxy"
            exoticMode -> "Local exotic"
            else -> "Local solar"
        }
        val stateText = if (paused) "Paused" else "Running"
        statusCallback?.invoke("$prefix | $stateText | Time x${String.format("%.3f", timeScale)} | $modeText")
    }

    private fun orbitalSpeedKmS(body: OrbitBody): Float {
        return 29.78f / sqrt(body.orbitAu)
    }

    private fun drawPoints(positions: FloatBuffer, colors: FloatBuffer, count: Int, pointSize: Float, mvpMatrix: FloatArray) {
        GLES20.glUseProgram(pointProgram)

        GLES20.glUniformMatrix4fv(pointMvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, pointSize)
        GLES20.glUniform1f(pointTimeHandle, simTimeSeconds)

        GLES20.glEnableVertexAttribArray(pointPositionHandle)
        GLES20.glVertexAttribPointer(pointPositionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(pointColorHandle)
        GLES20.glVertexAttribPointer(pointColorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        GLES20.glDisableVertexAttribArray(pointPositionHandle)
        GLES20.glDisableVertexAttribArray(pointColorHandle)
    }

    private fun drawSinglePoint(x: Float, y: Float, z: Float, color: FloatArray, pointSize: Float, mvpMatrix: FloatArray) {
        drawPoints(
            floatBufferOf(floatArrayOf(x, y, z)),
            floatBufferOf(floatArrayOf(color[0], color[1], color[2], color[3])),
            1,
            pointSize,
            mvpMatrix
        )
    }

    private fun drawLines(positions: FloatBuffer, colors: FloatBuffer, count: Int, mvpMatrix: FloatArray) {
        GLES20.glUseProgram(lineProgram)

        GLES20.glUniformMatrix4fv(lineMvpHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(linePositionHandle)
        GLES20.glVertexAttribPointer(linePositionHandle, 3, GLES20.GL_FLOAT, false, 0, positions)

        GLES20.glEnableVertexAttribArray(lineColorHandle)
        GLES20.glVertexAttribPointer(lineColorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count)

        GLES20.glDisableVertexAttribArray(linePositionHandle)
        GLES20.glDisableVertexAttribArray(lineColorHandle)
    }

    


    private fun floatBufferOf(values: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
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
                float shimmer = 0.95 + 0.05 * sin(uTime * 8.0 + d * 18.0);
                gl_FragColor = vec4(vColor.rgb * (0.55 + glow * 1.45) * shimmer, glow * vColor.a);
            }
        """

        private const val LINE_VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec4 aColor;
            uniform mat4 uMVP;
            varying vec4 vColor;

            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                vColor = aColor;
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;

            void main() {
                gl_FragColor = vColor;
            }
        """

        private const val MESH_VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uModel;
            uniform mat4 uMVP;
            varying vec3 vLocalPos;
            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;

            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vLocalPos = aPosition;
                vWorldPos = world.xyz;
                vWorldNormal = normalize(mat3(uModel) * aNormal);
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val MESH_FRAGMENT_SHADER = """
            precision mediump float;

            varying vec3 vLocalPos;
            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;

            uniform vec4 uBaseColor;
            uniform vec3 uLightDir;
            uniform vec3 uViewPos;
            uniform float uStyle;
            uniform float uAtmosphere;

            float fhash(vec3 p) {
                return sin(p.x * 13.0) + sin(p.y * 17.0) + sin(p.z * 19.0);
            }

            void main() {
                vec3 n = normalize(vWorldNormal);
                vec3 l = normalize(uLightDir);
                vec3 v = normalize(uViewPos - vWorldPos);

                float diff = max(dot(n, l), 0.0);
                float rim = pow(1.0 - max(dot(n, v), 0.0), 2.2);
                float spec = pow(max(dot(reflect(-l, n), v), 0.0), 24.0);

                vec3 base = uBaseColor.rgb;

                if (uAtmosphere > 0.5) {
                    float a = rim * uBaseColor.a;
                    vec3 col = mix(uBaseColor.rgb * 0.65, vec3(1.0), rim * 0.35);
                    gl_FragColor = vec4(col, a);
                    return;
                }

                if (uStyle < 0.5) {
                    float rock = 0.5 + 0.5 * fhash(vLocalPos) * 0.25;
                    base *= 0.78 + rock * 0.25;
                } else if (uStyle < 1.5) {
                    float bands = 0.5 + 0.5 * sin(vLocalPos.y * 18.0 + sin(vLocalPos.x * 6.0));
                    base = mix(uBaseColor.rgb * 0.75, vec3(0.97, 0.92, 0.80), bands * 0.55);
                } else if (uStyle < 2.5) {
                    float continents = sin(vLocalPos.x * 8.0) + sin(vLocalPos.y * 10.0) + sin(vLocalPos.z * 12.0);
                    vec3 ocean = vec3(0.08, 0.26, 0.70);
                    vec3 land = vec3(0.18, 0.55, 0.22);
                    base = mix(ocean, land, smoothstep(-0.15, 0.65, continents));
                    float ice = smoothstep(0.75, 0.92, abs(vLocalPos.y));
                    base = mix(base, vec3(0.96, 0.97, 1.0), ice * 0.90);
                    float clouds = smoothstep(0.82, 1.12, sin(vLocalPos.x * 20.0) * sin(vLocalPos.z * 16.0) + sin(vLocalPos.y * 13.0));
                    base = mix(base, vec3(1.0), clouds * 0.22);
                } else if (uStyle < 3.5) {
                    float crack = abs(sin(vLocalPos.x * 24.0) * sin(vLocalPos.z * 20.0));
                    base = mix(vec3(0.36, 0.12, 0.08), vec3(0.92, 0.33, 0.11), smoothstep(0.88, 0.98, crack));
                } else if (uStyle < 4.5) {
                    float band = 0.5 + 0.5 * sin(vLocalPos.y * 18.0);
                    base = mix(vec3(0.76, 0.57, 0.34), vec3(0.95, 0.82, 0.62), band);
                    float spot = smoothstep(0.78, 0.90, 1.0 - length(vLocalPos - vec3(0.55, -0.12, 0.22)) * 1.8);
                    base = mix(base, vec3(0.72, 0.30, 0.18), spot);
                } else if (uStyle < 5.5) {
                    float band = 0.5 + 0.5 * sin(vLocalPos.y * 14.0);
                    base = mix(vec3(0.70, 0.58, 0.30), vec3(0.93, 0.83, 0.58), band);
                } else if (uStyle < 6.5) {
                    float band = 0.5 + 0.5 * sin(vLocalPos.y * 10.0 + vLocalPos.z * 4.0);
                    base = mix(vec3(0.45, 0.82, 0.85), vec3(0.72, 0.95, 0.95), band);
                } else {
                    float band = 0.5 + 0.5 * sin(vLocalPos.y * 12.0);
                    base = mix(vec3(0.20, 0.36, 0.78), vec3(0.46, 0.62, 0.98), band);
                }

                vec3 col = base * (0.18 + 0.82 * diff) + rim * 0.10 + spec * 0.18;
                gl_FragColor = vec4(col, uBaseColor.a);
            }
        """
    }
}
