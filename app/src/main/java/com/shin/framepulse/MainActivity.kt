package com.shin.framepulse

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku: True Game FPS & SELinux Sensors ready!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {}

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CyberCyan,
                    background = BgObsidian,
                    surface = CardSurface
                ),
                typography = FramePulseTypography
            ) {
                FramePulseProApp(
                    onRequestNotif = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}

@Composable
fun FramePulseProApp(onRequestNotif: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var langPref by remember { mutableStateOf(Prefs.appLanguage) }
    val str = remember(langPref) { AppStrings.forLang(langPref) }

    var proStatus by remember { mutableStateOf(ProLicenseManager.getStatus(forceRefresh = true)) }
    val isPro = proStatus.isPro
    var showProDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) { availableUpdate = UpdateChecker.checkLatest(context) }

    var isAppForeground by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
            var showHomePresets by remember { mutableStateOf(false) }
            var showHomeLeaderboard by remember { mutableStateOf(false) }
            var showHomeThemes by remember { mutableStateOf(false) }
            var showHomeAbout by remember { mutableStateOf(false) }
            val globalOwnerFlag = ProLicenseManager.isOwnerEmail(Prefs.linkedGoogleEmail)
            val globalProFlag = ProLicenseManager.isProUnlocked() || globalOwnerFlag
            var activeAppTheme by remember(globalProFlag, globalOwnerFlag) {
                mutableStateOf(AppThemeManager.loadTheme(context, globalProFlag, globalOwnerFlag))
            }
    var telemetry by remember { mutableStateOf(TelemetryHub.latest) }
    var fpsHistory by remember { mutableStateOf<List<Int>>(emptyList()) }

    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var serviceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var touchLocked by remember { mutableStateOf(OverlayService.isTouchLocked) }
    var shizukuBinderAlive by remember { mutableStateOf(false) }
    var shizukuConnected by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(TelemetryHub.isRecording) }
    var recElapsedSec by remember { mutableIntStateOf(TelemetryHub.getRecordingElapsedSec()) }
    var benchmarkHistory by remember { mutableStateOf(TelemetryHub.loadHistory()) }
    var lastCrashLog by remember { mutableStateOf(Prefs.lastCrashLog) }

    var turboAutoBoost by remember { mutableStateOf(Prefs.turboAutoBoost) }
    var turboFixedPerf by remember { mutableStateOf(Prefs.turboFixedPerf) }
    var thermalLimitC by remember { mutableFloatStateOf(Prefs.turboThermalLimitC) }
    var fixedPerfLive by remember { mutableStateOf(GameBooster.isFixedPerfActive) }
    var thermalCooldown by remember { mutableStateOf(GameBooster.isThermalCooldown) }
    var boostStatusText by remember { mutableStateOf(GameBooster.lastBoostSummary) }
    var isBoosting by remember { mutableStateOf(false) }
    var isCompilingArt by remember { mutableStateOf(false) }
    var lastGamePkg by remember { mutableStateOf(Prefs.lastGamePackage) }

    var layoutMode by remember { mutableStateOf(Prefs.layoutMode) }
    var hudStyle by remember { mutableStateOf(Prefs.hudStyle) }
    var fontStyle by remember { mutableStateOf(Prefs.fontStyle) }
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var bgAlpha by remember { mutableIntStateOf(Prefs.bgAlpha) }
    var textSizeSp by remember { mutableFloatStateOf(Prefs.textSizeSp) }
    var intervalMs by remember { mutableLongStateOf(Prefs.updateIntervalMs) }
    var graphMaxFps by remember { mutableIntStateOf(Prefs.graphMaxFps) }
    var showHudGraph by remember { mutableStateOf(Prefs.showHudGraph) }
    var snapToEdge by remember { mutableStateOf(Prefs.snapToEdge) }

    var sFps by remember { mutableStateOf(Prefs.showFps) }
    var sFrameTime by remember { mutableStateOf(Prefs.showFrameTime) }
    var sCpu by remember { mutableStateOf(Prefs.showCpu) }
    var sClock by remember { mutableStateOf(Prefs.showClock) }
    var sGpu by remember { mutableStateOf(Prefs.showGpu) }
    var sRam by remember { mutableStateOf(Prefs.showRam) }
    var sTemp by remember { mutableStateOf(Prefs.showTemp) }
    var sBatt by remember { mutableStateOf(Prefs.showBatteryPct) }
    var sWatt by remember { mutableStateOf(Prefs.showWatt) }
    var sNet by remember { mutableStateOf(Prefs.showNet) }

            val activeAccent = activeAppTheme.accent

    fun pushOverlayUpdate() {
        if (OverlayService.isRunning) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_CONFIG
            })
        }
    }

    fun requirePro(action: () -> Unit) {
        if (isPro) {
            action()
        } else {
            showProDialog = true
        }
    }

    val localMonitor = remember { SystemMonitor(context) }
    val uiScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isAppForeground = true
                    TelemetryHub.isAppForeground = true
                    if (!OverlayService.isRunning) localMonitor.start()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppForeground = true
                    TelemetryHub.isAppForeground = true
                    overlayGranted = Settings.canDrawOverlays(context)
                    serviceRunning = OverlayService.isRunning
                    touchLocked = OverlayService.isTouchLocked
                    shizukuBinderAlive = localMonitor.isShizukuBinderAlive()
                    shizukuConnected = localMonitor.isShizukuReady()
                    benchmarkHistory = TelemetryHub.loadHistory()
                    lastCrashLog = Prefs.lastCrashLog
                    proStatus = ProLicenseManager.getStatus(forceRefresh = true)
                    if (Prefs.linkedGoogleEmail.isNotBlank() &&
                        ProLicenseManager.isCloudConfigured() &&
                        proStatus.state != LicenseState.PRO_ACTIVE
                    ) {
                        uiScope.launch {
                            val syncRes = withContext(Dispatchers.IO) {
                                ProLicenseManager.syncWithCloud(context)
                            }
                            if (syncRes is ProLicenseManager.CloudSyncResult.Activated) {
                                proStatus = ProLicenseManager.getStatus(forceRefresh = true)
                                pushOverlayUpdate()
                                Toast.makeText(context, "${str.proKeySuccess} (${syncRes.expiryLabel})", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    if (OverlayService.isRunning) {
                        if (localMonitor.isRunning()) localMonitor.stop()
                    } else {
                        localMonitor.start()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppForeground = false
                    TelemetryHub.isAppForeground = false
                    localMonitor.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            localMonitor.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAppForeground) {
        while (isAppForeground) {
            val running = OverlayService.isRunning
            if (running && localMonitor.isRunning()) localMonitor.stop()
            val data = if (running) {
                TelemetryHub.latest
            } else {
                withContext(Dispatchers.IO) { try { localMonitor.collect() } catch (_: Throwable) { TelemetryHub.latest } }
            }
            val prevRec = isRecording
            val nowRec = TelemetryHub.isRecording
            if (prevRec && !nowRec) {
                benchmarkHistory = TelemetryHub.loadHistory()
            }
            telemetry = data
            fpsHistory = synchronized(TelemetryHub.fpsHistory) { TelemetryHub.fpsHistory.toList() }
            serviceRunning = running
            touchLocked = OverlayService.isTouchLocked
            isRecording = nowRec
            recElapsedSec = TelemetryHub.getRecordingElapsedSec()
            shizukuBinderAlive = localMonitor.isShizukuBinderAlive()
            shizukuConnected = localMonitor.isShizukuReady()
            fixedPerfLive = GameBooster.isFixedPerfActive
            thermalCooldown = GameBooster.isThermalCooldown
            if (GameBooster.lastBoostSummary.isNotBlank()) boostStatusText = GameBooster.lastBoostSummary
            val latestPkg = Prefs.lastGamePackage
            if (latestPkg.isNotBlank()) lastGamePkg = latestPkg
            delay(500L)
        }
    }

    val effectiveGraphMax = if (graphMaxFps > 0) graphMaxFps else telemetry.displayRefreshHz

    if (showProDialog) {
        ProSupporterDialog(
            str = str,
            proStatus = proStatus,
            accent = activeAccent,
            onDismiss = { showProDialog = false },
            onStatusChanged = { newStatus ->
                proStatus = newStatus
                pushOverlayUpdate()
            }
        )
    }

    Scaffold(
        containerColor = activeAppTheme.bgDark,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(activeAppTheme.cardBg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FramePulseLogoMark(accent = activeAccent)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "FramePulse",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                )
                                Spacer(Modifier.width(6.dp))
                                val badgeCol = if (isPro) activeAccent else AmberVolt
                                val badgeTxt = when {
                                    proStatus.tierLabel == "OWNER" -> "OWNER"
                                    proStatus.state == LicenseState.PRO_ACTIVE -> "PRO"
                                    proStatus.state == LicenseState.TRIAL_ACTIVE -> "TRIAL"
                                    else -> "FREE • PRO"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeCol.copy(alpha = 0.18f))
                                        .border(1.dp, badgeCol.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable { showProDialog = true }
                                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                ) {
                                    Text(badgeTxt, color = badgeCol, fontSize = 8.8.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            val engineText = when {
                                telemetry.isTrueGameFps && telemetry.activeSurfaceName.isNotBlank() -> "SF • ${telemetry.activeSurfaceName}"
                                shizukuConnected -> "Shizuku • ${telemetry.displayRefreshHz}Hz"
                                else -> "Standard • ${telemetry.displayRefreshHz}Hz"
                            }
                            StatusDotPill(
                                label = engineText,
                                color = if (shizukuConnected) EmeraldNeon else TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, str.needOverlayPerm, Toast.LENGTH_SHORT).show()
                                selectedTab = 3
                                return@Button
                            }
                            onRequestNotif()
                            if (OverlayService.isRunning) {
                                context.startService(Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_STOP
                                })
                                serviceRunning = false
                                localMonitor.start()
                            } else {
                                localMonitor.stop()
                                ContextCompat.startForegroundService(
                                    context,
                                    Intent(context, OverlayService::class.java).apply {
                                        action = OverlayService.ACTION_START
                                    }
                                )
                                serviceRunning = true
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serviceRunning) CrimsonAccent else activeAppTheme.accent
                        ),
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (serviceRunning) str.stopHud else str.startHud,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
                HorizontalDivider(color = CardBorder.copy(alpha = 0.65f))
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(activeAppTheme.cardBg)) {
                HorizontalDivider(color = CardBorder.copy(alpha = 0.65f))
                NavigationBar(
                    containerColor = activeAppTheme.cardBg,
                    tonalElevation = 0.dp
                ) {
                    val tabs = listOf(str.tabMonitor, str.tabStudio, str.tabSensors, str.tabSetup)
                    tabs.forEachIndexed { idx, label ->
                        val selected = selectedTab == idx
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = idx },
                            icon = {
                                NavVectorIcon(
                                    index = idx,
                                    tint = if (selected) activeAccent else TextSecondary,
                                    selected = selected
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeAccent,
                                selectedTextColor = activeAccent,
                                indicatorColor = activeAccent.copy(alpha = 0.14f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            when (selectedTab) {
                    0 -> {
                        item {
                            availableUpdate?.let { upd ->
                                UpdateAvailableBanner(
                                    info = upd,
                                    accent = activeAccent,
                                    onDismiss = { availableUpdate = null }
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            val homeEmail = Prefs.linkedGoogleEmail
                            val ownerFlag = ProLicenseManager.isOwnerEmail(homeEmail)
                            val proFlag = ProLicenseManager.isProUnlocked() || ownerFlag
                            ProHomeCommandHub(
                                theme = activeAppTheme,
                                isPro = proFlag,
                                isOwner = ownerFlag,
                                onOpenPresets = { showHomePresets = true },
                                onOpenLeaderboard = { showHomeLeaderboard = true },
                                onOpenThemes = { showHomeThemes = true },
                                onOpenAbout = { showHomeAbout = true }
                            )
                            if (showHomePresets) {
                                GamePresetDialog(isPro = proFlag, onDismiss = { showHomePresets = false })
                            }
                            if (showHomeLeaderboard) {
                                LeaderboardDialog(email = homeEmail, isPro = proFlag, isOwner = ownerFlag, onDismiss = { showHomeLeaderboard = false })
                            }
                            if (showHomeThemes) {
                                ThemePickerDialog(
                                    currentTheme = activeAppTheme,
                                    isPro = proFlag,
                                    isOwner = ownerFlag,
                                    onSelect = { activeAppTheme = it },
                                    onDismiss = { showHomeThemes = false }
                                )
                            }
                            if (showHomeAbout) {
                                AboutAppDialog(isOwner = ownerFlag, onDismiss = { showHomeAbout = false })
                            }
                        }
                    item {
                        CyberCard(
                            borderColor = activeAccent.copy(alpha = 0.45f),
                            accentGlow = activeAccent
                        ) {
                            SectionTitle(
                                title = str.realTimeFramePacing,
                                badge = if (telemetry.isTrueGameFps) "SURFACEFLINGER" else "CHOREOGRAPHER",
                                accent = activeAccent,
                                iconType = "pulse"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${telemetry.fps}",
                                        color = when {
                                            telemetry.fps >= 50 -> EmeraldNeon
                                            telemetry.fps >= 30 -> AmberVolt
                                            else -> CrimsonAccent
                                        },
                                        fontSize = 52.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    Text(
                                        text = " FPS",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 11.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs),
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    Text(
                                        text = "${str.displayLabel} ${telemetry.displayRefreshHz}Hz • Cap $effectiveGraphMax",
                                        color = TextSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            ComposeSparkline(
                                points = fpsHistory,
                                accent = activeAccent,
                                maxScaleFps = effectiveGraphMax
                            )
                        }
                    }

                    item {
                        val turboBadge = when {
                            thermalCooldown -> "COOLDOWN"
                            fixedPerfLive -> "PERF HAL ON"
                            turboAutoBoost && isPro -> "AUTO READY"
                            else -> "SHIZUKU"
                        }
                        val turboBadgeColor = when {
                            thermalCooldown -> AmberVolt
                            fixedPerfLive -> EmeraldNeon
                            else -> activeAccent
                        }
                        CyberCard(
                            borderColor = if (fixedPerfLive) EmeraldNeon.copy(alpha = 0.55f) else CardBorder,
                            accentGlow = if (fixedPerfLive) EmeraldNeon else null
                        ) {
                            SectionTitle(str.gameTurboEngine, turboBadge, turboBadgeColor, iconType = "bolt")
                            val targetShort = lastGamePkg.substringAfterLast('.').ifBlank { str.autoWhilePlaying }
                            Text(
                                text = "${str.targetGame}: $targetShort • ${str.thermalGuard}: ${thermalLimitC.roundToInt()}°C",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (boostStatusText.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EmeraldNeon.copy(alpha = 0.10f))
                                        .border(1.dp, EmeraldNeon.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = boostStatusText,
                                        color = EmeraldNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !isBoosting,
                                    onClick = {
                                        isBoosting = true
                                        uiScope.launch {
                                            val res = withContext(Dispatchers.IO) {
                                                GameBooster.runQuickBoost(
                                                    context = context,
                                                    enableFixedPerf = isPro && Prefs.turboFixedPerf
                                                )
                                            }
                                            boostStatusText = res.summaryMessage
                                            fixedPerfLive = GameBooster.isFixedPerfActive
                                            thermalCooldown = GameBooster.isThermalCooldown
                                            isBoosting = false
                                            Toast.makeText(context, res.summaryMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(11.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = activeAppTheme.accent)
                                ) {
                                    Text(
                                        text = if (isBoosting) str.boosting else str.boostNow,
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Button(
                                    enabled = !isCompilingArt,
                                    onClick = {
                                        requirePro {
                                            isCompilingArt = true
                                            uiScope.launch {
                                                val msg = withContext(Dispatchers.IO) {
                                                    GameBooster.optimizeGameArtProfile(GameBooster.resolveTargetGamePackage())
                                                }
                                                boostStatusText = msg
                                                isCompilingArt = false
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(11.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceHigh)
                                ) {
                                    Text(
                                        text = if (isCompilingArt) str.optimizing else str.artCompile,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    if (!isPro) {
                                        Spacer(Modifier.width(4.dp))
                                        ProTagPill()
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            ToggleRow(str.autoBoostLabel, isPro && turboAutoBoost, activeAccent, proLocked = !isPro) {
                                requirePro {
                                    turboAutoBoost = it
                                    Prefs.turboAutoBoost = it
                                }
                            }
                            ToggleRow(str.fixedPerfLabel, isPro && turboFixedPerf, EmeraldNeon, proLocked = !isPro) { enabled ->
                                requirePro {
                                    turboFixedPerf = enabled
                                    Prefs.turboFixedPerf = enabled
                                    uiScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            GameBooster.setFixedPerformanceMode(enabled)
                                        }
                                        fixedPerfLive = GameBooster.isFixedPerfActive
                                        val msg = when {
                                            !enabled -> "Fixed Performance Mode OFF"
                                            ok -> "Fixed Performance Mode ON (Guard ${thermalLimitC.roundToInt()}°C)"
                                            else -> "Fixed Perf HAL unsupported on this device"
                                        }
                                        boostStatusText = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(str.thermalLimitTitle, color = TextSecondary, fontSize = 10.5.sp)
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(41f to str.coolPreset, 43f to str.idealPreset, 45f to str.maxPreset).forEach { (lim, lbl) ->
                                    PillChip(
                                        label = lbl,
                                        selected = thermalLimitC.roundToInt() == lim.roundToInt(),
                                        accent = AmberVolt,
                                        locked = !isPro && lim != 43f,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (lim == 43f || isPro) {
                                            thermalLimitC = lim
                                            Prefs.turboThermalLimitC = lim
                                        } else {
                                            showProDialog = true
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    layoutMode = Prefs.LAYOUT_MINI
                                    hudStyle = Prefs.STYLE_MINIMAL
                                    intervalMs = 1000L
                                    showHudGraph = false
                                    Prefs.layoutMode = Prefs.LAYOUT_MINI
                                    Prefs.hudStyle = Prefs.STYLE_MINIMAL
                                    Prefs.updateIntervalMs = 1000L
                                    Prefs.showHudGraph = false
                                    pushOverlayUpdate()
                                    Toast.makeText(context, str.ultraLightToast, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A1426))
                            ) {
                                Text(str.ultraLightPreset, color = activeAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            val recBadge = when {
                                isRecording -> String.format(Locale.US, "● REC %02d:%02d", recElapsedSec / 60, recElapsedSec % 60)
                                else -> "READY"
                            }
                            SectionTitle(
                                title = str.sessionBenchmark,
                                badge = recBadge,
                                accent = when {
                                    isRecording -> CrimsonAccent
                                    else -> EmeraldNeon
                                },
                                iconType = "record"
                            )
                            Button(
                                onClick = {
                                    run {
                                        if (isRecording) {
                                            TelemetryHub.stopRecording()
                                            isRecording = false
                                            benchmarkHistory = TelemetryHub.loadHistory()
                                        } else {
                                            TelemetryHub.startRecording()
                                            isRecording = true
                                        }
                                        pushOverlayUpdate()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) CrimsonAccent else CardSurfaceHigh
                                )
                            ) {
                                Text(
                                    text = if (isRecording) str.stopRecordBtn else str.startRecordBtn,
                                    color = if (isRecording) Color.Black else TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }

                            val latestRes = benchmarkHistory.firstOrNull()
                            if (latestRes != null) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricStatBox("AVG FPS", String.format(Locale.US, "%.1f", latestRes.avgFps), EmeraldNeon, Modifier.weight(1f))
                                    MetricStatBox("1% LOW", "${latestRes.onePercentLowFps}", AmberVolt, Modifier.weight(1f))
                                    MetricStatBox("MIN/MAX", "${latestRes.minFps}/${latestRes.maxFps}", CyberCyan, Modifier.weight(1f))
                                    MetricStatBox("PEAK °C", String.format(Locale.US, "%.1f°", latestRes.peakTempC), CrimsonAccent, Modifier.weight(1f))
                                }
                            }

                            if (benchmarkHistory.size > 1) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = CardBorder)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${str.sessionHistory} (${benchmarkHistory.size})", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = str.clearAll,
                                        color = CrimsonAccent,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            TelemetryHub.clearHistory()
                                            benchmarkHistory = emptyList()
                                        }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                benchmarkHistory.take(4).forEach { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${s.timestamp} (${s.durationSec}s • ${s.sourceLabel})",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = String.format(Locale.US, "AVG %.1f | 1%% %d", s.avgFps, s.onePercentLowFps),
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(fontFeatureSettings = "tnum")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MiniVectorIcon("cpu", PurpleGpu, Modifier.size(13.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("CPU & GPU", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(telemetry.cpuSource, color = activeAccent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "CPU ${telemetry.cpuLoadPercent}%",
                                    color = TextPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = "GPU ${telemetry.gpuLoadPercent?.let { "$it%" } ?: "N/A"} • ${String.format(Locale.US, "%.2f", telemetry.cpuMaxGhz)}G",
                                    color = PurpleGpu,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (telemetry.cpuLoadPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                    color = activeAccent,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MiniVectorIcon("temp", AmberVolt, Modifier.size(13.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("THERMAL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${telemetry.batteryPercent}%", color = EmeraldNeon, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(6.dp))
                                val tempCol = when {
                                    telemetry.batteryTempC >= 42f -> CrimsonAccent
                                    telemetry.batteryTempC >= 38f -> AmberVolt
                                    else -> CyberCyan
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", telemetry.batteryTempC),
                                    color = tempCol,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = String.format(Locale.US, "%s%.1f W • %d mA", if (telemetry.isCharging) "+" else "-", telemetry.powerWatts, telemetry.currentMa),
                                    color = if (telemetry.isCharging) EmeraldNeon else AmberVolt,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ((telemetry.batteryTempC - 25f) / 25f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                    color = tempCol,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiniVectorIcon("ram", PinkRam, Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("MEMORY (RAM)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f / %.1f GB", telemetry.ramUsedGb, telemetry.ramTotalGb),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                val ramRatio = if (telemetry.ramTotalGb > 0f) (telemetry.ramUsedGb / telemetry.ramTotalGb).coerceIn(0f, 1f) else 0f
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ramRatio },
                                    modifier = Modifier.fillMaxWidth().height(4.5.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PinkRam,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiniVectorIcon("net", EmeraldNeon, Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("NETWORK I/O", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "↓ ${telemetry.netDownKbps.toInt()} KB/s",
                                    color = EmeraldNeon,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = "↑ ${telemetry.netUpKbps.toInt()} KB/s",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (telemetry.cores.isNotEmpty()) {
                        item {
                            CyberCard {
                                SectionTitle(str.cpuMatrix, "${telemetry.cores.size} CORES", activeAccent, iconType = "cpu")
                                telemetry.cores.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        pair.forEach { core ->
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "C${core.coreIndex} ${if (core.isBigCore) "BIG" else "EFF"}",
                                                        color = if (core.isBigCore) AmberVolt else TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "${core.curMhz} MHz",
                                                        color = TextPrimary,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        style = TextStyle(fontFeatureSettings = "tnum")
                                                    )
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                LinearProgressIndicator(
                                                    progress = { core.loadPercent / 100f },
                                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
                                                    color = if (core.isBigCore) AmberVolt else activeAccent,
                                                    trackColor = Color(0xFF1E293B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        val previewStyle = if (!isPro && (hudStyle == Prefs.STYLE_DECK || hudStyle == Prefs.STYLE_PILL)) Prefs.STYLE_GLASS else hudStyle
                        val previewLayout = if (!isPro && layoutMode == Prefs.LAYOUT_VERTICAL) Prefs.LAYOUT_HORIZONTAL else layoutMode
                        val previewFont = if (!isPro && (fontStyle == Prefs.FONT_CLEAN || fontStyle == Prefs.FONT_TECH)) Prefs.FONT_MONO_BOLD else fontStyle
                        CyberCard(borderColor = activeAccent.copy(alpha = 0.5f), accentGlow = activeAccent) {
                            SectionTitle(str.liveHudPreview, "$previewStyle • $previewLayout", activeAccent)
                            HudLivePreviewBox(
                                telemetry = telemetry,
                                fpsHistory = fpsHistory,
                                layoutMode = previewLayout,
                                hudStyle = previewStyle,
                                fontStyle = previewFont,
                                accent = activeAccent,
                                bgAlpha = bgAlpha,
                                textSizeSp = textSizeSp,
                                showHudGraph = isPro && showHudGraph,
                                sFps = sFps,
                                sFrameTime = sFrameTime,
                                sCpu = sCpu,
                                sClock = isPro && sClock,
                                sGpu = isPro && sGpu,
                                sRam = isPro && sRam,
                                sTemp = sTemp,
                                sBatt = sBatt,
                                sWatt = isPro && sWatt,
                                sNet = isPro && sNet,
                                maxScaleFps = effectiveGraphMax
                            )
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.styleFontTheme)
                            Text(str.overlayBoxStyle, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    Triple(Prefs.STYLE_GLASS, "Glass", false),
                                    Triple(Prefs.STYLE_PILL, "Pill", true),
                                    Triple(Prefs.STYLE_DECK, "Deck", true),
                                    Triple(Prefs.STYLE_MINIMAL, "Min", false)
                                ).forEach { (key, label, needsPro) ->
                                    val locked = needsPro && !isPro
                                    PillChip(
                                        label = label,
                                        selected = hudStyle == key && !locked,
                                        accent = activeAccent,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            hudStyle = key
                                            Prefs.hudStyle = key
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(11.dp))
                            Text(str.hudFontLabel, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    Triple(Prefs.FONT_SPORT, "Sport", false),
                                    Triple(Prefs.FONT_MONO_BOLD, "Mono", false),
                                    Triple(Prefs.FONT_CLEAN, "Clean", true),
                                    Triple(Prefs.FONT_TECH, "Tech", true)
                                ).forEach { (key, label, needsPro) ->
                                    val locked = needsPro && !isPro
                                    PillChip(
                                        label = label,
                                        selected = fontStyle == key && !locked,
                                        accent = activeAccent,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            fontStyle = key
                                            Prefs.fontStyle = key
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(11.dp))
                            Text(str.dataLayoutLabel, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(Prefs.LAYOUT_HORIZONTAL, str.layoutHorizontal, false),
                                    Triple(Prefs.LAYOUT_VERTICAL, str.layoutVertical, true),
                                    Triple(Prefs.LAYOUT_MINI, str.layoutMini, false)
                                ).forEach { (key, label, needsPro) ->
                                    val locked = needsPro && !isPro
                                    PillChip(
                                        label = label,
                                        selected = layoutMode == key && !locked,
                                        accent = activeAccent,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            layoutMode = key
                                            Prefs.layoutMode = key
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(11.dp))
                            Text(str.accentThemeLabel, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    Triple(Prefs.THEME_CYBER, "Cyber", CyberCyan to false),
                                    Triple(Prefs.THEME_EMERALD, "Neon", EmeraldNeon to false),
                                    Triple(Prefs.THEME_CRIMSON, "Fury", CrimsonAccent to true)
                                ).forEach { (key, label, pair) ->
                                    val (col, needsPro) = pair
                                    val locked = needsPro && !isPro
                                    PillChip(
                                        label = label,
                                        selected = themeMode == key && !locked,
                                        accent = col,
                                        swatchColor = col,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            themeMode = key
                                            Prefs.themeMode = key
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    Triple(Prefs.THEME_AMBER, "Volt Amber", AmberVolt),
                                    Triple(Prefs.THEME_VIOLET, "Hyper Violet", VioletHyper)
                                ).forEach { (key, label, col) ->
                                    val locked = !isPro
                                    PillChip(
                                        label = label,
                                        selected = themeMode == key && !locked,
                                        accent = col,
                                        swatchColor = col,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            themeMode = key
                                            Prefs.themeMode = key
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.opacityScaleControl)
                            val pct = ((bgAlpha / 255f) * 100).roundToInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(str.bgOpacity, color = TextSecondary, fontSize = 12.sp)
                                Text("$pct%", color = activeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = bgAlpha.toFloat(),
                                onValueChange = {
                                    bgAlpha = it.roundToInt()
                                    Prefs.bgAlpha = bgAlpha
                                    pushOverlayUpdate()
                                },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(thumbColor = activeAccent, activeTrackColor = activeAccent)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(str.hudFontSize, color = TextSecondary, fontSize = 12.sp)
                                Text(String.format(Locale.US, "%.1f sp", textSizeSp), color = activeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = textSizeSp,
                                onValueChange = {
                                    textSizeSp = (it * 2f).roundToInt() / 2f
                                    Prefs.textSizeSp = textSizeSp
                                    pushOverlayUpdate()
                                },
                                valueRange = 8f..16f,
                                colors = SliderDefaults.colors(thumbColor = activeAccent, activeTrackColor = activeAccent)
                            )

                            Spacer(Modifier.height(6.dp))
                            Text(str.updateSpeed, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(250L, "0.25s", true),
                                    Triple(500L, "0.5s", false),
                                    Triple(1000L, "1.0s", false)
                                ).forEach { (ms, lbl, needsPro) ->
                                    val locked = needsPro && !isPro
                                    PillChip(
                                        label = lbl,
                                        selected = intervalMs == ms && !locked,
                                        accent = activeAccent,
                                        locked = locked,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (locked) {
                                            showProDialog = true
                                        } else {
                                            intervalMs = ms
                                            Prefs.updateIntervalMs = ms
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(str.graphMaxScale, color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Auto", 60 to "60", 90 to "90", 120 to "120").forEach { (cap, lbl) ->
                                    PillChip(
                                        label = lbl,
                                        selected = graphMaxFps == cap,
                                        accent = activeAccent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        graphMaxFps = cap
                                        Prefs.graphMaxFps = cap
                                        pushOverlayUpdate()
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            ToggleRow(str.hudGraphToggle, isPro && showHudGraph, activeAccent, proLocked = !isPro) {
                                requirePro {
                                    showHudGraph = it
                                    Prefs.showHudGraph = it
                                    pushOverlayUpdate()
                                }
                            }
                            ToggleRow(str.snapToEdgeToggle, snapToEdge, activeAccent) {
                                snapToEdge = it
                                Prefs.snapToEdge = it
                                pushOverlayUpdate()
                            }
                            ToggleRow(str.lockTouchToggle, touchLocked, activeAccent) {
                                if (!OverlayService.isRunning) {
                                    Toast.makeText(context, str.startHudBeforeLock, Toast.LENGTH_SHORT).show()
                                } else if (OverlayService.isTouchLocked != it) {
                                    context.startService(Intent(context, OverlayService::class.java).apply {
                                        action = OverlayService.ACTION_TOGGLE_LOCK
                                    })
                                    touchLocked = it
                                }
                            }
                        }
                    }

                    item {
                        val dm = context.resources.displayMetrics
                        val isLand = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        fun applyPreset(x: Int, y: Int) {
                            Prefs.setPos(isLand, x, y)
                            pushOverlayUpdate()
                        }
                        CyberCard {
                            SectionTitle(str.screenPosPreset, if (isLand) "LANDSCAPE" else "PORTRAIT", activeAccent)
                            val topSafeY = ((if (isLand) 14 else 38) * dm.density).toInt()
                            val sideMargin = (10 * dm.density).toInt()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip(str.posTopLeft, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset(sideMargin, topSafeY)
                                }
                                PillChip(str.posTopCenter, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.34f).toInt(), topSafeY)
                                }
                                PillChip(str.posTopRight, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.68f).toInt(), topSafeY)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip(str.posBotLeft, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset(sideMargin, (dm.heightPixels * 0.85f).toInt())
                                }
                                PillChip(str.posBotCenter, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.34f).toInt(), (dm.heightPixels * 0.85f).toInt())
                                }
                                PillChip(str.posBotRight, false, activeAccent, modifier = Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.68f).toInt(), (dm.heightPixels * 0.85f).toInt())
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        CyberCard {
                            SectionTitle(str.sensorQuickPreset, "1-TAP", activeAccent)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Esports", false, EmeraldNeon, modifier = Modifier.weight(1f)) {
                                    sFps = true; sFrameTime = false; sCpu = false; sClock = false
                                    sGpu = false; sRam = false; sTemp = true; sBatt = false; sWatt = false; sNet = false
                                    Prefs.showFps = true; Prefs.showFrameTime = false; Prefs.showCpu = false; Prefs.showClock = false
                                    Prefs.showGpu = false; Prefs.showRam = false; Prefs.showTemp = true; Prefs.showBatteryPct = false
                                    Prefs.showWatt = false; Prefs.showNet = false
                                    pushOverlayUpdate()
                                }
                                PillChip("Benchmark", false, activeAccent, locked = !isPro, modifier = Modifier.weight(1f)) {
                                    requirePro {
                                        sFps = true; sFrameTime = true; sCpu = true; sClock = false
                                        sGpu = true; sRam = false; sTemp = true; sBatt = false; sWatt = true; sNet = false
                                        Prefs.showFps = true; Prefs.showFrameTime = true; Prefs.showCpu = true; Prefs.showClock = false
                                        Prefs.showGpu = true; Prefs.showRam = false; Prefs.showTemp = true; Prefs.showBatteryPct = false
                                        Prefs.showWatt = true; Prefs.showNet = false
                                        pushOverlayUpdate()
                                    }
                                }
                                PillChip("Full Pro", false, VioletHyper, locked = !isPro, modifier = Modifier.weight(1f)) {
                                    requirePro {
                                        sFps = true; sFrameTime = true; sCpu = true; sClock = true
                                        sGpu = true; sRam = true; sTemp = true; sBatt = true; sWatt = true; sNet = true
                                        Prefs.showFps = true; Prefs.showFrameTime = true; Prefs.showCpu = true; Prefs.showClock = true
                                        Prefs.showGpu = true; Prefs.showRam = true; Prefs.showTemp = true; Prefs.showBatteryPct = true
                                        Prefs.showWatt = true; Prefs.showNet = true
                                        pushOverlayUpdate()
                                    }
                                }
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.sensorFramePerf, iconType = "pulse", accent = activeAccent)
                            SensorToggleRow("FPS (Frame Per Second)", "${telemetry.fps} FPS", EmeraldNeon, sFps, activeAccent) { sFps = it; Prefs.showFps = it; pushOverlayUpdate() }
                            SensorToggleRow("Frame Time", String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs), CyberCyan, sFrameTime, activeAccent) { sFrameTime = it; Prefs.showFrameTime = it; pushOverlayUpdate() }
                            SensorToggleRow("CPU Total Load", "${telemetry.cpuLoadPercent}% (${telemetry.cpuSource})", TextPrimary, sCpu, activeAccent) { sCpu = it; Prefs.showCpu = it; pushOverlayUpdate() }
                            SensorToggleRow("CPU Peak Clock", String.format(Locale.US, "%.2f GHz", telemetry.cpuMaxGhz), Color(0xFFCBD5E1), isPro && sClock, activeAccent, proLocked = !isPro) {
                                requirePro { sClock = it; Prefs.showClock = it; pushOverlayUpdate() }
                            }
                            SensorToggleRow("GPU Load", telemetry.gpuLoadPercent?.let { "$it%" } ?: "N/A", PurpleGpu, isPro && sGpu, activeAccent, proLocked = !isPro) {
                                requirePro { sGpu = it; Prefs.showGpu = it; pushOverlayUpdate() }
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.sensorMemBattNet, iconType = "temp", accent = AmberVolt)
                            SensorToggleRow("RAM Usage", String.format(Locale.US, "%.1f GB", telemetry.ramUsedGb), PinkRam, isPro && sRam, activeAccent, proLocked = !isPro) {
                                requirePro { sRam = it; Prefs.showRam = it; pushOverlayUpdate() }
                            }
                            SensorToggleRow("Battery Temp", String.format(Locale.US, "%.1f °C", telemetry.batteryTempC), CyberCyan, sTemp, activeAccent) { sTemp = it; Prefs.showTemp = it; pushOverlayUpdate() }
                            SensorToggleRow("Battery Level", "${telemetry.batteryPercent}%", EmeraldNeon, sBatt, activeAccent) { sBatt = it; Prefs.showBatteryPct = it; pushOverlayUpdate() }
                            SensorToggleRow("Power Draw", String.format(Locale.US, "%s%.1f W", if (telemetry.isCharging) "+" else "-", telemetry.powerWatts), AmberVolt, isPro && sWatt, activeAccent, proLocked = !isPro) {
                                requirePro { sWatt = it; Prefs.showWatt = it; pushOverlayUpdate() }
                            }
                            SensorToggleRow("Network Speed", "↓ ${telemetry.netDownKbps.toInt()} KB/s", EmeraldNeon, isPro && sNet, activeAccent, proLocked = !isPro) {
                                requirePro { sNet = it; Prefs.showNet = it; pushOverlayUpdate() }
                            }
                        }
                    }
                }

                else -> {
                    item {
                        val statusBadge = when (proStatus.state) {
                            LicenseState.PRO_ACTIVE -> str.proActiveStatus
                            LicenseState.TRIAL_ACTIVE -> "${str.proTrialStatus} (${proStatus.expiryLabel})"
                            LicenseState.EXPIRED -> str.proExpiredStatus
                            LicenseState.FREE -> str.proPriceBadge
                        }
                        val statusColor = when (proStatus.state) {
                            LicenseState.PRO_ACTIVE -> EmeraldNeon
                            LicenseState.TRIAL_ACTIVE -> CyberCyan
                            LicenseState.EXPIRED -> CrimsonAccent
                            LicenseState.FREE -> AmberVolt
                        }
                        CyberCard(borderColor = statusColor.copy(alpha = 0.5f), accentGlow = statusColor) {
                            SectionTitle(str.proTitle, statusBadge, statusColor, iconType = "bolt")
                            val linkedMail = Prefs.linkedGoogleEmail
                            Text(
                                text = when {
                                    proStatus.state == LicenseState.PRO_ACTIVE && linkedMail.isNotBlank() ->
                                        "$linkedMail • ${proStatus.tierLabel} (${proStatus.expiryLabel})"
                                    proStatus.state == LicenseState.PRO_ACTIVE ->
                                        "${proStatus.tierLabel} • ${proStatus.expiryLabel}"
                                    linkedMail.isNotBlank() ->
                                        "${str.proGoogleConnected}: $linkedMail"
                                    else -> str.proSubtitle
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { showProDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isPro) CardSurfaceHigh else AmberVolt)
                            ) {
                                Text(
                                    text = str.proManageBtn,
                                    color = if (isPro) TextPrimary else Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.setupOverlayTitle, if (overlayGranted) str.badgeActive else str.badgeRequired, if (overlayGranted) EmeraldNeon else AmberVolt)
                            Text(text = str.setupOverlayDesc, color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceHigh)
                            ) {
                                Text(if (overlayGranted) str.setupOverlayActive else str.setupOverlayOpen, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.setupShizukuTitle, if (shizukuConnected) str.badgeConnected else str.badgeOptional, if (shizukuConnected) EmeraldNeon else CyberCyan)
                            Text(text = str.setupShizukuDesc, color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusDotPill(
                                    label = if (shizukuBinderAlive) "Binder: OK" else "Binder: Off",
                                    color = if (shizukuBinderAlive) EmeraldNeon else TextSecondary
                                )
                                StatusDotPill(
                                    label = if (shizukuConnected) "Perm: Granted" else "Perm: Needed",
                                    color = if (shizukuConnected) EmeraldNeon else AmberVolt
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    try {
                                        if (!Shizuku.pingBinder()) {
                                            Toast.makeText(context, "Shizuku service not running. Open Shizuku app & restart FramePulse.", Toast.LENGTH_LONG).show()
                                        } else if (Shizuku.isPreV11()) {
                                            Toast.makeText(context, "Please update Shizuku to the latest version.", Toast.LENGTH_LONG).show()
                                        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                            Shizuku.requestPermission(100)
                                        } else {
                                            shizukuConnected = true
                                            Toast.makeText(context, str.setupShizukuConnected, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (t: Throwable) {
                                        Toast.makeText(context, "Shizuku error: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                            ) {
                                Text(if (shizukuConnected) str.setupShizukuConnected else str.setupShizukuConnect, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.setupTileTitle, "PRO", CyberCyan)
                            Text(text = str.setupTileDesc, color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.setupLangTitle, AppStrings.resolveLangCode(langPref).uppercase(Locale.US), activeAccent)
                            Text(text = str.setupLangDesc, color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            AppStrings.supportedLanguages.chunked(3).forEachIndexed { rowIdx, rowItems ->
                                if (rowIdx > 0) Spacer(Modifier.height(7.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    rowItems.forEach { (code, label) ->
                                        PillChip(
                                            label = label,
                                            selected = langPref == code,
                                            accent = activeAccent,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            langPref = code
                                            Prefs.appLanguage = code
                                            pushOverlayUpdate()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(str.setupDiagTitle, if (lastCrashLog.isBlank()) str.badgeClean else str.badgeHasLog, if (lastCrashLog.isBlank()) EmeraldNeon else CrimsonAccent)
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${telemetry.displayRefreshHz}Hz • ${Runtime.getRuntime().availableProcessors()} Cores",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    uiScope.launch {
                                        val report = withContext(Dispatchers.IO) {
                                            val diagMon = SystemMonitor(context)
                                            try {
                                                diagMon.dumpSurfaceFlingerDiagnostics()
                                            } catch (t: Throwable) {
                                                "Error: ${t.message}"
                                            } finally {
                                                diagMon.stop()
                                            }
                                        }
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("FramePulse SF Info", report))
                                        Toast.makeText(context, str.sfDebugCopied, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceHigh)
                            ) {
                                Text(str.copySfDebug, color = TextPrimary, fontSize = 12.sp)
                            }
                            if (lastCrashLog.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = lastCrashLog.lines().take(5).joinToString("\n"),
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("FramePulse Crash", lastCrashLog))
                                            Toast.makeText(context, str.copyLog, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                                    ) {
                                        Text(str.copyLog, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            Prefs.lastCrashLog = ""
                                            lastCrashLog = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceHigh)
                                    ) {
                                        Text(str.clearLog, color = TextPrimary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProSupporterDialog(
    str: AppLocaleStrings,
    proStatus: ProStatus,
    accent: Color,
    onDismiss: () -> Unit,
    onStatusChanged: (ProStatus) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isIndonesian = AppStrings.resolveLangCode() == "id"

    var linkedEmail by remember { mutableStateOf(Prefs.linkedGoogleEmail) }
    var selectedPlanId by remember { mutableStateOf("1Y") }
    var isSyncing by remember { mutableStateOf(false) }
    var showManualKey by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(EmeraldNeon) }

    val selectedPlan = ProLicenseManager.plans.firstOrNull { it.id == selectedPlanId } ?: ProLicenseManager.plans[2]

    fun triggerCloudSync(emailToUse: String = linkedEmail) {
        if (emailToUse.isBlank()) {
            feedbackMsg = str.proSyncNeedEmail
            feedbackColor = AmberVolt
            return
        }
        isSyncing = true
        feedbackMsg = ""
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ProLicenseManager.syncWithCloud(context, emailToUse)
            }
            isSyncing = false
            when (res) {
                is ProLicenseManager.CloudSyncResult.Activated -> {
                    val next = ProLicenseManager.getStatus(forceRefresh = true)
                    onStatusChanged(next)
                    Toast.makeText(context, "${str.proKeySuccess} (${res.expiryLabel})", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
                ProLicenseManager.CloudSyncResult.NotFound -> {
                    feedbackMsg = str.proSyncNotFound
                    feedbackColor = AmberVolt
                }
                ProLicenseManager.CloudSyncResult.Expired -> {
                    feedbackMsg = str.proKeyExpired
                    feedbackColor = AmberVolt
                }
                ProLicenseManager.CloudSyncResult.DeviceLimit -> {
                    feedbackMsg = str.proSyncDeviceLimit
                    feedbackColor = CrimsonAccent
                }
                ProLicenseManager.CloudSyncResult.NoEmail -> {
                    feedbackMsg = str.proSyncNeedEmail
                    feedbackColor = AmberVolt
                }
                ProLicenseManager.CloudSyncResult.ServerNotConfigured -> {
                    feedbackMsg = str.proSyncNoServer
                    feedbackColor = AmberVolt
                }
                is ProLicenseManager.CloudSyncResult.Error -> {
                    feedbackMsg = "Sync error: ${res.detail}"
                    feedbackColor = CrimsonAccent
                }
            }
        }
    }

    val googlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
            if (picked.isNotBlank()) {
                linkedEmail = picked.lowercase(Locale.US)
                Prefs.linkedGoogleEmail = linkedEmail
                val updated = ProLicenseManager.getStatus(forceRefresh = true)
                onStatusChanged(updated)
                if (updated.tierLabel == "OWNER") {
                    Toast.makeText(context, "Owner Access aktif (Lifetime)!", Toast.LENGTH_LONG).show()
                    onDismiss()
                } else if (ProLicenseManager.isCloudConfigured()) {
                    triggerCloudSync(linkedEmail)
                }
            }
        }
    }

    fun launchGooglePicker() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            googlePickerLauncher.launch(intent)
        } catch (_: Throwable) {
            Toast.makeText(context, "Google Account Picker unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
        }
    }

    fun planTitle(id: String): String = when (id) {
        "1M" -> str.proPlan1M
        "6M" -> str.proPlan6M
        "1Y" -> str.proPlan1Y
        else -> str.proPlanLife
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = CardSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AmberVolt.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FramePulseLogoMark(accent = AmberVolt, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(str.proTitle, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        val statusPillText = when (proStatus.state) {
                            LicenseState.PRO_ACTIVE -> "${str.proActiveStatus} • ${proStatus.expiryLabel}"
                            LicenseState.TRIAL_ACTIVE -> "${str.proTrialStatus} (${proStatus.expiryLabel})"
                            LicenseState.EXPIRED -> "${str.proExpiredStatus} (${proStatus.expiryLabel})"
                            LicenseState.FREE -> str.proFreeStatus
                        }
                        Spacer(Modifier.height(2.dp))
                        StatusDotPill(
                            label = statusPillText,
                            color = if (proStatus.isPro) EmeraldNeon else AmberVolt
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(str.proSubtitle, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)

                // === STEP 1: GOOGLE ACCOUNT LINK ===
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070C17))
                        .border(1.dp, if (linkedEmail.isNotBlank()) EmeraldNeon.copy(alpha = 0.45f) else CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    if (linkedEmail.isBlank()) {
                        Column {
                            Text(str.proGoogleHint, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { launchGooglePicker() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                            ) {
                                Text(str.proConnectGoogle, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(str.proGoogleConnected, color = EmeraldNeon, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = linkedEmail,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            PillChip(
                                label = str.proSwitchGoogle,
                                selected = false,
                                accent = CyberCyan,
                                onClick = { launchGooglePicker() }
                            )
                        }
                    }
                }

                // === STEP 2: 4 SUBSCRIPTION TIERS GRID ===
                Spacer(Modifier.height(10.dp))
                ProLicenseManager.plans.chunked(2).forEachIndexed { rowIdx, pair ->
                    if (rowIdx > 0) Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        pair.forEach { plan ->
                            val isSelected = selectedPlanId == plan.id
                            val isHighlight = plan.id == "1Y" || plan.id == "LIFETIME"
                            val cardAccent = if (plan.id == "LIFETIME") AmberVolt else if (plan.id == "1Y") EmeraldNeon else accent
                            val badgeTxt = if (isIndonesian) plan.badgeId else plan.badgeEn
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) cardAccent.copy(alpha = 0.16f) else Color(0xFF070C17))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) cardAccent else CardBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedPlanId = plan.id }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = planTitle(plan.id),
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(cardAccent.copy(alpha = if (isHighlight) 0.22f else 0.12f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeTxt,
                                            color = cardAccent,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = if (isIndonesian) plan.priceIdr else plan.priceUsd,
                                    color = cardAccent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isIndonesian) "Global ${plan.priceUsd}*" else "ID ${plan.priceIdr}",
                                    color = TextMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(str.proIntlFeeNote, color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp)

                // === FEATURES SUMMARY ===
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF070C17))
                        .border(1.dp, CardBorder, RoundedCornerShape(11.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    str.proFeatureList.forEach { feat ->
                        Text("✓  $feat", color = TextPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }

                // === STEP 3: PAY & AUTO-SYNC ===
                if (proStatus.canStartTrial) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (ProLicenseManager.startFreeTrial()) {
                                val next = ProLicenseManager.getStatus(forceRefresh = true)
                                onStatusChanged(next)
                                Toast.makeText(context, str.proTrialStarted, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text(str.proStartTrial, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = {
                        if (linkedEmail.isBlank()) {
                            Toast.makeText(context, str.proSyncNeedEmail, Toast.LENGTH_SHORT).show()
                            launchGooglePicker()
                            return@Button
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("FramePulse Google Email", linkedEmail))
                        Toast.makeText(context, str.proTakoEmailCopied, Toast.LENGTH_LONG).show()
                        openUrl(ProLicenseManager.DONATE_URL_ID)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon)
                ) {
                    Text(
                        text = "${str.proPayTakoPrefix} • ${selectedPlan.priceIdr}",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp
                    )
                }
                var showQrisDialog by remember { mutableStateOf(false) }
                var showPresetDialog by remember { mutableStateOf(false) }
                var showLeaderboardDialog by remember { mutableStateOf(false) }
                var showAboutDialog by remember { mutableStateOf(false) }
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = {
                        if (linkedEmail.isBlank()) {
                            Toast.makeText(context, str.proSyncNeedEmail, Toast.LENGTH_SHORT).show()
                            launchGooglePicker()
                            return@Button
                        }
                        showQrisDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) {
                    Text(
                        text = "Bayar QRIS Otomatis • ${selectedPlan.priceIdr}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp
                    )
                }
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = { showPresetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text(
                        text = "Preset Game Pro (Valorant, MLBB, PUBG...)",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
                if (showQrisDialog) {
                    val amt = selectedPlan.priceIdr.filter { it.isDigit() }.toIntOrNull() ?: 10000
                    QrisPaymentDialog(
                        email = linkedEmail,
                        planLabel = selectedPlan.priceIdr,
                        amountIdr = amt,
                        onCheckPaid = {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                ProLicenseManager.syncWithCloud(context, linkedEmail)
                                ProLicenseManager.isProUnlocked()
                            }
                        },
                        onDismiss = { showQrisDialog = false }
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showLeaderboardDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("Leaderboard & Pamer", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("About & GitHub", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                if (showLeaderboardDialog) {
                    val ownerNow = ProLicenseManager.isOwnerEmail(linkedEmail)
                    LeaderboardDialog(
                        email = linkedEmail,
                        isPro = ProLicenseManager.isProUnlocked() || ownerNow,
                        isOwner = ownerNow,
                        onDismiss = { showLeaderboardDialog = false }
                    )
                }
                if (showAboutDialog) {
                    AboutAppDialog(
                        isOwner = ProLicenseManager.isOwnerEmail(linkedEmail),
                        onDismiss = { showAboutDialog = false }
                    )
                }
                if (showPresetDialog) {
                    GamePresetDialog(
                        isPro = ProLicenseManager.isProUnlocked(),
                        onDismiss = { showPresetDialog = false }
                    )
                }
                if (ProLicenseManager.DONATE_URL_GLOBAL.startsWith("https://")) {
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = {
                        if (linkedEmail.isBlank()) {
                            Toast.makeText(context, str.proSyncNeedEmail, Toast.LENGTH_SHORT).show()
                            launchGooglePicker()
                            return@Button
                        }
                        openUrl(ProLicenseManager.buildGlobalCheckoutUrl(linkedEmail))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                ) {
                    Text(
                        text = "${str.proPayGlobalPrefix} • ${selectedPlan.priceUsd}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
                }

                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceHigh)
                    ) {
                        Text(str.proCloseBtn, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                    Button(
                        enabled = !isSyncing,
                        onClick = { triggerCloudSync() },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberVolt)
                    ) {
                        Text(
                            text = if (isSyncing) str.proSyncing else str.proSyncStatusBtn,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                if (feedbackMsg.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(feedbackMsg, color = feedbackColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
                }

                // Optional collapsible offline manual key fallback
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (showManualKey) "▾ ${str.proManualToggle}" else "▸ ${str.proManualToggle}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualKey = !showManualKey }
                        .padding(vertical = 4.dp)
                )
                if (showManualKey) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text(str.proKeyPlaceholder, color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberVolt,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = Color(0xFF070C17),
                                unfocusedContainerColor = Color(0xFF070C17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                when (val res = ProLicenseManager.redeemKey(keyInput)) {
                                    is ProLicenseManager.RedeemResult.Success -> {
                                        val next = ProLicenseManager.getStatus(forceRefresh = true)
                                        onStatusChanged(next)
                                        Toast.makeText(context, str.proKeySuccess, Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    }
                                    is ProLicenseManager.RedeemResult.Expired -> {
                                        feedbackMsg = "${str.proKeyExpired} (${res.expiryLabel})"
                                        feedbackColor = AmberVolt
                                    }
                                    ProLicenseManager.RedeemResult.Invalid -> {
                                        feedbackMsg = str.proKeyInvalid
                                        feedbackColor = CrimsonAccent
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberVolt),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            Text(str.proActivateBtn, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF080E1B))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    accent: Color,
    proLocked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
            if (proLocked) {
                Spacer(Modifier.width(6.dp))
                ProTagPill()
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
        )
    }
}

@Composable
fun SensorToggleRow(
    label: String,
    liveValue: String,
    valueColor: Color,
    checked: Boolean,
    accent: Color,
    proLocked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (proLocked) {
                    Spacer(Modifier.width(6.dp))
                    ProTagPill()
                }
            }
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(valueColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = liveValue,
                    color = valueColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
        )
    }
}
