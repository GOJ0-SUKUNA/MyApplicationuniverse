package com.example.myapplicationuniverse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
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
            setBackgroundColor(Color.argb(120, 10, 10, 18))
        }

        val infoHud = TextView(this).apply {
            text = "No object selected"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(150, 8, 12, 20))
        }

        glView.setStatusListener { msg ->
            runOnUiThread {
                statusHud.text = msg
            }
        }

        glView.setInfoListener { msg ->
            runOnUiThread {
                infoHud.text = msg
            }
        }

        root.addView(glView)

        root.addView(
            statusHud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val infoParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        infoParams.setMargins(0, 0, 0, 0)
        root.addView(infoHud, infoParams)

        setContentView(root)
    }
}
