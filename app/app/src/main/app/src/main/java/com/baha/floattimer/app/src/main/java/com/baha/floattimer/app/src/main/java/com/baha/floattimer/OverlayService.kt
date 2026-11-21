package com.baha.floattimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class OverlayService : Service() {
    companion object {
        const val ACTION_STOP = "com.baha.floattimer.STOP"
        const val PREFS = "ft"
        private const val CH = "float_timer"
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: android.content.SharedPreferences
    private var view: CircleView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var endAt = 0L
    private var remaining = 0L
    private var duration = 0L
    private var density = 1f

    private fun dp(v: Float) = (v * density).toInt()
    private val minSize get() = dp(24f)
    private val maxSize get() = dp(280f)

    private val finish = Runnable {
        running = false
        remaining = duration
        view?.running = false
        vibrate()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startFg()
        if (view == null) createView()
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Float Timer", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Float Timer")
            .setContentText("Floating circle is on")
            .setOngoing(true)
            .addAction(0, "Close", stop)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun createView() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        density = resources.displayMetrics.density
        duration = prefs.getLong("dur", 25 * 60000L)
        remaining = duration

        val size = prefs.getInt("size", dp(64f)).coerceIn(minSize, maxSize)
        lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(24f))
            y = prefs.getInt("y", dp(200f))
        }

        val v = CircleView(this)
        view = v
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                resizeTo((lp.width * d.scaleFactor).toInt())
                return true
            }
        })

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0; var startSize = 0
        var moved = false; var resizeMode = false; var scaled = false
        val longPress = Runnable {
            resizeMode = true
            v.alpha = 0.6f
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        v.setOnTouchListener { _, e ->
            scale.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y; startSize = lp.width
                    moved = false; resizeMode = false; scaled = false
                    handler.postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scale.isInProgress) {
                        scaled = true
                        handler.removeCallbacks(longPress)
                    } else if (!scaled) {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (resizeMode) {
                            resizeTo(startSize + dy.toInt(), startX, startY, startSize)
                        } else if (moved || Math.hypot(dx.toDouble(), dy.toDouble()) > slop) {
                            moved = true
                            handler.removeCallbacks(longPress)
                            lp.x = startX + dx.toInt()
                            lp.y = startY + dy.toInt()
                            wm.updateViewLayout(v, lp)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    v.alpha = 1f
                    if (e.actionMasked == MotionEvent.ACTION_UP && !moved && !resizeMode && !scaled) toggle()
                    savePos()
                }
            }
            true
        }
        wm.addView(v, lp)
    }

    private fun resizeTo(newSize: Int, cx0: Int = lp.x, cy0: Int = lp.y, base: Int = lp.width) {
        val v = view ?: return
        val s = newSize.coerceIn(minSize, maxSize)
        lp.x = cx0 - (s - base) / 2
        lp.y = cy0 - (s - base) / 2
        lp.width = s; lp.height = s
        wm.updateViewLayout(v, lp)
    }

    private fun toggle() {
        val v = view ?: return
        if (running) {
            remaining = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(1000)
            handler.removeCallbacks(finish)
            running = false
        } else {
            if (remaining <= 0) remaining = duration
            endAt = SystemClock.elapsedRealtime() + remaining
            handler.postDelayed(finish, remaining)
            running = true
        }
        v.running = running
    }

    private fun savePos() {
        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).putInt("size", lp.width).apply()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }
}
