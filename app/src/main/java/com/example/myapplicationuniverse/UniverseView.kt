package com.example.myapplicationuniverse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class UniverseView(context: Context) : View(context) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 28f
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 15, 18, 24)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
    }

    private val minusButton = RectF(24f, 24f, 108f, 88f)
    private val plusButton = RectF(120f, 24f, 204f, 88f)
    private val modeButton = RectF(216f, 24f, 360f, 88f)
    private val backButton = RectF(372f, 24f, 500f, 88f)

    private val sun = Body(
        name = "Sun",
        kind = "Star",
        x = 0.0,
        y = 0.0,
        vx = 0.0,
        vy = 0.0,
        mass = 150000.0,
        radius = 24f,
        color = Color.YELLOW
    )

    private val earth = Body(
        name = "Earth",
        kind = "Planet",
        x = 240.0,
        y = 0.0,
        vx = 0.0,
        vy = 330.0,
        mass = 400.0,
        radius = 9f,
        color = Color.CYAN
    )

    private val verd = Body(
        name = "Verd",
        kind = "Planet",
        x = 410.0,
        y = 0.0,
        vx = 0.0,
        vy = 245.0,
        mass = 260.0,
        radius = 11f,
        color = Color.GREEN
    )

    private val crimson = Body(
        name = "Crimson",
        kind = "Planet",
        x = 600.0,
        y = 0.0,
        vx = 0.0,
        vy = 205.0,
        mass = 180.0,
        radius = 12f,
        color = Color.RED
    )

    private val moon = Body(
        name = "Moon",
        kind = "Moon",
        x = earth.x + 42.0,
        y = earth.y,
        vx = 0.0,
        vy = earth.vy + 520.0,
        mass = 20.0,
        radius = 5f,
        color = Color.LTGRAY
    )

    private val planets = mutableListOf(earth, verd, crimson)
    private val moons = mutableListOf(moon)
    private val liveSatellites = mutableListOf<Body>()
    private val liveNeos = mutableListOf<Body>()
    private val localBodies = mutableListOf<Body>()

    private data class BgStar(val x: Float, val y: Float, val r: Float, val a: Int)
    private val backgroundStars = MutableList(220) {
        BgStar(
            x = Random(42 + it).nextFloat(),
            y = Random(420 + it).nextFloat(),
            r = 0.8f + Random(4200 + it).nextFloat() * 2.2f,
            a = 80 + Random(7 + it).nextInt(120)
        )
    }

    private var lastTimeNanos = 0L
    private val gSun = 200.0
    private val gEarth = 120.0
    private val gMoon = 70.0
    private val softening = 100.0

    private var paused = false
    private var timeScale = 1.0
    private var scaleFactor = 1.0f

    private var offsetX = 0f
    private var offsetY = 0f
    private var cameraTargetOffsetX = 0f
    private var cameraTargetOffsetY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private var selectedBody: Body? = null
    private var detailMode = false

    private var liveStatus = "Fetching live data..."
    private var lastRefreshLabel = "Never"
    private var lastRefreshMs = 0L
    private val refreshIntervalMs = 30L * 60L * 1000L
    private var loading = false

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.18f, 8.0f)
            invalidate()
            return true
        }
    })

    init {
        refreshLiveData()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val nowMs = System.currentTimeMillis()
        if (!loading && nowMs - lastRefreshMs > refreshIntervalMs) {
            refreshLiveData()
        }

        val now = System.nanoTime()
        if (lastTimeNanos == 0L) lastTimeNanos = now
        val dtRaw = ((now - lastTimeNanos) / 1_000_000_000.0).coerceIn(0.0, 0.033)
        lastTimeNanos = now
        val dt = dtRaw * timeScale

        if (!paused) {
            if (detailMode) {
                updateLocalPhysics(dt)
            } else {
                updatePhysics(dt)
            }
        }

        updateCameraFocus()

        canvas.drawColor(Color.BLACK)
        drawBackground(canvas)

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        if (detailMode) {
            drawDetailScene(canvas, cx, cy)
        } else {
            drawMainScene(canvas, cx, cy)
        }

        drawHud(canvas)
        postInvalidateOnAnimation()
    }

    private fun drawMainScene(canvas: Canvas, cx: Float, cy: Float) {
        drawBody(canvas, sun, cx, cy)
        for (planet in planets) {
            drawTrail(canvas, planet, cx, cy, Color.argb(110, 90, 90, 110))
            drawBody(canvas, planet, cx, cy)
        }
        for (m in moons) {
            drawTrail(canvas, m, cx, cy, Color.argb(100, 110, 110, 110))
            drawBody(canvas, m, cx, cy)
        }
        for (sat in liveSatellites) {
            drawTrail(canvas, sat, cx, cy, Color.argb(65, 180, 180, 180))
            drawBody(canvas, sat, cx, cy)
        }
        for (neo in liveNeos) {
            drawTrail(canvas, neo, cx, cy, Color.argb(70, 120, 120, 120))
            drawBody(canvas, neo, cx, cy)
        }
    }

    private fun drawDetailScene(canvas: Canvas, cx: Float, cy: Float) {
        val focus = selectedBody ?: return
        drawBody(canvas, focus.copy(x = 0.0, y = 0.0), cx, cy)
        for (b in localBodies) {
            drawTrail(canvas, b, cx, cy, Color.argb(100, 120, 120, 130))
            drawBody(canvas, b, cx, cy)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val px = offsetX * 0.08f
        val py = offsetY * 0.08f
        for (s in backgroundStars) {
            bodyPaint.color = Color.argb(s.a, 255, 255, 255)
            val x = (s.x * width + px) % width
            val y = (s.y * height + py) % height
            canvas.drawCircle(if (x < 0) x + width else x, if (y < 0) y + height else y, s.r, bodyPaint)
        }
    }

    private fun updateCameraFocus() {
        val target = selectedBody
        if (detailMode) {
            cameraTargetOffsetX = 0f
            cameraTargetOffsetY = 0f
        } else if (target != null) {
            cameraTargetOffsetX = -target.x.toFloat() * scaleFactor
            cameraTargetOffsetY = -target.y.toFloat() * scaleFactor
        }

        offsetX += (cameraTargetOffsetX - offsetX) * 0.08f
        offsetY += (cameraTargetOffsetY - offsetY) * 0.08f
    }

    private fun refreshLiveData() {
        loading = true
        liveStatus = "Refreshing..."

        Thread {
            val sats = LiveAstronomyRepository.fetchCelesTrakStations(earth.x, earth.y)
            val neos = LiveAstronomyRepository.fetchTodayNeoBodies(sun.x, sun.y)

            post {
                liveSatellites.clear()
                liveSatellites.addAll(sats)

                liveNeos.clear()
                liveNeos.addAll(neos)

                loading = false
                lastRefreshMs = System.currentTimeMillis()
                lastRefreshLabel = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(lastRefreshMs))

                liveStatus = if (sats.isEmpty() && neos.isEmpty()) "No live data loaded" else "Live feeds active"
                invalidate()
            }
        }.start()
    }

    private fun updatePhysics(dt: Double) {
        for (planet in planets) {
            updateOrbiter(planet, sun, dt, gSun, 520)
        }

        val dx = earth.x - moon.x
        val dy = earth.y - moon.y
        val distSq = dx * dx + dy * dy + softening
        val dist = sqrt(distSq)
        val accel = gMoon * earth.mass / distSq
        moon.vx += accel * dx / dist * dt
        moon.vy += accel * dy / dist * dt
        moon.x += moon.vx * dt
        moon.y += moon.vy * dt
        moon.trail.add(moon.x to moon.y)
        if (moon.trail.size > 260) moon.trail.removeAt(0)

        for (sat in liveSatellites) {
            updateOrbiter(sat, earth, dt, gEarth, 150)
        }

        for (neo in liveNeos) {
            updateOrbiter(neo, sun, dt, gSun * 0.75, 190)
        }
    }

    private fun updateLocalPhysics(dt: Double) {
        val center = selectedBody ?: return
        for (body in localBodies) {
            val dx = -body.x
            val dy = -body.y
            val distSq = dx * dx + dy * dy + 12.0
            val dist = sqrt(distSq)
            val accel = 260.0 * center.mass / distSq

            body.vx += accel * dx / dist * dt
            body.vy += accel * dy / dist * dt

            body.x += body.vx * dt
            body.y += body.vy * dt

            body.trail.add(body.x to body.y)
            if (body.trail.size > 240) body.trail.removeAt(0)
        }
    }

    private fun updateOrbiter(body: Body, parent: Body, dt: Double, g: Double, trailLimit: Int) {
        val dx = parent.x - body.x
        val dy = parent.y - body.y

        val distSq = dx * dx + dy * dy + softening
        val dist = sqrt(distSq)

        val accel = g * parent.mass / distSq
        val ax = accel * dx / dist
        val ay = accel * dy / dist

        body.vx += ax * dt
        body.vy += ay * dt

        body.x += body.vx * dt
        body.y += body.vy * dt

        body.trail.add(body.x to body.y)
        if (body.trail.size > trailLimit) {
            body.trail.removeAt(0)
        }
    }

    private fun drawBody(canvas: Canvas, body: Body, cx: Float, cy: Float) {
        val sx = cx + body.x.toFloat() * scaleFactor
        val sy = cy + body.y.toFloat() * scaleFactor
        val rr = (body.radius * scaleFactor).coerceAtLeast(2.4f)

        when (body.kind) {
            "Star" -> {
                bodyPaint.color = Color.argb(40, 255, 220, 120)
                canvas.drawCircle(sx, sy, rr * 3.2f, bodyPaint)
                bodyPaint.color = Color.argb(80, 255, 210, 90)
                canvas.drawCircle(sx, sy, rr * 2.1f, bodyPaint)
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)
            }

            "Planet" -> {
                bodyPaint.color = Color.argb(60, Color.red(body.color), Color.green(body.color), Color.blue(body.color))
                canvas.drawCircle(sx, sy, rr * 1.5f, bodyPaint)
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)

                bodyPaint.color = Color.argb(70, 180, 220, 255)
                canvas.drawCircle(sx, sy, rr * 1.2f, bodyPaint)
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)
            }

            "Moon" -> {
                bodyPaint.color = Color.DKGRAY
                canvas.drawCircle(sx + rr * 0.15f, sy + rr * 0.1f, rr, bodyPaint)
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)
            }

            "Asteroid" -> {
                bodyPaint.color = Color.DKGRAY
                canvas.drawCircle(sx - rr * 0.15f, sy + rr * 0.1f, rr * 1.05f, bodyPaint)
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)
            }

            "Satellite" -> {
                bodyPaint.color = body.color
                canvas.drawRect(sx - rr, sy - rr, sx + rr, sy + rr, bodyPaint)
                linePaint.color = Color.GRAY
                canvas.drawLine(sx - rr * 2.2f, sy, sx + rr * 2.2f, sy, linePaint)
                canvas.drawLine(sx, sy - rr * 2.2f, sx, sy + rr * 2.2f, linePaint)
            }

            else -> {
                bodyPaint.color = body.color
                canvas.drawCircle(sx, sy, rr, bodyPaint)
            }
        }

        if (selectedBody?.name == body.name && selectedBody?.kind == body.kind) {
            bodyPaint.style = Paint.Style.STROKE
            bodyPaint.strokeWidth = 4f
            bodyPaint.color = Color.WHITE
            canvas.drawCircle(sx, sy, rr + 12f, bodyPaint)
            bodyPaint.style = Paint.Style.FILL
        }
    }

    private fun drawTrail(canvas: Canvas, body: Body, cx: Float, cy: Float, color: Int) {
        linePaint.color = color
        linePaint.strokeWidth = 2f

        for (i in 1 until body.trail.size) {
            val p1 = body.trail[i - 1]
            val p2 = body.trail[i]
            canvas.drawLine(
                cx + p1.first.toFloat() * scaleFactor,
                cy + p1.second.toFloat() * scaleFactor,
                cx + p2.first.toFloat() * scaleFactor,
                cy + p2.second.toFloat() * scaleFactor,
                linePaint
            )
        }
    }

    private fun drawHud(canvas: Canvas) {
        canvas.drawRoundRect(18f, 18f, width - 18f, 435f, 18f, 18f, hudPaint)

        bodyPaint.color = Color.argb(180, 40, 40, 50)
        canvas.drawRoundRect(minusButton, 12f, 12f, bodyPaint)
        canvas.drawRoundRect(plusButton, 12f, 12f, bodyPaint)
        canvas.drawRoundRect(modeButton, 12f, 12f, bodyPaint)
        canvas.drawRoundRect(backButton, 12f, 12f, bodyPaint)

        canvas.drawText("-", 57f, 69f, textPaint)
        canvas.drawText("+", 151f, 69f, textPaint)
        canvas.drawText("MODE", 236f, 69f, smallTextPaint)
        canvas.drawText("BACK", 392f, 69f, smallTextPaint)

        canvas.drawText("Time x" + String.format("%.2f", timeScale), 520f, 69f, textPaint)
        canvas.drawText("Pinch zoom | Drag pan | Tap body to select/focus", 34f, 122f, smallTextPaint)
        canvas.drawText("MODE enters local system view for selected body", 34f, 162f, smallTextPaint)
        canvas.drawText("BACK exits local mode and clears focus if needed", 34f, 202f, smallTextPaint)
        canvas.drawText("Scale: " + String.format("%.2f", scaleFactor), 34f, 242f, smallTextPaint)
        canvas.drawText("Status: " + if (paused) "Paused" else "Running", 34f, 282f, smallTextPaint)
        canvas.drawText("View: " + if (detailMode) "Detail mode" else "System mode", 34f, 322f, smallTextPaint)

        val s = selectedBody
        if (s == null) {
            canvas.drawText("Selected: none", 34f, 362f, smallTextPaint)
        } else {
            canvas.drawText("Selected: " + s.name + " | " + s.kind, 34f, 362f, smallTextPaint)
            canvas.drawText("Mass: " + String.format("%.1f", s.mass) + " | Radius: " + String.format("%.1f", s.radius), 34f, 402f, smallTextPaint)
        }
    }

    private fun allBodies(): List<Body> {
        val out = mutableListOf<Body>()
        out.add(sun)
        out.addAll(planets)
        out.addAll(moons)
        out.addAll(liveSatellites)
        out.addAll(liveNeos)
        return out
    }

    private fun trySelectAt(screenX: Float, screenY: Float): Boolean {
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        var best: Body? = null
        var bestDist = Float.MAX_VALUE

        for (b in allBodies()) {
            val sx = cx + b.x.toFloat() * scaleFactor
            val sy = cy + b.y.toFloat() * scaleFactor
            val dx = screenX - sx
            val dy = screenY - sy
            val d = sqrt(dx * dx + dy * dy)
            val threshold = maxOf(26f, b.radius * scaleFactor + 18f)
            if (d < threshold && d < bestDist) {
                best = b
                bestDist = d
            }
        }

        return if (best != null) {
            selectedBody = if (selectedBody?.name == best.name && selectedBody?.kind == best.kind) null else best
            true
        } else {
            false
        }
    }

    private fun enterDetailMode() {
        val focus = selectedBody ?: return
        detailMode = true
        paused = false
        offsetX = 0f
        offsetY = 0f
        cameraTargetOffsetX = 0f
        cameraTargetOffsetY = 0f
        scaleFactor = 1.0f
        localBodies.clear()

        if (focus.kind == "Star") {
            val colors = listOf(Color.CYAN, Color.GREEN, Color.RED, Color.MAGENTA)
            for (i in 0 until 4) {
                val r = 130.0 + i * 90.0
                val speed = 180.0 / sqrt(r / 100.0)
                localBodies.add(
                    Body(
                        name = focus.name + "-P" + (i + 1),
                        kind = "Planet",
                        x = r,
                        y = 0.0,
                        vx = 0.0,
                        vy = speed,
                        mass = 20.0 + i * 10.0,
                        radius = (6 + i * 2).toFloat(),
                        color = colors[i % colors.size]
                    )
                )
            }
        } else if (focus.kind == "Planet") {
            val moonColors = listOf(Color.LTGRAY, Color.GRAY, Color.WHITE)
            for (i in 0 until 3) {
                val r = 70.0 + i * 45.0
                val speed = 260.0 / sqrt(r / 60.0)
                localBodies.add(
                    Body(
                        name = focus.name + "-M" + (i + 1),
                        kind = "Moon",
                        x = r,
                        y = 0.0,
                        vx = 0.0,
                        vy = speed,
                        mass = 8.0 + i * 4.0,
                        radius = (4 + i).toFloat(),
                        color = moonColors[i % moonColors.size]
                    )
                )
            }
        } else {
            localBodies.add(
                Body(
                    name = focus.name + "-Companion",
                    kind = "Satellite",
                    x = 90.0,
                    y = 0.0,
                    vx = 0.0,
                    vy = 200.0,
                    mass = 4.0,
                    radius = 4f,
                    color = Color.WHITE
                )
            )
        }
    }

    private fun exitDetailMode() {
        detailMode = false
        localBodies.clear()
        scaleFactor = 1.0f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && selectedBody == null && !detailMode) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    if (abs(dx) > 3f || abs(dy) > 3f) {
                        isDragging = true
                    }

                    offsetX += dx
                    offsetY += dy

                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y

                if (minusButton.contains(x, y)) {
                    timeScale = (timeScale / 2.0).coerceAtLeast(0.25)
                    invalidate()
                    return true
                }

                if (plusButton.contains(x, y)) {
                    timeScale = (timeScale * 2.0).coerceAtMost(16.0)
                    invalidate()
                    return true
                }

                if (modeButton.contains(x, y)) {
                    if (selectedBody != null) {
                        enterDetailMode()
                    }
                    invalidate()
                    return true
                }

                if (backButton.contains(x, y)) {
                    if (detailMode) {
                        exitDetailMode()
                    } else {
                        selectedBody = null
                    }
                    invalidate()
                    return true
                }

                if (!isDragging && !scaleDetector.isInProgress) {
                    if (!detailMode) {
                        val hit = trySelectAt(x, y)
                        if (!hit) {
                            paused = !paused
                        }
                    } else {
                        paused = !paused
                    }
                    invalidate()
                }
            }
        }
        return true
    }
}
