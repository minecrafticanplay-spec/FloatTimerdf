package com.baha.floattimer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        fun field(hint: String, v: Long) = EditText(this).apply {
            this.hint = hint
            setText(if (v > 0) v.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val total = prefs.getLong("dur", 25 * 60000L) / 60000
        val h = field("hours", total / 60)
        val m = field("minutes", total % 60)

        val row = LinearLayout(this).apply { addView(h); addView(m) }
        val start = Button(this).apply { text = "Show floating circle" }
        val close = Button(this).apply { text = "Close floating circle" }
        val help = TextView(this).apply {
            setTextColor(Color.GRAY)
            text = "Tap: play/pause\nDrag: move\nPinch, or long-press then drag up/down: resize"
        }

        start.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            val mins = (h.text.toString().toLongOrNull() ?: 0) * 60 + (m.text.toString().toLongOrNull() ?: 0)
            prefs.edit().putLong("dur", (if (mins > 0) mins else 25) * 60000L).apply()
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            stopService(Intent(this, OverlayService::class.java))
            startForegroundService(Intent(this, OverlayService::class.java))
            moveTaskToBack(true)
        }
        close.setOnClickListener { stopService(Intent(this, OverlayService::class.java)) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
            addView(row); addView(start); addView(close); addView(help)
        })
    }
}
