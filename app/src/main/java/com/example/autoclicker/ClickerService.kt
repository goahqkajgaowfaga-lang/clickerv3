package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.content.pm.ActivityInfo

class ClickerService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var crosshair: View? = null
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var crossParams: WindowManager.LayoutParams

    private var running = false
    private var cps = 10
    private var targetX = 0f
    private var targetY = 0f
    private var nextTime = 0L
    private var busy = false
    private var busySince = 0L

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (!busy || now - busySince > 100) {
                tap(targetX, targetY)
            }
            nextTime += 1000L / cps
            val t = SystemClock.uptimeMillis()
            if (nextTime < t) nextTime = t
            handler.postDelayed(this, nextTime - t)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createCrosshair()
        createPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopClicking()
        panel?.let { runCatching { wm.removeView(it) } }
        crosshair?.let { runCatching { wm.removeView(it) } }
        panel = null
        crosshair = null
        return super.onUnbind(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        busy = true
        busySince = SystemClock.uptimeMillis()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { busy = false }
            override fun onCancelled(g: GestureDescription?) { busy = false }
        }, null)
        if (!ok) busy = false
    }

    private fun startClicking() {
        if (running) return
        val c = crosshair ?: return
        val loc = IntArray(2)
        c.getLocationOnScreen(loc)
        targetX = loc[0] + c.width / 2f
        targetY = loc[1] + c.height / 2f

        // Прицел становится «прозрачным» для касаний, чтобы тапы проходили в игру
        crossParams.flags = crossParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(c, crossParams)

        running = true
        btnStart.alpha = 0.4f
        btnStop.alpha = 1f
        nextTime = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(tick)
        crosshair?.let {
            crossParams.flags = crossParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(it, crossParams) }
        }
        if (::btnStart.isInitialized) {
            btnStart.alpha = 1f
            btnStop.alpha = 0.4f
        }
    }

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
                ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }

    private fun createCrosshair() {
        val size = dp(56)
        val view = object : View(this) {
            private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED; style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
            }
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 255, 0, 0); style = Paint.Style.FILL
            }
            private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED; style = Paint.Style.FILL
            }
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = width / 2f - dp(3)
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, ring)
                canvas.drawLine(cx - r, cy, cx + r, cy, ring)
                canvas.drawLine(cx, cy - r, cx, cy + r, ring)
                canvas.drawCircle(cx, cy, dp(3).toFloat(), dot)
            }
        }
        crossParams = overlayParams(size, size).apply {
            x = resources.displayMetrics.widthPixels / 2 - size / 2
            y = resources.displayMetrics.heightPixels / 2 - size / 2
        }

        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = crossParams.x; startY = crossParams.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    crossParams.x = startX + (e.rawX - touchX).toInt()
                    crossParams.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(view, crossParams)
                }
            }
            true
        }
        crosshair = view
        wm.addView(view, crossParams)
    }

    private fun createPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 25, 25, 30))
                cornerRadius = dp(12).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "☰ Кликер (тяни)"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(title)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnStart = Button(this).apply {
            text = "СТАРТ"
            setOnClickListener { startClicking() }
        }
        btnStop = Button(this).apply {
            text = "СТОП"
            alpha = 0.4f
            setOnClickListener { stopClicking() }
        }
        row.addView(btnStart, LinearLayout.LayoutParams(dp(90), dp(44)))
        row.addView(btnStop, LinearLayout.LayoutParams(dp(90), dp(44)))
        root.addView(row)

        val label = TextView(this).apply {
            text = "$cps CPS"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(label)

        val seek = SeekBar(this).apply {
            max = 19          // 0..19 -> 1..20 CPS
            progress = cps - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    cps = p + 1
                    label.text = "$cps CPS"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(seek, LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.WRAP_CONTENT))

        panelParams = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply { x = dp(16); y = dp(80) }

        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = panelParams.x; startY = panelParams.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = startX + (e.rawX - touchX).toInt()
                    panelParams.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(root, panelParams)
                }
            }
            true
        }

        panel = root
        wm.addView(root, panelParams)
    }
}
