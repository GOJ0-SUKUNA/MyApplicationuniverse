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

        val hud = TextView(this).apply {
            text = "Loading universe..."
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(120, 10, 10, 18))
        }

        glView.setStatusListener { msg ->
            runOnUiThread {
                hud.text = msg
            }
        }

        root.addView(glView)
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        setContentView(root)
    }
}
