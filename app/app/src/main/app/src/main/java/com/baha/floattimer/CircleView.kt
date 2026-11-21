package com.baha.floattimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class CircleView(ctx: Context) : View(ctx) {
    var running = false
        set(v) { field = v; invalidate() }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt(); style = Paint.Style.STROKE
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDDDDD.toInt() }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val cx = s / 2
        ring.strokeWidth = s * 0.04f
        val r = s / 2 - ring.strokeWidth / 2
        c.drawCircle(cx, cx, r, bg)
        c.drawCircle(cx, cx, r, ring)
        if (running) {
            val bw = s * 0.09f
            val gap = s * 0.07f
            val h = s * 0.17f
            c.drawRect(cx - gap / 2 - bw, cx - h, cx - gap / 2, cx + h, icon)
            c.drawRect(cx + gap / 2, cx - h, cx + gap / 2 + bw, cx + h, icon)
        } else {
            path.reset()
            path.moveTo(cx - s * 0.09f, cx - s * 0.17f)
            path.lineTo(cx - s * 0.09f, cx + s * 0.17f)
            path.lineTo(cx + s * 0.17f, cx)
            path.close()
            c.drawPath(path, icon)
        }
    }
}
