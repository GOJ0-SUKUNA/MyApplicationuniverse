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
                    renderer.cameraDistance = renderer.cameraDistance.coerceIn(2.0f, 60f)
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

                override fun onLongPress(e: MotionEvent) {
                    queueEvent {
                        renderer.toggleExoticMode()
                    }
                }
            }
        )
    }

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
        listener("Drag rotate | Pinch zoom | Tap select | Double tap local mode | Long press exotic mode")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    if (abs(dx) > 1f || abs(dy) > 1f) {
                        renderer.yaw += dx * 0.005f
                        renderer.pitch += dy * 0.005f
                        renderer.pitch = renderer.pitch.coerceIn(-1.2f, 1.2f)
                    }

                    lastX = event.x
                    lastY = event.y
                }
            }
        }
        return true
    }
}
