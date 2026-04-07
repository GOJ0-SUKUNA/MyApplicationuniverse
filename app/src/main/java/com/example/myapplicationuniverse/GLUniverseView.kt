package com.example.myapplicationuniverse

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

class GLUniverseView(context: Context) : GLSurfaceView(context) {

    private val renderer: StarfieldRenderer
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var statusListener: ((String) -> Unit)? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        setEGLContextClientVersion(2)
        renderer = StarfieldRenderer()
        renderer.statusCallback = { msg ->
            post { statusListener?.invoke(msg) }
        }

        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    renderer.cameraDistance /= detector.scaleFactor
                    renderer.cameraDistance = renderer.cameraDistance.coerceIn(2.2f, 55f)
                    return true
                }
            }
        )

        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    queueEvent {
                        renderer.pickAt(e.x, e.y)
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    queueEvent {
                        renderer.toggleSystemMode()
                    }
                    return true
                }
            }
        )
    }

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
        listener("Drag: rotate | Pinch: zoom | Tap: select | Double tap: enter/exit system")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    if (abs(dx) > 2f || abs(dy) > 2f) dragging = true

                    renderer.yaw += dx * 0.005f
                    renderer.pitch += dy * 0.005f
                    renderer.pitch = renderer.pitch.coerceIn(-1.2f, 1.2f)

                    lastX = event.x
                    lastY = event.y
                }
            }
        }
        return true
    }
}
