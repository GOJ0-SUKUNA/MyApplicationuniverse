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
    private var infoListener: ((String) -> Unit)? = null

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    @Volatile
    private var latestObjectInfo = "No object selected"

    @Volatile
    private var latestLiveSummary = "Live data pending..."

    @Volatile
    private var latestLiveShort = "Live: pending"

    @Volatile
    private var refreshInFlight = false

    @Volatile
    private var lastRefreshMs = 0L

    private val refreshIntervalMs = 15L * 60L * 1000L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (!refreshInFlight && now - lastRefreshMs >= refreshIntervalMs) {
                refreshLiveData()
            }
            postDelayed(this, refreshIntervalMs)
        }
    }

    init {
        setEGLContextClientVersion(2)
        renderer = StarfieldRenderer()

        renderer.statusCallback = { msg ->
            post { statusListener?.invoke(msg + " | " + latestLiveShort) }
        }

        renderer.infoCallback = { msg ->
            latestObjectInfo = msg
            post { infoListener?.invoke(combinedInfo()) }
        }

        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    renderer.cameraDistance /= detector.scaleFactor
                    renderer.cameraDistance = renderer.cameraDistance.coerceIn(2.0f, 60f)
                    renderer.emitStatus("Zoom updated")
                    return true
                }
            }
        )

        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    queueEvent { renderer.pickAt(e.x, e.y) }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    queueEvent { renderer.toggleSystemMode() }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    queueEvent { renderer.toggleExoticMode() }
                }
            }
        )

        refreshLiveData()
        postDelayed(refreshRunnable, refreshIntervalMs)
    }

    private fun combinedInfo(): String {
        return latestObjectInfo + "\n\nLive data:\n" + latestLiveSummary
    }

    private fun refreshLiveData() {
        if (refreshInFlight) return
        refreshInFlight = true
        latestLiveShort = "Live: refreshing..."
        statusListener?.invoke("Refreshing live space data...")

        Thread {
            val summary = LiveSpaceRepository.fetchLiveSummary()
            lastRefreshMs = System.currentTimeMillis()
            refreshInFlight = false

            val stationPreview = if (summary.stationNames.isEmpty()) "none" else summary.stationNames.joinToString(", ")
            val neoPreview = if (summary.neoNames.isEmpty()) "none" else summary.neoNames.joinToString(", ")

            latestLiveSummary =
                "Status: " + summary.status +
                    "\nCelesTrak stations: " + summary.stationsCount +
                    "\nSample stations: " + stationPreview +
                    "\nNASA NEOs today: " + summary.neoCount +
                    "\nHazardous today: " + summary.hazardousCount +
                    "\nSample NEOs: " + neoPreview

            latestLiveShort =
                "Live: stations " + summary.stationsCount +
                    " | NEOs " + summary.neoCount +
                    " | hazardous " + summary.hazardousCount

            post {
                renderer.emitStatus("Live space data updated")
                infoListener?.invoke(combinedInfo())
            }
        }.start()
    }

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
        listener("Loading... | " + latestLiveShort)
    }

    fun setInfoListener(listener: (String) -> Unit) {
        infoListener = listener
        listener(combinedInfo())
    }

    fun slowDownTime() {
        queueEvent { renderer.slowDownTime() }
    }

    fun speedUpTime() {
        queueEvent { renderer.speedUpTime() }
    }

    fun togglePause() {
        queueEvent { renderer.togglePause() }
    }

    fun resetView() {
        queueEvent { renderer.resetView() }
    }

    fun toggleMode() {
        queueEvent { renderer.toggleSystemMode() }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refreshRunnable)
        super.onDetachedFromWindow()
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
