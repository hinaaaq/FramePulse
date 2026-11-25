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
import android.os.PowerManager
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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.shin.framepulse.hud.HudDensityMode
import com.shin.framepulse.v110.FramePulseV110Bridge
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

        private const val COL_FPS_GOOD = 0xFF22C55E.toInt()
        private const val COL_FPS_MID = 0xFFFACC15.toInt()
        private const val COL_FPS_LOW = 0xFFEF4444.toInt()
        private const val COL_DOT_TRUE = 0xFF10B981.toInt()
        private const val COL_DOT_STD = 0xFFFBBF24.toInt()
        private const val COL_REC_ALERT = 0xFFF87171.toInt()
        private const val COL_TEXT_PRIMARY = 0xFFF8FAFC.toInt()
        private const val COL_TEXT_SUB = 0xFF94A3B8.toInt()
        private const val COL_CLOCK = 0xFFCBD5E1.toInt()
        private const val COL_GPU = 0xFFC084FC.toInt()
        private const val COL_RAM = 0xFFF472B6.toInt()
        private const val COL_EMERALD = 0xFF34D399.toInt()

        @Volatile
        private var activeInstance: OverlayService? = null

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

    private var cachedScreenW: Int = 0
    private var cachedScreenH: Int = 0
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
            if (!isRunning || activeInstance !== this@OverlayService) return
            if (!isCollecting.compareAndSet(false, true)) {
                mainHandler.postDelayed(this, 100L)
                return
            }
            workerExecutor.execute {
                try {
                    val data = monitor.collect()
                    lastTelemetry = data
                    mainHandler.post {
                        if (isRunning && activeInstance === this@OverlayService) {
                            renderTelemetry(data)
                            maybeRefreshNotification(data)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    isCollecting.set(false)
                    if (isRunning && activeInstance === this@OverlayService) {
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
        refreshCachedScreenBounds()
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

    private fun safeStartForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopGracefullyBeforeSetup() {
        runCatching { safeStartForeground(buildNotification(null)) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isRunning && activeInstance === this) {
            mainHandler.post {
                refreshCachedScreenBounds()
                val land = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                layoutParams.x = Prefs.getPosX(land)
                layoutParams.y = Prefs.getPosY(land)
                clampPositionToScreen()
                rootContainer?.let {
                    if (it.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(it, layoutParams)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (!isRunning) {
                    stopGracefullyBeforeSetup()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_LOCK -> {
                if (isRunning && activeInstance === this) {
                    setTouchLocked(!isTouchLocked)
                    return START_STICKY
                }
                stopGracefullyBeforeSetup()
                return START_NOT_STICKY
            }
            ACTION_QUICK_BOOST -> {
                if (isRunning && activeInstance === this) {
                    workerExecutor.execute {
                        val res = GameBooster.runQuickBoost(this)
                        mainHandler.post {
                            Toast.makeText(this, "Boost: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return START_STICKY
                }
                stopGracefullyBeforeSetup()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                if (isRunning && activeInstance === this) {
                    refreshVisualStateInProcess()
                    return START_STICKY
                }
                stopGracefullyBeforeSetup()
                return START_NOT_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopGracefullyBeforeSetup()
            return START_NOT_STICKY
        }

        if (!isRunning || activeInstance !== this) {
            try {
                val notification = buildNotification(lastTelemetry)
                safeStartForeground(notification)
                setupOverlayView()
                activeInstance = this
                isRunning = true
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
                if (activeInstance === this) {
                    activeInstance = null
                    isRunning = false
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun refreshVisualStateInProcess() {
        lastRenderedHash = 0
        applyVisualConfig()
        val v110Density = if (Prefs.layoutMode == Prefs.LAYOUT_MINI) HudDensityMode.COMPACT else HudDensityMode.DETAILED
        workerExecutor.execute {
            runCatching { FramePulseV110Bridge(this).updateHudDensity(v110Density) }
        }
        lastTelemetry?.let { renderTelemetry(it) }
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
            setColor(COL_DOT_TRUE)
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bgDrawable
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }

        accentBarView = View(this).apply {
            background = accentBarDrawable
            layoutParams = LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginEnd = dp(7)
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        }

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

        deckHeaderText = TextView(this).apply {
            textSize = 8f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(COL_TEXT_SUB)
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
                dp(26)
            ).apply {
                topMargin = dp(4)
            }
        }

        quickActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }

        fun makeActionChip(label: String, col: Int, onClick: () -> Unit): TextView {
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

        val boostBtn = makeActionChip("BST", COL_DOT_TRUE) {
            workerExecutor.execute {
                val res = GameBooster.runQuickBoost(this)
                mainHandler.post {
                    Toast.makeText(this, "Boost: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        recBtnView = makeActionChip(
            label = if (TelemetryHub.isRecording) "STOP" else "REC",
            col = COL_DOT_STD
        ) {
            if (TelemetryHub.isRecording) {
                val summary = TelemetryHub.stopRecording()
                if (summary != null) {
                    val gamePkg = GameBooster.resolveTargetGamePackage().substringAfterLast('.')
                    workerExecutor.execute {
                        runCatching {
                            FramePulseV110Bridge(this).recordCompletedSession(
                                sessionId = "rec_${System.currentTimeMillis()}",
                                timestampMs = System.currentTimeMillis(),
                                gameOrLabel = gamePkg,
                                durationSec = summary.durationSec,
                                avgFps = summary.avgFps,
                                low1PercentFps = summary.onePercentLowFps.toFloat(),
                                peakTempC = summary.peakTempC,
                                thermalThrottleDetected = summary.peakTempC >= 42f,
                                verified = summary.sourceLabel == "SF"
                            )
                        }
                    }
                }
                recBtnView?.text = "REC"
            } else {
                TelemetryHub.startRecording()
                recBtnView?.text = "STOP"
            }
            lastRenderedHash = 0
            lastTelemetry?.let { renderTelemetry(it) }
        }

        val modeBtn = makeActionChip("MODE", 0xFFA855F7.toInt()) {
            Prefs.layoutMode = when (Prefs.layoutMode) {
                Prefs.LAYOUT_HORIZONTAL -> Prefs.LAYOUT_VERTICAL
                Prefs.LAYOUT_VERTICAL -> Prefs.LAYOUT_MINI
                else -> Prefs.LAYOUT_HORIZONTAL
            }
            refreshVisualStateInProcess()
        }

        val lockBtn = makeActionChip("LOCK", 0xFF38BDF8.toInt()) {
            setTouchLocked(true)
        }

        val closeBtn = makeActionChip("✕", 0xFFF43F5E.toInt()) {
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
                    refreshCachedScreenBounds()
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
                    rootContainer?.let {
                        if (it.isAttachedToWindow) {
                            try {
                                windowManager.updateViewLayout(it, layoutParams)
                            } catch (_: Exception) {}
                        }
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
                        rootContainer?.let {
                            if (it.isAttachedToWindow) {
                                try {
                                    windowManager.updateViewLayout(it, layoutParams)
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        val nowTap = System.currentTimeMillis()
                        if (nowTap - lastTapTimeMs < 320L) {
                            rootContainer?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            lastTapTimeMs = 0L
                            val isId = AppStrings.resolveLangCode() == "id"
                            if (Prefs.layoutMode == Prefs.LAYOUT_MINI) {
                                Prefs.layoutMode = preMiniLayout
                                Toast.makeText(
                                    this,
                                    if (isId) "HUD: Mode Lengkap" else "HUD: Full Mode",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                preMiniLayout = Prefs.layoutMode
                                Prefs.layoutMode = Prefs.LAYOUT_MINI
                                Toast.makeText(
                                    this,
                                    if (isId) "HUD: Mode Mini (Double-Tap)" else "HUD: Mini Mode (Double-Tap)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            refreshVisualStateInProcess()
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

        refreshCachedScreenBounds()
        clampPositionToScreen()
        windowManager.addView(rootContainer, layoutParams)
    }

    private fun resolveHudTypeface(): Typeface {
        return when (Prefs.fontStyle) {
            Prefs.FONT_SPORT -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            Prefs.FONT_CLEAN -> Typeface.create("sans-serif", Typeface.BOLD)
            Prefs.FONT_TECH -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            else -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    private fun refreshCachedScreenBounds() {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
        cachedScreenW = w.coerceAtLeast(1)
        cachedScreenH = h.coerceAtLeast(1)
    }

    private fun getScreenBoundsPx(): Pair<Int, Int> {
        if (cachedScreenW <= 1 || cachedScreenH <= 1) {
            refreshCachedScreenBounds()
        }
        return cachedScreenW to cachedScreenH
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

    private fun effectiveStyle(): String = Prefs.hudStyle

    private fun effectiveLayout(): String = Prefs.layoutMode

    private fun effectiveIntervalMs(): Long = Prefs.updateIntervalMs.coerceIn(250L, 2000L)

    private fun getThemeAccent(): Int {
        val mode = Prefs.themeMode
        val pro = ProLicenseManager.isProUnlocked() || ProLicenseManager.isOwnerEmail(Prefs.linkedGoogleEmail)
        return when {
            mode == Prefs.THEME_EMERALD -> 0xFF10B981.toInt()
            pro && mode == Prefs.THEME_CRIMSON -> 0xFFF43F5E.toInt()
            pro && mode == Prefs.THEME_AMBER -> 0xFFFBBF24.toInt()
            pro && mode == Prefs.THEME_VIOLET -> 0xFFA855F7.toInt()
            else -> 0xFF38BDF8.toInt()
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

        val allowGraph = Prefs.showHudGraph &&
            effectiveLayout() != Prefs.LAYOUT_MINI &&
            style != Prefs.STYLE_PILL
        sparklineView?.visibility = if (allowGraph) View.VISIBLE else View.GONE
        quickActionRow?.visibility =
            if (Prefs.showQuickButtons && !isTouchLocked) View.VISIBLE else View.GONE

        refreshCachedScreenBounds()
        val land = isLandscape()
        layoutParams.x = Prefs.getPosX(land)
        layoutParams.y = Prefs.getPosY(land)
        clampPositionToScreen()
        rootContainer?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(it, layoutParams)
                } catch (_: Exception) {}
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
            val isId = AppStrings.resolveLangCode() == "id"
            Toast.makeText(
                this,
                if (isId) "HUD dikunci (Touch-Through). Buka lewat Notifikasi / Quick Tile / App."
                else "HUD locked (Touch-Through). Unlock via Notification / Quick Tile / App.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            recBtnView?.text = if (TelemetryHub.isRecording) "STOP" else "REC"
            quickActionRow?.visibility = if (Prefs.showQuickButtons) View.VISIBLE else View.GONE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            layoutParams.alpha = 1.0f
        }
        rootContainer?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(it, layoutParams)
                } catch (_: Exception) {}
            }
        }
        refreshNotificationNow(lastTelemetry)
    }

    private fun renderTelemetry(data: TelemetryData) {
        val mode = effectiveLayout()
        val style = effectiveStyle()
        val accent = getThemeAccent()
        val recording = TelemetryHub.isRecording

        statusDotDrawable?.setColor(
            when {
                recording -> COL_FPS_LOW
                data.isTrueGameFps -> COL_DOT_TRUE
                else -> COL_DOT_STD
            }
        )

        if (style == Prefs.STYLE_DECK) {
            val tag = when {
                recording -> "● REC"
                data.isTrueGameFps && data.activeSurfaceName.isNotBlank() -> data.activeSurfaceName.uppercase(Locale.US).take(14)
                data.isTrueGameFps -> "SURFACEFLINGER"
                else -> "SCREEN ${data.displayRefreshHz}HZ"
            }
            deckHeaderText?.text = "FRAMEPULSE • $tag"
            deckHeaderText?.setTextColor(if (recording) COL_REC_ALERT else accent)
        }

        val entries = ArrayList<Triple<String, String, Int>>(10)

        val fpsColor = when {
            data.fps >= 50 -> COL_FPS_GOOD
            data.fps >= 30 -> COL_FPS_MID
            else -> COL_FPS_LOW
        }

        if (recording && style != Prefs.STYLE_DECK) {
            entries.add(Triple("", "●REC", COL_FPS_LOW))
        }

        if (mode == Prefs.LAYOUT_MINI) {
            if (Prefs.showFps) entries.add(Triple("", "${if (data.isTrueGameFps) "" else "~"}${data.fps}FPS", fpsColor))
            if (Prefs.showTemp) {
                val tCol = if (data.batteryTempC >= 42f) COL_REC_ALERT else accent
                entries.add(Triple("", "${monitor.formatFloat(data.batteryTempC, 1)}°", tCol))
            }
            if (Prefs.showCpu) entries.add(Triple("C ", "${data.cpuLoadPercent}%", Color.WHITE))
            if (Prefs.showGpu && data.gpuLoadPercent != null) entries.add(Triple("G ", "${data.gpuLoadPercent}%", COL_GPU))
            if (Prefs.showBatteryPct) entries.add(Triple("", "${data.batteryPercent}%", COL_EMERALD))
        } else {
            if (Prefs.showFps) {
                val lowPart = if (data.isTrueGameFps && data.onePercentLowFps > 0 && Prefs.showFrameTime) {
                    " (${data.onePercentLowFps})"
                } else ""
                val ft = if (Prefs.showFrameTime && data.frameTimeMs > 0f) " ${monitor.formatFloat(data.frameTimeMs, 1)}ms" else ""
                entries.add(Triple(if (data.isTrueGameFps) "FPS " else "~FPS ", "${data.fps}$lowPart$ft", fpsColor))
            }
            if (Prefs.showCpu) {
                entries.add(Triple("CPU ", "${data.cpuLoadPercent}%", COL_TEXT_PRIMARY))
            }
            if (Prefs.showClock) {
                entries.add(Triple("CLK ", "${monitor.formatFloat(data.cpuMaxGhz, 2)}G", COL_CLOCK))
            }
            if (Prefs.showGpu && data.gpuLoadPercent != null) {
                entries.add(Triple("GPU ", "${data.gpuLoadPercent}%", COL_GPU))
            }
            if (Prefs.showRam) {
                entries.add(Triple("RAM ", "${monitor.formatFloat(data.ramUsedGb, 1)}G", COL_RAM))
            }
            if (Prefs.showTemp) {
                val tempColor = when {
                    data.batteryTempC >= 42f || data.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> COL_REC_ALERT
                    data.batteryTempC >= 38f || data.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> COL_DOT_STD
                    else -> accent
                }
                entries.add(Triple("TMP ", "${monitor.formatFloat(data.batteryTempC, 1)}°C", tempColor))
            }
            if (Prefs.showBatteryPct) {
                entries.add(Triple("BAT ", "${data.batteryPercent}%", COL_EMERALD))
            }
            if (Prefs.showWatt) {
                val wPrefix = if (data.isCharging) "+" else "-"
                entries.add(Triple("PWR ", "$wPrefix${monitor.formatFloat(data.powerWatts, 1)}W", if (data.isCharging) COL_EMERALD else COL_DOT_STD))
            }
            if (Prefs.showNet) {
                entries.add(Triple("NET ", "↓${data.netDownKbps.toInt()}K", COL_EMERALD))
            }
        }

        if (entries.isEmpty()) {
            entries.add(Triple(if (data.isTrueGameFps) "FPS " else "~FPS ", "${data.fps}", fpsColor))
        }

        val contentHash = entries.hashCode() * 31 + mode.hashCode() * 7 + style.hashCode()
        if (contentHash != lastRenderedHash) {
            lastRenderedHash = contentHash
            val sepColor = Color.argb(95, 148, 163, 184)
            val separator = if (mode == Prefs.LAYOUT_VERTICAL) "\n" else "  ·  "
            val builder = SpannableStringBuilder()

            entries.forEachIndexed { index, (lbl, value, valColor) ->
                if (lbl.isNotEmpty()) {
                    val lStart = builder.length
                    builder.append(lbl)
                    builder.setSpan(ForegroundColorSpan(COL_TEXT_SUB), lStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

        val allowGraph = Prefs.showHudGraph &&
            mode != Prefs.LAYOUT_MINI &&
            style != Prefs.STYLE_PILL
        if (allowGraph) {
            val hist = TelemetryHub.getFpsHistorySnapshot()
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
            "FramePulse HUD",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Real-time floating HUD status & quick controls"
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
        val wasActive = (activeInstance === this)
        if (wasActive) {
            activeInstance = null
            isRunning = false
            isTouchLocked = false
            syncQsTile()
            Thread { try { GameBooster.shutdownSafely() } catch (_: Throwable) {} }.start()
        }
        mainHandler.removeCallbacksAndMessages(null)
        monitor.stop()
        workerExecutor.shutdownNow()
        rootContainer?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {}
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

    private var lastThermalWarnMs = 0L
    private var thermalPulseToggle = false
    private var autoVisStarted = false

    private fun startAutoVisibilityWatcher() {
        if (autoVisStarted) return
        autoVisStarted = true
        val r = object : Runnable {
            override fun run() {
                if (!isRunning || activeInstance !== this@OverlayService) {
                    autoVisStarted = false
                    return
                }
                rootContainer?.let { container ->
                    if (Prefs.autoShowHudInGames && monitor.isShizukuReady()) {
                        val pkg = TelemetryHub.activeForegroundPkg.orEmpty()
                        val hasGameSurface = TelemetryHub.latest.isTrueGameFps &&
                            TelemetryHub.activeLockedLayer?.contains("SurfaceView") == true
                        val shouldShow = if (pkg.isBlank()) {
                            hasGameSurface
                        } else {
                            !GameBooster.isSystemOrSelfPkg(pkg) &&
                                (hasGameSurface || TelemetryHub.isLikelyGamePackage(this@OverlayService, pkg))
                        }
                        val desiredVis = if (shouldShow) View.VISIBLE else View.GONE
                        if (container.visibility != desiredVis) {
                            container.visibility = desiredVis
                        }
                    } else if (container.visibility != View.VISIBLE) {
                        container.visibility = View.VISIBLE
                    }
                }

                val latest = TelemetryHub.latest
                val isHot = latest.batteryTempC >= 42.0f ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && latest.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE)

                if (Prefs.thermalWarningEnabled && isHot) {
                    thermalPulseToggle = !thermalPulseToggle
                    rootContainer?.alpha = if (thermalPulseToggle) 0.74f else 1.0f
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastThermalWarnMs > 60_000L) {
                        lastThermalWarnMs = nowMs
                        val tStr = String.format(Locale.US, "%.1f", latest.batteryTempC)
                        val isId = AppStrings.resolveLangCode() == "id"
                        Toast.makeText(
                            this@OverlayService,
                            if (isId) "Peringatan Suhu: ${tStr}°C! Hindari throttling"
                            else "Thermal Warning: ${tStr}°C! Watch for throttling",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (rootContainer?.alpha != 1.0f) {
                    rootContainer?.alpha = 1.0f
                }

                mainHandler.postDelayed(this, 1000L)
            }
        }
        mainHandler.postDelayed(r, 1000L)
    }
}
