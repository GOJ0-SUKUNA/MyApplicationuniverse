package com.example.myapplicationuniverse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.sqrt

class UniverseView(context: Context) : View(context) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
    }

    private val sun = Body(
        name = "Sun",
        x = 0.0,
        y = 0.0,
        vx = 0.0,
        vy = 0.0,
        mass = 100000.0,
        radius = 24f,
        color = Color.YELLOW
    )

    private val planets = mutableListOf(
        Body(
            name = "Aqua",
            x = 260.0,
            y = 0.0,
            vx = 0.0,
            vy = 277.0,
            mass = 10.0,
            radius = 8f,
            color = Color.CYAN
        ),
        Body(
            name = "Verd",
            x = 420.0,
            y = 0.0,
            vx = 0.0,
            vy = 218.0,
            mass = 10.0,
            radius = 10f,
            color = Color.GREEN
        ),
        Body(
            name = "Crimson",
            x = 560.0,
            y = 0.0,
            vx = 0.0,
            vy = 188.0,
            mass = 12.0,
            radius = 12f,
            color = Color.RED
        )
    )

    private var lastTimeNanos = 0L
    private val g = 200.0
    private val softening = 100.0

    private var paused = false

    private var scaleFactor = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.3f, 4.5f)
            invalidate()
            return true
        }
    })

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        if (lastTimeNanos == 0L) lastTimeNanos = now
        val dt = ((now - lastTimeNanos) / 1_000_000_000.0).coerceIn(0.0, 0.033)
        lastTimeNanos = now

        if (!paused) {
            updatePhysics(dt)
        }

        canvas.drawColor(Color.BLACK)

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        drawBody(canvas, sun, cx, cy)

        for (planet in planets) {
            drawTrail(canvas, planet, cx, cy)
            drawBody(canvas, planet, cx, cy)
        }

        canvas.drawText("Pinch: zoom   Drag: pan   Tap: pause/resume", 30f, 55f, textPaint)
        canvas.drawText("Scale: " + String.format("%.2f", scaleFactor), 30f, 105f, textPaint)
        canvas.drawText("Status: " + if (paused) "Paused" else "Running", 30f, 155f, textPaint)

        postInvalidateOnAnimation()
    }

    private fun updatePhysics(dt: Double) {
        for (planet in planets) {
            val dx = sun.x - planet.x
            val dy = sun.y - planet.y

            val distSq = dx * dx + dy * dy + softening
            val dist = sqrt(distSq)

            val accel = g * sun.mass / distSq
            val ax = accel * dx / dist
            val ay = accel * dy / dist

            planet.vx += ax * dt
            planet.vy += ay * dt

            planet.x += planet.vx * dt
            planet.y += planet.vy * dt

            planet.trail.add(planet.x to planet.y)
            if (planet.trail.size > 400) {
                planet.trail.removeAt(0)
            }
        }
    }

    private fun drawBody(canvas: Canvas, body: Body, cx: Float, cy: Float) {
        bodyPaint.color = body.color
        canvas.drawCircle(
            cx + body.x.toFloat() * scaleFactor,
            cy + body.y.toFloat() * scaleFactor,
            body.radius * scaleFactor.coerceAtLeast(0.7f),
            bodyPaint
        )
    }

    private fun drawTrail(canvas: Canvas, body: Body, cx: Float, cy: Float) {
        bodyPaint.color = Color.DKGRAY
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY

                    if (kotlin.math.abs(dx) > 3f || kotlin.math.abs(dy) > 3f) {
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
                    paused = !paused
                    invalidate()
                }
            }
        }
        return true
    }
}
