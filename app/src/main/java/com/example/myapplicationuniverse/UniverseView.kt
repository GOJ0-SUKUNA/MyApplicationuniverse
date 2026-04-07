package com.example.myapplicationuniverse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

class UniverseView(context: Context) : View(context) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 20, 20, 20)
    }

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

    private var lastTimeNanos = 0L
    private val gSun = 200.0
    private val gEarth = 120.0
    private val gMoon = 70.0
    private val softening = 100.0

    private var paused = false
    private var scaleFactor = 1.0f

    private var offsetX = 0f
    private var offsetY = 0f
    private var cameraTargetOffsetX = 0f
    private var cameraTargetOffsetY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private var selectedBody: Body? = null

    private var liveStatus = "Fetching live data..."
    private var lastRefreshLabel = "Never"
    private var lastRefreshMs = 0L
    private val refreshIntervalMs = 30L * 60L * 1000L
    private var loading = false

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.2f, 6.0f)
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
        val dt = ((now - lastTimeNanos) / 1_000_000_000.0).coerceIn(0.0, 0.033)
        lastTimeNanos = now

        if (!paused) {
            updatePhysics(dt)
        }

        updateCameraFocus()

        canvas.drawColor(Color.BLACK)

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        drawBody(canvas, sun, cx, cy)
        for (planet in planets) {
            drawTrail(canvas, planet, cx, cy, Color.DKGRAY)
            drawBody(canvas, planet, cx, cy)
        }
        for (m in moons) {
            drawTrail(canvas, m, cx, cy, Color.GRAY)
            drawBody(canvas, m, cx, cy)
        }
        for (sat in liveSatellites) {
            drawTrail(canvas, sat, cx, cy, Color.argb(70, 200, 200, 200))
            drawBody(canvas, sat, cx, cy)
        }
        for (neo in liveNeos) {
            drawTrail(canvas, neo, cx, cy, Color.argb(70, 120, 120, 120))
            drawBody(canvas, neo, cx, cy)
        }

        drawHud(canvas)
        postInvalidateOnAnimation()
    }

    private fun updateCameraFocus() {
        val target = selectedBody
        if (target != null) {
            cameraTargetOffsetX = -target.x.toFloat() * scaleFactor
            cameraTargetOffsetY = -target.y.toFloat() * scaleFactor
            offsetX += (cameraTargetOffsetX - offsetX) * 0.08f
            offsetY += (cameraTargetOffsetY - offsetY) * 0.08f
        }
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
            updateOrbiter(planet, sun, dt, gSun, 500)
        }

        val desiredMoonRadius = 42.0
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
        if (moon.trail.size > 240) moon.trail.removeAt(0)

        for (sat in liveSatellites) {
            updateOrbiter(sat, earth, dt, gEarth, 140)
        }

        for (neo in liveNeos) {
            updateOrbiter(neo, sun, dt, gSun * 0.75, 180)
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
        val rr = body.radius * scaleFactor.coerceAtLeast(0.65f)

        if (body.kind == "Star") {
            bodyPaint.color = Color.argb(70, 255, 220, 120)
            canvas.drawCircle(sx, sy, rr * 1.9f, bodyPaint)
        }

        bodyPaint.color = body.color
        canvas.drawCircle(sx, sy, rr, bodyPaint)

        if (selectedBody === body) {
            bodyPaint.style = Paint.Style.STROKE
            bodyPaint.strokeWidth = 4f
            bodyPaint.color = Color.WHITE
            canvas.drawCircle(sx, sy, rr + 10f, bodyPaint)
            bodyPaint.style = Paint.Style.FILL
        }
    }

    private fun drawTrail(canvas: Canvas, body: Body, cx: Float, cy: Float, color: Int) {
        bodyPaint.color = color
        bodyPaint.strokeWidth = 2f

        for (i in 1 until body.trail.size) {
            val p1 = body.trail[i - 1]
            val p2 = body.trail[i]
            canvas.drawLine(
                cx + p1.first.toFloat() * scaleFactor,
                cy + p1.second.toFloat() * scaleFactor,
                cx + p2.first.toFloat() * scaleFactor,
                cy + p2.second.toFloat() * scaleFactor,
                bodyPaint
            )
        }
    }

    private fun drawHud(canvas: Canvas) {
        canvas.drawRoundRect(18f, 18f, width - 18f, 320f, 18f, 18f, hudPaint)
        canvas.drawText("Pinch zoom | Drag pan | Tap body to select", 34f, 60f, textPaint)
        canvas.drawText("Tap empty space: pause/resume | Tap selected again: unfocus", 34f, 102f, textPaint)
        canvas.drawText("Scale: " + String.format("%.2f", scaleFactor), 34f, 144f, textPaint)
        canvas.drawText("Status: " + if (paused) "Paused" else "Running", 34f, 186f, textPaint)
        canvas.drawText("Live sats: " + liveSatellites.size + " | Live NEOs: " + liveNeos.size, 34f, 228f, textPaint)

        val s = selectedBody
        val selectedText = if (s == null) {
            "Selected: none"
        } else {
            "Selected: " + s.name + " | " + s.kind + " | mass " + String.format("%.1f", s.mass)
        }
        canvas.drawText(selectedText, 34f, 270f, textPaint)
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
            val threshold = maxOf(24f, b.radius * scaleFactor + 18f)
            if (d < threshold && d < bestDist) {
                best = b
                bestDist = d
            }
        }

        return if (best != null) {
            selectedBody = if (selectedBody === best) null else best
            true
        } else {
            false
        }
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
                if (!scaleDetector.isInProgress && selectedBody == null) {
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
                if (!isDragging && !scaleDetector.isInProgress) {
                    val hit = trySelectAt(event.x, event.y)
                    if (!hit) {
                        paused = !paused
                    }
                    invalidate()
                }
            }
        }
        return true
    }
}
