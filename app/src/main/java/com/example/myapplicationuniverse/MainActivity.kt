package com.example.myapplicationuniverse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        val glView = GLUniverseView(this)

        val statusHud = TextView(this).apply {
            text = "Loading universe..."
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(130, 10, 10, 18))
        }

        val infoHud = TextView(this).apply {
            text = "No object selected"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(150, 8, 12, 20))
        }

        glView.setStatusListener { msg ->
            runOnUiThread { statusHud.text = msg }
        }

        glView.setInfoListener { msg ->
            runOnUiThread { infoHud.text = msg }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.argb(120, 12, 14, 22))
            gravity = Gravity.CENTER
        }

        fun makeButton(label: String, action: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setOnClickListener { action() }
            }
        }

        controls.addView(makeButton("- Time") { glView.slowDownTime() })
        controls.addView(makeButton("+ Time") { glView.speedUpTime() })
        controls.addView(makeButton("Pause") { glView.togglePause() })
        controls.addView(makeButton("Reset") { glView.resetView() })
        controls.addView(makeButton("Mode") { glView.toggleMode() })

        root.addView(glView)

        root.addView(
            statusHud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val controlsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        controlsParams.setMargins(0, 0, 0, 220)
        root.addView(controls, controlsParams)

        root.addView(
            infoHud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }
}
