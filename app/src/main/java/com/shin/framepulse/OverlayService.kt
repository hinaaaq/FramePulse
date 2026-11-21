package com.shin.framepulse

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.shin.framepulse.START"
        const val ACTION_STOP = "com.shin.framepulse.STOP"
        const val ACTION_TOGGLE_LOCK = "com.shin.framepulse.TOGGLE_LOCK"
        const val ACTION_UPDATE_CONFIG = "com.shin.framepulse.UPDATE_CONFIG"
        private const val CHANNEL_ID = "framepulse_overlay_channel"
        private const val NOTIF_ID = 1001

        var isRunning = false
            private set
        var isTouchLocked = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var monitor: SystemMonitor
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var rootContainer: LinearLayout? = null
    private var hudText: TextView? = null
    private var quickActionRow: LinearLayout? = null
    private var bgDrawable: GradientDrawable? = null

    private var lastTelemetry: TelemetryData? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerExecutor = Executors.newSingleThreadExecutor()

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            workerExecutor.execute {
                val data = monitor.collect()
                lastTelemetry = data
                mainHandler.post {
                    renderTelemetry(data)
                }
            }
            mainHandler.postDelayed(this, Prefs.updateIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        monitor = SystemMonitor(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_LOCK -> {
                if (isRunning) {
                    setTouchLocked(!isTouchLocked)
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                if (isRunning) {
                    applyVisualConfig()
                    lastTelemetry?.let { renderTelemetry(it) }
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!isRunning) {
            isRunning = true
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            setupOverlayView()
            applyVisualConfig()
            monitor.start()
            mainHandler.post(updateRunnable)
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        bgDrawable = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        hudText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setShadowLayer(3.5f, 1.5f, 1.5f, Color.BLACK)
            text = "FPS --"
        }

        quickActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(3), 0, 0)
        }

        val lockBtn = TextView(this).apply {
            text = "[LOCK]"
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#38BDF8"))
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setPadding(0, 0, dp(8), 0)
            setOnClickListener { setTouchLocked(true) }
        }

        val closeBtn = TextView(this).apply {
            text = "[X]"
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#F87171"))
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setOnClickListener { stopSelf() }
        }

        quickActionRow?.addView(lockBtn)
        quickActionRow?.addView(closeBtn)

        rootContainer?.addView(hudText)
        rootContainer?.addView(quickActionRow)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.posX
            y = Prefs.posY
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        rootContainer?.setOnTouchListener { _, event ->
            if (isTouchLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 5 || abs(dy) > 5) moved = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    windowManager.updateViewLayout(rootContainer, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        Prefs.posX = layoutParams.x
                        Prefs.posY = layoutParams.y
                    } else {
                        Prefs.showQuickButtons = !Prefs.showQuickButtons
                        quickActionRow?.visibility =
                            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(rootContainer, layoutParams)
    }

    private fun applyVisualConfig() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val alpha = Prefs.bgAlpha
        bgDrawable?.setColor(Color.argb(alpha, 15, 23, 42))
        if (alpha > 20) {
            val strokeAlpha = (alpha * 0.45f).toInt().coerceIn(0, 255)
            bgDrawable?.setStroke(dp(1), Color.argb(strokeAlpha, 56, 189, 248))
        } else {
            bgDrawable?.setStroke(0, Color.TRANSPARENT)
        }

        hudText?.textSize = Prefs.textSizeSp
        quickActionRow?.visibility =
            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE

        layoutParams.x = Prefs.posX
        layoutParams.y = Prefs.posY
        rootContainer?.let {
            try {
                windowManager.updateViewLayout(it, layoutParams)
            } catch (_: Exception) {
            }
        }
    }

    private fun setTouchLocked(locked: Boolean) {
        isTouchLocked = locked
        if (locked) {
            quickActionRow?.visibility = View.GONE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            quickActionRow?.visibility = if (Prefs.showQuickButtons) View.VISIBLE else View.GONE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        rootContainer?.let { windowManager.updateViewLayout(it, layoutParams) }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun renderTelemetry(data: TelemetryData) {
        val mode = Prefs.layoutMode
        val items = mutableListOf<Pair<String, Int>>()

        val fpsColor = when {
            data.fps >= 50 -> Color.parseColor("#22C55E")
            data.fps >= 30 -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#EF4444")
        }

        if (mode == Prefs.LAYOUT_MINI) {
            if (Prefs.showFps) items.add("${data.fps}FPS" to fpsColor)
            if (Prefs.showTemp) items.add("${monitor.formatFloat(data.batteryTempC, 1)}°" to Color.parseColor("#38BDF8"))
            if (Prefs.showCpu) items.add("C:${data.cpuLoadPercent}%" to Color.WHITE)
            if (Prefs.showGpu && data.gpuLoadPercent != null) items.add("G:${data.gpuLoadPercent}%" to Color.WHITE)
        } else {
            if (Prefs.showFps) {
                val tag = if (data.isTrueGameFps) "" else "*"
                items.add("FPS ${data.fps}$tag" to fpsColor)
            }
            if (Prefs.showCpu) {
                items.add("CPU ${data.cpuLoadPercent}%" to Color.parseColor("#E2E8F0"))
            }
            if (Prefs.showClock) {
                items.add("CLK ${monitor.formatFloat(data.cpuMaxGhz, 2)}G" to Color.parseColor("#CBD5E1"))
            }
            if (Prefs.showGpu && data.gpuLoadPercent != null) {
                items.add("GPU ${data.gpuLoadPercent}%" to Color.parseColor("#A78BFA"))
            }
            if (Prefs.showRam) {
                items.add("RAM ${monitor.formatFloat(data.ramUsedGb, 1)}G" to Color.parseColor("#F472B6"))
            }
            if (Prefs.showTemp) {
                val tempColor = if (data.batteryTempC >= 42f) Color.parseColor("#F87171") else Color.parseColor("#38BDF8")
                items.add("${monitor.formatFloat(data.batteryTempC, 1)}°C" to tempColor)
            }
            if (Prefs.showWatt) {
                items.add("${monitor.formatFloat(data.powerWatts, 1)}W" to Color.parseColor("#FBBF24"))
            }
        }

        if (items.isEmpty()) {
            items.add("FPS ${data.fps}" to fpsColor)
        }

        val separator = if (mode == Prefs.LAYOUT_VERTICAL) "\n" else " | "
        val builder = SpannableStringBuilder()
        items.forEachIndexed { index, (text, color) ->
            val start = builder.length
            builder.append(text)
            builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (index < items.lastIndex) {
                builder.append(separator)
            }
        }
        hudText?.text = builder
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FramePulse Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val toggleLockIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_LOCK
        }
        val lockPending = PendingIntent.getService(
            this, 1, toggleLockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lockLabel = if (isTouchLocked) "Unlock HUD" else "Lock Touch"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("FramePulse Aktif")
            .setContentText(if (isTouchLocked) "Mode tembus sentuhan aktif" else "Geser HUD untuk pindah, tap untuk tombol")
            .setOngoing(true)
            .addAction(0, lockLabel, lockPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        isTouchLocked = false
        mainHandler.removeCallbacks(updateRunnable)
        monitor.stop()
        workerExecutor.shutdownNow()
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
