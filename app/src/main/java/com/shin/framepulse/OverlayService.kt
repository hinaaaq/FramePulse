package com.shin.framepulse

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.shin.framepulse.START"
        const val ACTION_STOP = "com.shin.framepulse.STOP"
        const val ACTION_TOGGLE_LOCK = "com.shin.framepulse.TOGGLE_LOCK"
        const val ACTION_UPDATE_CONFIG = "com.shin.framepulse.UPDATE_CONFIG"
        private const val CHANNEL_ID = "framepulse_overlay_channel"
        private const val NOTIF_ID = 1001

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isTouchLocked = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var monitor: SystemMonitor
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var rootContainer: LinearLayout? = null
    private var hudText: TextView? = null
    private var sparklineView: HudSparklineView? = null
    private var quickActionRow: LinearLayout? = null
    private var recBtnView: TextView? = null
    private var bgDrawable: GradientDrawable? = null

    private var lastTelemetry: TelemetryData? = null
    private var lastRenderedHash: Int = 0
    private val isCollecting = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "FramePulse-SensorWorker").apply {
            isDaemon = true
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (!isCollecting.compareAndSet(false, true)) {
                mainHandler.postDelayed(this, 100L)
                return
            }
            workerExecutor.execute {
                try {
                    val data = monitor.collect()
                    lastTelemetry = data
                    mainHandler.post {
                        if (isRunning) {
                            renderTelemetry(data)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    isCollecting.set(false)
                    if (isRunning) {
                        mainHandler.postDelayed(this, Prefs.updateIntervalMs)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        monitor = SystemMonitor(this)
        createNotificationChannel()
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun syncQsTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, FramePulseTileService::class.java))
        } catch (_: Throwable) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isRunning) {
            mainHandler.post {
                val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                layoutParams.x = Prefs.getPosX(land)
                layoutParams.y = Prefs.getPosY(land)
                clampPositionToScreen()
                rootContainer?.let {
                    try {
                        windowManager.updateViewLayout(it, layoutParams)
                    } catch (_: Exception) {
                    }
                }
            }
        }
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
                    lastRenderedHash = 0
                    applyVisualConfig()
                    lastTelemetry?.let { renderTelemetry(it) }
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            try {
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
                syncQsTile()
                if (Prefs.turboAutoBoost) {
                    workerExecutor.execute {
                        try { GameBooster.runQuickBoost(this) } catch (_: Throwable) {}
                    }
                }
                mainHandler.removeCallbacks(updateRunnable)
                mainHandler.post(updateRunnable)
            } catch (_: Throwable) {
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        bgDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }

        hudText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            includeFontPadding = false
            text = "FPS --"
        }

        sparklineView = HudSparklineView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(22)
            ).apply {
                topMargin = dp(4)
            }
        }

        quickActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(5), 0, 0)
        }

        fun makeActionChip(label: String, textColorHex: String, onClick: () -> Unit): TextView {
            val chipBg = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.argb(175, 15, 23, 42))
                setStroke(dp(1), Color.argb(110, 148, 163, 184))
            }
            return TextView(this).apply {
                text = label
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor(textColorHex))
                background = chipBg
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(5)
                }
                setOnClickListener { onClick() }
            }
        }

        recBtnView = makeActionChip(
            label = if (TelemetryHub.isRecording) "STOP" else "REC",
            textColorHex = "#FBBF24"
        ) {
            if (TelemetryHub.isRecording) {
                TelemetryHub.stopRecording()
                recBtnView?.text = "REC"
            } else {
                TelemetryHub.startRecording()
                recBtnView?.text = "STOP"
            }
            lastRenderedHash = 0
            lastTelemetry?.let { renderTelemetry(it) }
        }

        val modeBtn = makeActionChip("MODE", "#A78BFA") {
            Prefs.layoutMode = when (Prefs.layoutMode) {
                Prefs.LAYOUT_HORIZONTAL -> Prefs.LAYOUT_VERTICAL
                Prefs.LAYOUT_VERTICAL -> Prefs.LAYOUT_MINI
                else -> Prefs.LAYOUT_HORIZONTAL
            }
            lastRenderedHash = 0
            applyVisualConfig()
            lastTelemetry?.let { renderTelemetry(it) }
        }

        val lockBtn = makeActionChip("LOCK", "#38BDF8") {
            setTouchLocked(true)
        }

        val closeBtn = makeActionChip("X", "#F87171") {
            stopSelf()
        }

        val boostBtn = makeActionChip("BST", "#10B981") {
            workerExecutor.execute {
                val res = GameBooster.runQuickBoost(this)
                mainHandler.post {
                    Toast.makeText(this, "Boost: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        quickActionRow?.addView(boostBtn)
        quickActionRow?.addView(recBtnView)
        quickActionRow?.addView(modeBtn)
        quickActionRow?.addView(lockBtn)
        quickActionRow?.addView(closeBtn)

        rootContainer?.addView(hudText)
        rootContainer?.addView(sparklineView)
        rootContainer?.addView(quickActionRow)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val land = isLandscape()
            x = Prefs.getPosX(land)
            y = Prefs.getPosY(land)
            alpha = 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
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
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    clampPositionToScreen()
                    try {
                        windowManager.updateViewLayout(rootContainer, layoutParams)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (Prefs.snapToEdge) {
                            snapToNearestEdge()
                        } else {
                            clampPositionToScreen()
                        }
                        Prefs.setPos(isLandscape(), layoutParams.x, layoutParams.y)
                        try {
                            windowManager.updateViewLayout(rootContainer, layoutParams)
                        } catch (_: Exception) {
                        }
                    } else {
                        Prefs.showQuickButtons = !Prefs.showQuickButtons
                        recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"
                        quickActionRow?.visibility =
                            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        clampPositionToScreen()
        windowManager.addView(rootContainer, layoutParams)
    }

    private fun getScreenBoundsPx(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun snapToNearestEdge() {
        val density = resources.displayMetrics.density
        val (screenW, _) = getScreenBoundsPx()
        val margin = (8 * density).toInt()
        val viewW = rootContainer?.width?.takeIf { it > 0 } ?: (120 * density).toInt()
        val maxX = (screenW - viewW - margin).coerceAtLeast(margin)
        val centerX = layoutParams.x + viewW / 2
        layoutParams.x = if (centerX < screenW / 2) margin else maxX
        clampPositionToScreen()
    }

    private fun clampPositionToScreen() {
        val density = resources.displayMetrics.density
        val (screenW, screenH) = getScreenBoundsPx()
        val viewW = rootContainer?.width?.takeIf { it > 0 } ?: (120 * density).toInt()
        val viewH = rootContainer?.height?.takeIf { it > 0 } ?: (40 * density).toInt()
        val maxX = (screenW - viewW).coerceAtLeast(0)
        val maxY = (screenH - viewH).coerceAtLeast(0)
        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
    }

    private fun getThemeAccent(): Int {
        return when (Prefs.themeMode) {
            Prefs.THEME_EMERALD -> Color.parseColor("#10B981")
            Prefs.THEME_CRIMSON -> Color.parseColor("#F43F5E")
            Prefs.THEME_AMBER -> Color.parseColor("#FBBF24")
            else -> Color.parseColor("#38BDF8")
        }
    }

    private fun applyVisualConfig() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val style = Prefs.hudStyle
        val alphaVal = if (style == Prefs.STYLE_MINIMAL) 0 else Prefs.bgAlpha
        val accent = getThemeAccent()

        when (style) {
            Prefs.STYLE_PILL -> {
                bgDrawable?.cornerRadius = dp(99).toFloat()
                rootContainer?.setPadding(dp(12), dp(5), dp(12), dp(5))
            }
            Prefs.STYLE_MINIMAL -> {
                bgDrawable?.cornerRadius = 0f
                rootContainer?.setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            else -> {
                bgDrawable?.cornerRadius = dp(10).toFloat()
                rootContainer?.setPadding(dp(9), dp(6), dp(9), dp(6))
            }
        }

        bgDrawable?.setColor(Color.argb(alphaVal, 9, 14, 26))
        if (alphaVal > 20) {
            val strokeAlpha = (alphaVal * 0.6f).toInt().coerceIn(0, 255)
            bgDrawable?.setStroke(
                dp(1),
                Color.argb(strokeAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
            hudText?.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            bgDrawable?.setStroke(0, Color.TRANSPARENT)
            hudText?.setShadowLayer(4f, 1.2f, 1.2f, Color.BLACK)
        }

        hudText?.textSize = Prefs.textSizeSp
        recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"
        val allowGraph = Prefs.showHudGraph &&
            Prefs.layoutMode != Prefs.LAYOUT_MINI &&
            style != Prefs.STYLE_PILL
        sparklineView?.visibility = if (allowGraph) View.VISIBLE else View.GONE
        quickActionRow?.visibility =
            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE

        val land = isLandscape()
        layoutParams.x = Prefs.getPosX(land)
        layoutParams.y = Prefs.getPosY(land)
        clampPositionToScreen()
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
            // Android 12+ requires window alpha <= 0.8f so system does not block pass-through game touches
            layoutParams.alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.78f else 1.0f
        } else {
            recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"
            quickActionRow?.visibility = if (Prefs.showQuickButtons) View.VISIBLE else View.GONE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            layoutParams.alpha = 1.0f
        }
        rootContainer?.let {
            try {
                windowManager.updateViewLayout(it, layoutParams)
            } catch (_: Exception) {
            }
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun renderTelemetry(data: TelemetryData) {
        val mode = Prefs.layoutMode
        val accent = getThemeAccent()
        val recording = TelemetryHub.isRecording
        val items = mutableListOf<Pair<String, Int>>()

        val fpsColor = when {
            data.fps >= 50 -> Color.parseColor("#22C55E")
            data.fps >= 30 -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#EF4444")
        }

        if (recording) {
            items.add("●REC" to Color.parseColor("#EF4444"))
        }

        if (mode == Prefs.LAYOUT_MINI) {
            if (Prefs.showFps) items.add("${if (data.isTrueGameFps) "" else "~"}${data.fps}FPS" to fpsColor)
            if (Prefs.showTemp) {
                val tCol = if (data.batteryTempC >= 42f) Color.parseColor("#F87171") else accent
                items.add("${monitor.formatFloat(data.batteryTempC, 1)}°" to tCol)
            }
            if (Prefs.showCpu) items.add("C:${data.cpuLoadPercent}%" to Color.WHITE)
            if (Prefs.showGpu && data.gpuLoadPercent != null) items.add("G:${data.gpuLoadPercent}%" to Color.parseColor("#C084FC"))
            if (Prefs.showBatteryPct) items.add("${data.batteryPercent}%" to Color.parseColor("#34D399"))
        } else {
            if (Prefs.showFps) {
                val ft = if (Prefs.showFrameTime && data.frameTimeMs > 0f) " ${monitor.formatFloat(data.frameTimeMs, 1)}ms" else ""
                items.add("${if (data.isTrueGameFps) "FPS" else "~FPS"} ${data.fps}$ft" to fpsColor)
            }
            if (Prefs.showCpu) {
                items.add("CPU ${data.cpuLoadPercent}%" to Color.parseColor("#F1F5F9"))
            }
            if (Prefs.showClock) {
                items.add("CLK ${monitor.formatFloat(data.cpuMaxGhz, 2)}G" to Color.parseColor("#CBD5E1"))
            }
            if (Prefs.showGpu && data.gpuLoadPercent != null) {
                items.add("GPU ${data.gpuLoadPercent}%" to Color.parseColor("#C084FC"))
            }
            if (Prefs.showRam) {
                items.add("RAM ${monitor.formatFloat(data.ramUsedGb, 1)}G" to Color.parseColor("#F472B6"))
            }
            if (Prefs.showTemp) {
                val tempColor = when {
                    data.batteryTempC >= 42f -> Color.parseColor("#F87171")
                    data.batteryTempC >= 38f -> Color.parseColor("#FBBF24")
                    else -> accent
                }
                items.add("${monitor.formatFloat(data.batteryTempC, 1)}°C" to tempColor)
            }
            if (Prefs.showBatteryPct) {
                items.add("BAT ${data.batteryPercent}%" to Color.parseColor("#34D399"))
            }
            if (Prefs.showWatt) {
                val wPrefix = if (data.isCharging) "+" else "-"
                items.add("$wPrefix${monitor.formatFloat(data.powerWatts, 1)}W" to if (data.isCharging) Color.parseColor("#34D399") else Color.parseColor("#FBBF24"))
            }
            if (Prefs.showNet) {
                items.add("↓${data.netDownKbps.toInt()}K" to Color.parseColor("#34D399"))
            }
        }

        if (items.isEmpty()) {
            items.add("${if (data.isTrueGameFps) "FPS" else "~FPS"} ${data.fps}" to fpsColor)
        }

        val contentHash = items.hashCode() * 31 + mode.hashCode()
        if (contentHash != lastRenderedHash) {
            lastRenderedHash = contentHash
            val separator = if (mode == Prefs.LAYOUT_VERTICAL) "\n" else " │ "
            val builder = SpannableStringBuilder()
            items.forEachIndexed { index, (text, color) ->
                val start = builder.length
                builder.append(text)
                builder.setSpan(ForegroundColorSpan(color), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (index < items.lastIndex) {
                    val sepStart = builder.length
                    builder.append(separator)
                    if (mode != Prefs.LAYOUT_VERTICAL) {
                        builder.setSpan(
                            ForegroundColorSpan(Color.argb(115, 148, 163, 184)),
                            sepStart,
                            builder.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            hudText?.text = builder
        }

        val allowGraph = Prefs.showHudGraph &&
            mode != Prefs.LAYOUT_MINI &&
            Prefs.hudStyle != Prefs.STYLE_PILL
        if (allowGraph) {
            val hist = synchronized(TelemetryHub.fpsHistory) { TelemetryHub.fpsHistory.toList() }
            val scaleMax = if (Prefs.graphMaxFps > 0) Prefs.graphMaxFps else data.displayRefreshHz
            sparklineView?.updateData(hist, accent, scaleMax)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FramePulse Pro Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val toggleLockIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_LOCK }
        val lockPending = PendingIntent.getService(
            this, 1, toggleLockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(openPending)
            .setSmallIcon(R.drawable.ic_tile_pulse)
            .setContentTitle("FramePulse Pro Aktif")
            .setContentText(if (isTouchLocked) "Mode tembus sentuhan aktif" else "Tap HUD untuk menu cepat, geser untuk pindah")
            .setOngoing(true)
            .addAction(0, if (isTouchLocked) "Unlock HUD" else "Lock Touch", lockPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        isTouchLocked = false
        syncQsTile()
        mainHandler.removeCallbacksAndMessages(null)
        monitor.stop()
        workerExecutor.shutdownNow()
        Thread { try { GameBooster.shutdownSafely() } catch (_: Throwable) {} }.start()
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        hudText = null
        sparklineView = null
        quickActionRow = null
        recBtnView = null
        rootContainer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
