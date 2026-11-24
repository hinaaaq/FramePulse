package com.shin.framepulse

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
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
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.shin.framepulse.START"
        const val ACTION_STOP = "com.shin.framepulse.STOP"
        const val ACTION_TOGGLE_LOCK = "com.shin.framepulse.TOGGLE_LOCK"
        const val ACTION_UPDATE_CONFIG = "com.shin.framepulse.UPDATE_CONFIG"
        const val ACTION_QUICK_BOOST = "com.shin.framepulse.QUICK_BOOST"
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
    private var contentWrapper: LinearLayout? = null
    private var accentBarView: View? = null
    private var statusDotView: View? = null
    private var deckHeaderText: TextView? = null
    private var deckDividerView: View? = null
    private var hudText: TextView? = null
    private var sparklineView: HudSparklineView? = null
    private var quickActionRow: LinearLayout? = null
    private var recBtnView: TextView? = null
    private var bgDrawable: GradientDrawable? = null
    private var accentBarDrawable: GradientDrawable? = null
    private var statusDotDrawable: GradientDrawable? = null

    private var lastTelemetry: TelemetryData? = null
    private var lastRenderedHash: Int = 0
    private var lastNotifUpdateMs: Long = 0L
    private var lastNotifText: String = ""
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
                            maybeRefreshNotification(data)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    isCollecting.set(false)
                    if (isRunning) {
                        mainHandler.postDelayed(this, effectiveIntervalMs())
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
            ACTION_QUICK_BOOST -> {
                if (isRunning) {
                    workerExecutor.execute {
                        val res = GameBooster.runQuickBoost(this)
                        mainHandler.post {
                            Toast.makeText(this, "Boost: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                if (isRunning) {
                    lastRenderedHash = 0
                    applyVisualConfig()
                val v110Density = if (Prefs.layoutMode == Prefs.LAYOUT_MINI) com.shin.framepulse.hud.HudDensityMode.COMPACT else com.shin.framepulse.hud.HudDensityMode.DETAILED
                com.shin.framepulse.v110.FramePulseV110Bridge(this).updateHudDensity(v110Density)
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
                val notification = buildNotification(lastTelemetry)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
                setupOverlayView()
                applyVisualConfig()
                monitor.start()
                syncQsTile()
                if (true && Prefs.turboAutoBoost) {
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
            cornerRadius = dp(11).toFloat()
        }

        accentBarDrawable = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(getThemeAccent())
        }

        statusDotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#10B981"))
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bgDrawable
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }

        // Sleek glowing accent indicator strip on left edge (Glass / Deck mode)
        accentBarView = View(this).apply {
            background = accentBarDrawable
            layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(7)
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        }

        // Compact glowing status dot (Pill mode)
        statusDotView = View(this).apply {
            background = statusDotDrawable
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                marginEnd = dp(6)
            }
            visibility = View.GONE
        }

        contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Deck mode micro header
        deckHeaderText = TextView(this).apply {
            textSize = 8f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            includeFontPadding = false
            text = "FRAMEPULSE • LIVE"
            visibility = View.GONE
        }

        deckDividerView = View(this).apply {
            setBackgroundColor(Color.argb(45, 148, 163, 184))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
            visibility = View.GONE
        }

        hudText = TextView(this).apply {
            typeface = resolveHudTypeface()
            fontFeatureSettings = "tnum"
            setTextColor(Color.WHITE)
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1.0f)
            text = "FPS --"
        }

        sparklineView = HudSparklineView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)
            ).apply {
                topMargin = dp(4)
            }
        }

        quickActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }

        fun makeActionChip(label: String, accentHex: String, onClick: () -> Unit): TextView {
            val col = Color.parseColor(accentHex)
            val chipBg = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
                setColor(Color.argb(200, 12, 20, 36))
                setStroke(dp(1), Color.argb(120, Color.red(col), Color.green(col), Color.blue(col)))
            }
            return TextView(this).apply {
                text = label
                textSize = 8.8f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(col)
                background = chipBg
                setPadding(dp(7), dp(3), dp(7), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(4)
                }
                setOnClickListener { onClick() }
            }
        }

        val boostBtn = makeActionChip("BST", "#10B981") {
            workerExecutor.execute {
                val res = GameBooster.runQuickBoost(this)
                mainHandler.post {
                    Toast.makeText(this, "Boost: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        recBtnView = makeActionChip(
            label = if (TelemetryHub.isRecording) "STOP" else "REC",
            accentHex = "#FBBF24"
        ) {
            if (TelemetryHub.isRecording) {
                TelemetryHub.stopRecording()
                val hist = synchronized(TelemetryHub.fpsHistory) { TelemetryHub.fpsHistory.toList() }.map { it.toFloat() }
                val avgFps = if (hist.isNotEmpty()) hist.average().toFloat() else (lastTelemetry?.fps?.toFloat() ?: 0f)
                val low1 = if (hist.isNotEmpty()) { val s = hist.sorted(); s[(s.size * 0.01).toInt().coerceIn(0, s.lastIndex)] } else avgFps
                val peakTemp = lastTelemetry?.batteryTempC ?: 0f
                com.shin.framepulse.v110.FramePulseV110Bridge(this).recordCompletedSession(
                    sessionId = "rec_${System.currentTimeMillis()}",
                    timestampMs = System.currentTimeMillis(),
                    gameOrLabel = "",
                    durationSec = hist.size,
                    avgFps = avgFps,
                    low1PercentFps = low1,
                    peakTempC = peakTemp,
                    thermalThrottleDetected = peakTemp >= 42f,
                    verified = lastTelemetry?.isTrueGameFps ?: false
                )
                recBtnView?.text = "REC"
            } else {
                TelemetryHub.startRecording()
                recBtnView?.text = "STOP"
            }
            lastRenderedHash = 0
            lastTelemetry?.let { renderTelemetry(it) }
        }

        val modeBtn = makeActionChip("MODE", "#A855F7") {
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

        val closeBtn = makeActionChip("✕", "#F43F5E") {
            stopSelf()
        }

        quickActionRow?.addView(boostBtn)
        quickActionRow?.addView(recBtnView)
        quickActionRow?.addView(modeBtn)
        quickActionRow?.addView(lockBtn)
        quickActionRow?.addView(closeBtn)

        contentWrapper?.addView(deckHeaderText)
        contentWrapper?.addView(deckDividerView)
        contentWrapper?.addView(hudText)
        contentWrapper?.addView(sparklineView)
        contentWrapper?.addView(quickActionRow)

        rootContainer?.addView(accentBarView)
        rootContainer?.addView(statusDotView)
        rootContainer?.addView(contentWrapper)

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
        var lastTapTimeMs = 0L
        var preMiniLayout = if (Prefs.layoutMode == Prefs.LAYOUT_MINI) Prefs.LAYOUT_HORIZONTAL else Prefs.layoutMode
        startAutoVisibilityWatcher()
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
                        val nowTap = System.currentTimeMillis()
                        if (nowTap - lastTapTimeMs < 320L) {
                            lastTapTimeMs = 0L
                            if (Prefs.layoutMode == Prefs.LAYOUT_MINI) {
                                Prefs.layoutMode = preMiniLayout
                                android.widget.Toast.makeText(this, "HUD: Mode Lengkap", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                preMiniLayout = Prefs.layoutMode
                                Prefs.layoutMode = Prefs.LAYOUT_MINI
                                android.widget.Toast.makeText(this, "HUD: Mode Mini (Double-Tap)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            val upd = android.content.Intent(this, OverlayService::class.java).apply { action = ACTION_UPDATE_CONFIG }
                            startService(upd)
                        } else {
                            lastTapTimeMs = nowTap
                            Prefs.showQuickButtons = !Prefs.showQuickButtons
                        recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"
                        quickActionRow?.visibility =
                            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE
                        }
                    }
                    true
                }
                else -> false
            }
        }

        clampPositionToScreen()
        windowManager.addView(rootContainer, layoutParams)
    }

    private fun resolveHudTypeface(): Typeface {
        val pro = true
        return when (Prefs.fontStyle) {
            Prefs.FONT_SPORT -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            Prefs.FONT_CLEAN -> if (pro) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            Prefs.FONT_TECH -> if (pro) Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            else -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
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

    private fun effectiveStyle(): String {
        val s = Prefs.hudStyle
        if (!true && (s == Prefs.STYLE_DECK || s == Prefs.STYLE_PILL)) {
            return Prefs.STYLE_GLASS
        }
        return s
    }

    private fun effectiveLayout(): String {
        val m = Prefs.layoutMode
        if (!true && m == Prefs.LAYOUT_VERTICAL) {
            return Prefs.LAYOUT_HORIZONTAL
        }
        return m
    }

    private fun effectiveIntervalMs(): Long {
        val ms = Prefs.updateIntervalMs
        return if (!true && ms < 500L) 500L else ms
    }

    private fun getThemeAccent(): Int {
        val mode = Prefs.themeMode
        val pro = true
        return when {
            mode == Prefs.THEME_EMERALD -> Color.parseColor("#10B981")
            pro && mode == Prefs.THEME_CRIMSON -> Color.parseColor("#F43F5E")
            pro && mode == Prefs.THEME_AMBER -> Color.parseColor("#FBBF24")
            pro && mode == Prefs.THEME_VIOLET -> Color.parseColor("#A855F7")
            else -> Color.parseColor("#38BDF8")
        }
    }

    private fun applyVisualConfig() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val style = effectiveStyle()
        val alphaVal = if (style == Prefs.STYLE_MINIMAL) 0 else Prefs.bgAlpha
        val accent = getThemeAccent()

        val topBg = Color.argb(alphaVal, 11, 18, 34)
        val botBg = Color.argb(alphaVal, 5, 9, 18)
        bgDrawable?.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        bgDrawable?.colors = intArrayOf(topBg, botBg)

        accentBarDrawable?.setColor(accent)

        when (style) {
            Prefs.STYLE_PILL -> {
                bgDrawable?.cornerRadius = dp(99).toFloat()
                rootContainer?.setPadding(dp(11), dp(5), dp(12), dp(5))
                accentBarView?.visibility = View.GONE
                statusDotView?.visibility = View.VISIBLE
                deckHeaderText?.visibility = View.GONE
                deckDividerView?.visibility = View.GONE
            }
            Prefs.STYLE_DECK -> {
                bgDrawable?.cornerRadius = dp(12).toFloat()
                rootContainer?.setPadding(dp(10), dp(7), dp(10), dp(7))
                accentBarView?.visibility = View.VISIBLE
                statusDotView?.visibility = View.GONE
                deckHeaderText?.visibility = View.VISIBLE
                deckDividerView?.visibility = View.VISIBLE
            }
            Prefs.STYLE_MINIMAL -> {
                bgDrawable?.cornerRadius = 0f
                rootContainer?.setPadding(dp(4), dp(2), dp(4), dp(2))
                accentBarView?.visibility = View.GONE
                statusDotView?.visibility = View.GONE
                deckHeaderText?.visibility = View.GONE
                deckDividerView?.visibility = View.GONE
            }
            else -> {
                // STYLE_GLASS
                bgDrawable?.cornerRadius = dp(11).toFloat()
                rootContainer?.setPadding(dp(9), dp(6), dp(10), dp(6))
                accentBarView?.visibility = if (alphaVal > 20) View.VISIBLE else View.GONE
                statusDotView?.visibility = View.GONE
                deckHeaderText?.visibility = View.GONE
                deckDividerView?.visibility = View.GONE
            }
        }

        if (alphaVal > 20) {
            val strokeAlpha = (alphaVal * 0.55f).toInt().coerceIn(0, 255)
            bgDrawable?.setStroke(
                dp(1),
                Color.argb(strokeAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
            hudText?.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            bgDrawable?.setStroke(0, Color.TRANSPARENT)
            hudText?.setShadowLayer(4.5f, 1.4f, 1.4f, Color.BLACK)
        }

        hudText?.typeface = resolveHudTypeface()
        hudText?.fontFeatureSettings = "tnum"
        hudText?.textSize = Prefs.textSizeSp
        recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"

        val allowGraph = true &&
            Prefs.showHudGraph &&
            effectiveLayout() != Prefs.LAYOUT_MINI &&
            style != Prefs.STYLE_PILL
        sparklineView?.visibility = if (allowGraph) View.VISIBLE else View.GONE
        quickActionRow?.visibility =
            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE
                        }

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
        refreshNotificationNow(lastTelemetry)
    }

    private fun renderTelemetry(data: TelemetryData) {
        val mode = effectiveLayout()
        val style = effectiveStyle()
        val accent = getThemeAccent()
        val recording = TelemetryHub.isRecording

        // Live status dot color in Pill mode
        statusDotDrawable?.setColor(
            when {
                recording -> Color.parseColor("#EF4444")
                data.isTrueGameFps -> Color.parseColor("#10B981")
                else -> Color.parseColor("#FBBF24")
            }
        )

        if (style == Prefs.STYLE_DECK) {
            val tag = when {
                recording -> "● REC"
                data.isTrueGameFps && data.activeSurfaceName.isNotBlank() -> data.activeSurfaceName.uppercase(Locale.US).take(14)
                data.isTrueGameFps -> "SURFACEFLINGER"
                else -> "SCREEN ${data.displayRefreshHz}HZ"
            }
            deckHeaderText?.text = "FP PRO • $tag"
            deckHeaderText?.setTextColor(if (recording) Color.parseColor("#F87171") else accent)
        }

        // Triple(microLabel, value, valueColor)
        val entries = mutableListOf<Triple<String, String, Int>>()

        val fpsColor = when {
            data.fps >= 50 -> Color.parseColor("#22C55E")
            data.fps >= 30 -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#EF4444")
        }

        if (recording && style != Prefs.STYLE_DECK) {
            entries.add(Triple("", "●REC", Color.parseColor("#EF4444")))
        }

        if (mode == Prefs.LAYOUT_MINI) {
            if (Prefs.showFps) entries.add(Triple("", "${if (data.isTrueGameFps) "" else "~"}${data.fps}FPS", fpsColor))
            if (Prefs.showTemp) {
                val tCol = if (data.batteryTempC >= 42f) Color.parseColor("#F87171") else accent
                entries.add(Triple("", "${monitor.formatFloat(data.batteryTempC, 1)}°", tCol))
            }
            if (Prefs.showCpu) entries.add(Triple("C ", "${data.cpuLoadPercent}%", Color.WHITE))
            if (true && Prefs.showGpu && data.gpuLoadPercent != null) entries.add(Triple("G ", "${data.gpuLoadPercent}%", Color.parseColor("#C084FC")))
            if (Prefs.showBatteryPct) entries.add(Triple("", "${data.batteryPercent}%", Color.parseColor("#34D399")))
        } else {
            if (Prefs.showFps) {
                val ft = if (Prefs.showFrameTime && data.frameTimeMs > 0f) " ${monitor.formatFloat(data.frameTimeMs, 1)}ms" else ""
                entries.add(Triple(if (data.isTrueGameFps) "FPS " else "~FPS ", "${data.fps}$ft", fpsColor))
            }
            if (Prefs.showCpu) {
                entries.add(Triple("CPU ", "${data.cpuLoadPercent}%", Color.parseColor("#F8FAFC")))
            }
            if (true && Prefs.showClock) {
                entries.add(Triple("CLK ", "${monitor.formatFloat(data.cpuMaxGhz, 2)}G", Color.parseColor("#CBD5E1")))
            }
            if (true && Prefs.showGpu && data.gpuLoadPercent != null) {
                entries.add(Triple("GPU ", "${data.gpuLoadPercent}%", Color.parseColor("#C084FC")))
            }
            if (true && Prefs.showRam) {
                entries.add(Triple("RAM ", "${monitor.formatFloat(data.ramUsedGb, 1)}G", Color.parseColor("#F472B6")))
            }
            if (Prefs.showTemp) {
                val tempColor = when {
                    data.batteryTempC >= 42f -> Color.parseColor("#F87171")
                    data.batteryTempC >= 38f -> Color.parseColor("#FBBF24")
                    else -> accent
                }
                entries.add(Triple("TMP ", "${monitor.formatFloat(data.batteryTempC, 1)}°C", tempColor))
            }
            if (Prefs.showBatteryPct) {
                entries.add(Triple("BAT ", "${data.batteryPercent}%", Color.parseColor("#34D399")))
            }
            if (true && Prefs.showWatt) {
                val wPrefix = if (data.isCharging) "+" else "-"
                entries.add(Triple("PWR ", "$wPrefix${monitor.formatFloat(data.powerWatts, 1)}W", if (data.isCharging) Color.parseColor("#34D399") else Color.parseColor("#FBBF24")))
            }
            if (true && Prefs.showNet) {
                entries.add(Triple("NET ", "↓${data.netDownKbps.toInt()}K", Color.parseColor("#34D399")))
            }
        }

        if (entries.isEmpty()) {
            entries.add(Triple(if (data.isTrueGameFps) "FPS " else "~FPS ", "${data.fps}", fpsColor))
        }

        val contentHash = entries.hashCode() * 31 + mode.hashCode() * 7 + style.hashCode()
        if (contentHash != lastRenderedHash) {
            lastRenderedHash = contentHash
            val labelColor = Color.parseColor("#94A3B8")
            val sepColor = Color.argb(95, 148, 163, 184)
            val separator = if (mode == Prefs.LAYOUT_VERTICAL) "\n" else "  ·  "
            val builder = SpannableStringBuilder()

            entries.forEachIndexed { index, (lbl, value, valColor) ->
                if (lbl.isNotEmpty()) {
                    val lStart = builder.length
                    builder.append(lbl)
                    builder.setSpan(ForegroundColorSpan(labelColor), lStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(RelativeSizeSpan(0.84f), lStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val vStart = builder.length
                builder.append(value)
                builder.setSpan(ForegroundColorSpan(valColor), vStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), vStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                if (index < entries.lastIndex) {
                    val sepStart = builder.length
                    builder.append(separator)
                    if (mode != Prefs.LAYOUT_VERTICAL) {
                        builder.setSpan(ForegroundColorSpan(sepColor), sepStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            hudText?.text = builder
        }

        val allowGraph = true &&
            Prefs.showHudGraph &&
            mode != Prefs.LAYOUT_MINI &&
            style != Prefs.STYLE_PILL
        if (allowGraph) {
            val hist = synchronized(TelemetryHub.fpsHistory) { TelemetryHub.fpsHistory.toList() }
            val scaleMax = if (Prefs.graphMaxFps > 0) Prefs.graphMaxFps else data.displayRefreshHz
            sparklineView?.updateData(hist, accent, scaleMax)
        }
    }

    private fun maybeRefreshNotification(data: TelemetryData) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifUpdateMs < 2000L) return
        val signature = "${data.fps}|${data.cpuLoadPercent}|${data.gpuLoadPercent}|${data.batteryTempC.toInt()}|$isTouchLocked|${TelemetryHub.isRecording}"
        if (signature == lastNotifText) return
        lastNotifUpdateMs = now
        lastNotifText = signature
        refreshNotificationNow(data)
    }

    private fun refreshNotificationNow(data: TelemetryData?) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(data))
        } catch (_: Throwable) {}
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FramePulse Pro HUD",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Status real-time dan kontrol cepat HUD melayang FramePulse"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(data: TelemetryData?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLockIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_LOCK }
        val lockPending = PendingIntent.getService(
            this, 1, toggleLockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val boostIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_QUICK_BOOST }
        val boostPending = PendingIntent.getService(
            this, 3, boostIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val str = AppStrings.forLang()
        val title = if (data != null && data.fps > 0) {
            val recPrefix = if (TelemetryHub.isRecording) "● REC • " else ""
            val src = if (data.isTrueGameFps) "SF" else "STD"
            "${recPrefix}FramePulse • ${data.fps} FPS ($src)"
        } else {
            str.notifTitleIdle
        }

        val subtitle = if (data != null) {
            val gpuPart = data.gpuLoadPercent?.let { " • GPU $it%" } ?: ""
            val lockPart = if (isTouchLocked) " • Locked" else ""
            "CPU ${data.cpuLoadPercent}%$gpuPart • ${monitor.formatFloat(data.batteryTempC, 1)}°C$lockPart"
        } else {
            if (isTouchLocked) str.notifSubLocked else str.notifSubUnlocked
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(openPending)
            .setSmallIcon(R.drawable.ic_tile_pulse)
            .setColor(getThemeAccent())
            .setContentTitle(title)
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .addAction(0, if (isTouchLocked) str.notifUnlock else str.notifLock, lockPending)
            .addAction(0, str.notifBoost, boostPending)
            .addAction(0, str.notifStop, stopPending)
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
        deckHeaderText = null
        deckDividerView = null
        accentBarView = null
        statusDotView = null
        sparklineView = null
        quickActionRow = null
        recBtnView = null
        contentWrapper = null
        rootContainer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var autoVisStarted = false
    private fun startAutoVisibilityWatcher() {
        if (autoVisStarted) return
        autoVisStarted = true
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                if (!isRunning) {
                    autoVisStarted = false
                    return
                }
                rootContainer?.let { container ->
                    if (Prefs.autoShowHudInGames) {
                        val pkg = TelemetryHub.activeForegroundPkg.orEmpty()
                        val isHomeOrSelf = pkg.isBlank() ||
                            pkg == "com.shin.framepulse" ||
                            pkg.contains("launcher", ignoreCase = true) ||
                            pkg.contains("trebuchet", ignoreCase = true) ||
                            pkg == "com.android.systemui"
                        container.visibility = if (isHomeOrSelf) View.GONE else View.VISIBLE
                    } else if (container.visibility != View.VISIBLE) {
                        container.visibility = View.VISIBLE
                    }
                }
                h.postDelayed(this, 1000L)
            }
        }
        h.postDelayed(r, 1000L)
    }
}
