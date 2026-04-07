package com.example.myapplicationuniverse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.sqrt

class UniverseView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sun = Body(
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
            x = 260.0,
            y = 0.0,
            vx = 0.0,
            vy = 277.0,
            mass = 10.0,
            radius = 8f,
            color = Color.CYAN
        ),
        Body(
            x = 420.0,
            y = 0.0,
            vx = 0.0,
            vy = 218.0,
            mass = 10.0,
            radius = 10f,
            color = Color.GREEN
        )
    )

    private var lastTimeNanos = 0L
    private val g = 200.0
    private val softening = 100.0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()
        if (lastTimeNanos == 0L) lastTimeNanos = now
        val dt = ((now - lastTimeNanos) / 1_000_000_000.0).coerceIn(0.0, 0.033)
        lastTimeNanos = now

        updatePhysics(dt)

        canvas.drawColor(Color.BLACK)

        val cx = width / 2f
        val cy = height / 2f

        drawBody(canvas, sun, cx, cy)

        for (planet in planets) {
            drawTrail(canvas, planet, cx, cy)
            drawBody(canvas, planet, cx, cy)
        }

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
            if (planet.trail.size > 300) {
                planet.trail.removeAt(0)
            }
        }
    }

    private fun drawBody(canvas: Canvas, body: Body, cx: Float, cy: Float) {
        paint.color = body.color
        canvas.drawCircle(
            cx + body.x.toFloat(),
            cy + body.y.toFloat(),
            body.radius,
            paint
        )
    }

    private fun drawTrail(canvas: Canvas, body: Body, cx: Float, cy: Float) {
        paint.color = Color.DKGRAY
        paint.strokeWidth = 2f

        for (i in 1 until body.trail.size) {
            val p1 = body.trail[i - 1]
            val p2 = body.trail[i]

            canvas.drawLine(
                cx + p1.first.toFloat(),
                cy + p1.second.toFloat(),
                cx + p2.first.toFloat(),
                cy + p2.second.toFloat(),
                paint
            )
        }
    }
}
