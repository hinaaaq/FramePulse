package com.shin.framepulse

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                Toast.makeText(this, "Shizuku aktif: True Game FPS & SELinux Sensors siap!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Izin Shizuku ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                )
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

    var isAppForeground by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
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

    var layoutMode by remember { mutableStateOf(Prefs.layoutMode) }
    var hudStyle by remember { mutableStateOf(Prefs.hudStyle) }
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

    val activeAccent = when (themeMode) {
        Prefs.THEME_EMERALD -> EmeraldNeon
        Prefs.THEME_CRIMSON -> CrimsonAccent
        Prefs.THEME_AMBER -> AmberVolt
        else -> CyberCyan
    }

    fun pushOverlayUpdate() {
        if (OverlayService.isRunning) {
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_CONFIG
            })
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
                    if (OverlayService.isRunning) { if (localMonitor.isRunning()) localMonitor.stop() } else localMonitor.start()
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
            delay(500L)
        }
    }

    val effectiveGraphMax = if (graphMaxFps > 0) graphMaxFps else telemetry.displayRefreshHz

    Scaffold(
        containerColor = BgObsidian,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TopBarSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FramePulse",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(activeAccent.copy(alpha = 0.2f))
                                .border(1.dp, activeAccent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("PRO 2.5.4", color = activeAccent, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val engineText = when {
                        telemetry.isTrueGameFps && telemetry.activeSurfaceName.isNotBlank() -> "SF • ${telemetry.activeSurfaceName}"
                        shizukuConnected -> "Shizuku Ready • ${telemetry.displayRefreshHz}Hz"
                        else -> "Standard • ${telemetry.displayRefreshHz}Hz"
                    }
                    StatusDotPill(
                        label = engineText,
                        color = if (shizukuConnected) EmeraldNeon else TextSecondary
                    )
                }

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "Aktifkan izin Overlay di tab Setup dulu", Toast.LENGTH_SHORT).show()
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceRunning) CrimsonAccent else EmeraldNeon
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (serviceRunning) "STOP HUD" else "START HUD",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = TopBarSurface,
                tonalElevation = 0.dp
            ) {
                val tabs = listOf("Monitor", "HUD Studio", "Sensors", "Setup")
                tabs.forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = idx },
                        icon = {
                            NavVectorIcon(
                                index = idx,
                                tint = if (selected) activeAccent else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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
                        CyberCard(borderColor = activeAccent.copy(alpha = 0.45f)) {
                            SectionTitle(
                                title = "REAL-TIME FRAME PACING",
                                badge = if (telemetry.isTrueGameFps) "SURFACEFLINGER" else "CHOREOGRAPHER",
                                accent = activeAccent
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
                                        fontSize = 46.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = " FPS",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 9.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs),
                                        color = TextPrimary,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Target ${telemetry.displayRefreshHz}Hz",
                                        color = TextSecondary,
                                        fontSize = 11.sp
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
                        CyberCard {
                            val recBadge = if (isRecording) {
                                String.format(Locale.US, "● REC %02d:%02d", recElapsedSec / 60, recElapsedSec % 60)
                            } else {
                                "READY"
                            }
                            SectionTitle(
                                title = "SESSION BENCHMARK RECORDER",
                                badge = recBadge,
                                accent = if (isRecording) CrimsonAccent else EmeraldNeon
                            )
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        TelemetryHub.stopRecording()
                                        isRecording = false
                                        benchmarkHistory = TelemetryHub.loadHistory()
                                    } else {
                                        TelemetryHub.startRecording()
                                        isRecording = true
                                    }
                                    pushOverlayUpdate()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) CrimsonAccent else Color(0xFF1E293B)
                                )
                            ) {
                                Text(
                                    text = if (isRecording) "STOP & SIMPAN HASIL BENCHMARK" else "MULAI REKAM SESI GAME (TRUE 1% LOW)",
                                    color = if (isRecording) Color.Black else TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }

                            val latestRes = benchmarkHistory.firstOrNull()
                            if (latestRes != null) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricMiniCol("AVG FPS", String.format(Locale.US, "%.1f", latestRes.avgFps), EmeraldNeon)
                                    MetricMiniCol("1% LOW", "${latestRes.onePercentLowFps}", AmberVolt)
                                    MetricMiniCol("MIN/MAX", "${latestRes.minFps}/${latestRes.maxFps}", CyberCyan)
                                    MetricMiniCol("PEAK °C", String.format(Locale.US, "%.1f°", latestRes.peakTempC), CrimsonAccent)
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
                                    Text("RIWAYAT SESI (${benchmarkHistory.size})", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Hapus Semua",
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
                                            fontFamily = FontFamily.Monospace
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CPU & GPU", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(telemetry.cpuSource, color = activeAccent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "CPU ${telemetry.cpuLoadPercent}%",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "GPU ${telemetry.gpuLoadPercent?.let { "$it%" } ?: "N/A"} • ${String.format(Locale.US, "%.2f", telemetry.cpuMaxGhz)}G",
                                    color = PurpleGpu,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("THERMAL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("BAT ${telemetry.batteryPercent}%", color = EmeraldNeon, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", telemetry.batteryTempC),
                                    color = when {
                                        telemetry.batteryTempC >= 42f -> CrimsonAccent
                                        telemetry.batteryTempC >= 38f -> AmberVolt
                                        else -> CyberCyan
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "%s%.1f W • %d mA", if (telemetry.isCharging) "+" else "-", telemetry.powerWatts, telemetry.currentMa),
                                    color = AmberVolt,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
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
                                Text("MEMORY (RAM)", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f / %.1f GB", telemetry.ramUsedGb, telemetry.ramTotalGb),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                val ramRatio = if (telemetry.ramTotalGb > 0f) (telemetry.ramUsedGb / telemetry.ramTotalGb).coerceIn(0f, 1f) else 0f
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ramRatio },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PinkRam,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Text("NETWORK I/O", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "↓ ${telemetry.netDownKbps.toInt()} KB/s",
                                    color = EmeraldNeon,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
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
                                SectionTitle("CPU FREQUENCY MATRIX", "${telemetry.cores.size} CORES", activeAccent)
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
                                                        fontFamily = FontFamily.Monospace
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
                        CyberCard(borderColor = activeAccent.copy(alpha = 0.5f)) {
                            SectionTitle("LIVE HUD PREVIEW (1:1)", "$hudStyle • $layoutMode", activeAccent)
                            HudLivePreviewBox(
                                telemetry = telemetry,
                                fpsHistory = fpsHistory,
                                layoutMode = layoutMode,
                                hudStyle = hudStyle,
                                accent = activeAccent,
                                bgAlpha = bgAlpha,
                                textSizeSp = textSizeSp,
                                showHudGraph = showHudGraph,
                                sFps = sFps,
                                sFrameTime = sFrameTime,
                                sCpu = sCpu,
                                sClock = sClock,
                                sGpu = sGpu,
                                sRam = sRam,
                                sTemp = sTemp,
                                sBatt = sBatt,
                                sWatt = sWatt,
                                sNet = sNet,
                                maxScaleFps = effectiveGraphMax
                            )
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("GAYA, LAYOUT & TEMA WARNA")
                            Text("Gaya Kotak HUD", color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Prefs.STYLE_GLASS to "Glass Card",
                                    Prefs.STYLE_PILL to "Esports Pill",
                                    Prefs.STYLE_MINIMAL to "Minimal"
                                ).forEach { (key, label) ->
                                    PillChip(
                                        label = label,
                                        selected = hudStyle == key,
                                        accent = activeAccent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        hudStyle = key
                                        Prefs.hudStyle = key
                                        pushOverlayUpdate()
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Susunan Data", color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Prefs.LAYOUT_HORIZONTAL to "Horizontal",
                                    Prefs.LAYOUT_VERTICAL to "Vertikal",
                                    Prefs.LAYOUT_MINI to "Mini"
                                ).forEach { (key, label) ->
                                    PillChip(
                                        label = label,
                                        selected = layoutMode == key,
                                        accent = activeAccent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        layoutMode = key
                                        Prefs.layoutMode = key
                                        pushOverlayUpdate()
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Aksen Warna", color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(Prefs.THEME_CYBER, "Cyber", CyberCyan),
                                    Triple(Prefs.THEME_EMERALD, "Emerald", EmeraldNeon),
                                    Triple(Prefs.THEME_CRIMSON, "Crimson", CrimsonAccent),
                                    Triple(Prefs.THEME_AMBER, "Volt", AmberVolt)
                                ).forEach { (key, label, col) ->
                                    PillChip(
                                        label = label,
                                        selected = themeMode == key,
                                        accent = col,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        themeMode = key
                                        Prefs.themeMode = key
                                        pushOverlayUpdate()
                                    }
                                }
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("TRANSPARANSI, SKALA & KONTROL")
                            val pct = ((bgAlpha / 255f) * 100).roundToInt()
                            Text("Opacity Background: $pct%", color = TextSecondary, fontSize = 12.sp)
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

                            Text("Ukuran Teks HUD: ${String.format(Locale.US, "%.1f", textSizeSp)} sp", color = TextSecondary, fontSize = 12.sp)
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
                            Text("Kecepatan Update", color = TextSecondary, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(250L to "0.25s", 500L to "0.5s", 1000L to "1.0s").forEach { (ms, lbl) ->
                                    PillChip(
                                        label = lbl,
                                        selected = intervalMs == ms,
                                        accent = activeAccent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        intervalMs = ms
                                        Prefs.updateIntervalMs = ms
                                        pushOverlayUpdate()
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text("Skala Maks Grafik FPS", color = TextSecondary, fontSize = 11.sp)
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
                            ToggleRow("Grafik Mini di dalam HUD", showHudGraph, activeAccent) {
                                showHudGraph = it
                                Prefs.showHudGraph = it
                                pushOverlayUpdate()
                            }
                            ToggleRow("Snap Otomatis ke Tepi Layar", snapToEdge, activeAccent) {
                                snapToEdge = it
                                Prefs.snapToEdge = it
                                pushOverlayUpdate()
                            }
                            ToggleRow("Kunci Sentuhan (Touch-Through)", touchLocked, activeAccent) {
                                if (!OverlayService.isRunning) {
                                    Toast.makeText(context, "Nyalakan START HUD dulu untuk mengunci sentuhan", Toast.LENGTH_SHORT).show()
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
                            SectionTitle("PRESET POSISI LAYAR", if (isLand) "LANDSCAPE" else "PORTRAIT", activeAccent)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Kiri Atas", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset(20, 48)
                                }
                                PillChip("Tengah Atas", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.34f).toInt(), 48)
                                }
                                PillChip("Kanan Atas", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.68f).toInt(), 48)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Kiri Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset(20, (dm.heightPixels * 0.85f).toInt())
                                }
                                PillChip("Tengah Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.34f).toInt(), (dm.heightPixels * 0.85f).toInt())
                                }
                                PillChip("Kanan Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    applyPreset((dm.widthPixels * 0.68f).toInt(), (dm.heightPixels * 0.85f).toInt())
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        CyberCard {
                            SectionTitle("SENSOR FRAME & PERFORMA")
                            SensorToggleRow("FPS (Frame Per Second)", "${telemetry.fps} FPS", sFps, activeAccent) { sFps = it; Prefs.showFps = it; pushOverlayUpdate() }
                            SensorToggleRow("Frame Time", String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs), sFrameTime, activeAccent) { sFrameTime = it; Prefs.showFrameTime = it; pushOverlayUpdate() }
                            SensorToggleRow("CPU Total Load", "${telemetry.cpuLoadPercent}% (${telemetry.cpuSource})", sCpu, activeAccent) { sCpu = it; Prefs.showCpu = it; pushOverlayUpdate() }
                            SensorToggleRow("CPU Peak Clock", String.format(Locale.US, "%.2f GHz", telemetry.cpuMaxGhz), sClock, activeAccent) { sClock = it; Prefs.showClock = it; pushOverlayUpdate() }
                            SensorToggleRow("GPU Load", telemetry.gpuLoadPercent?.let { "$it%" } ?: "N/A", sGpu, activeAccent) { sGpu = it; Prefs.showGpu = it; pushOverlayUpdate() }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("MEMORI, BATERAI & JARINGAN")
                            SensorToggleRow("RAM Terpakai", String.format(Locale.US, "%.1f GB", telemetry.ramUsedGb), sRam, activeAccent) { sRam = it; Prefs.showRam = it; pushOverlayUpdate() }
                            SensorToggleRow("Suhu Baterai", String.format(Locale.US, "%.1f °C", telemetry.batteryTempC), sTemp, activeAccent) { sTemp = it; Prefs.showTemp = it; pushOverlayUpdate() }
                            SensorToggleRow("Persentase Baterai", "${telemetry.batteryPercent}%", sBatt, activeAccent) { sBatt = it; Prefs.showBatteryPct = it; pushOverlayUpdate() }
                            SensorToggleRow("Konsumsi Daya", String.format(Locale.US, "%.1f W", telemetry.powerWatts), sWatt, activeAccent) { sWatt = it; Prefs.showWatt = it; pushOverlayUpdate() }
                            SensorToggleRow("Kecepatan Download", "↓ ${telemetry.netDownKbps.toInt()} KB/s", sNet, activeAccent) { sNet = it; Prefs.showNet = it; pushOverlayUpdate() }
                        }
                    }
                }

                else -> {
                    item {
                        CyberCard {
                            SectionTitle("1. IZIN FLOATING OVERLAY", if (overlayGranted) "AKTIF" else "WAJIB", if (overlayGranted) EmeraldNeon else AmberVolt)
                            Text(
                                text = "Diperlukan agar HUD FramePulse dapat melayang di atas game dan aplikasi lain.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text(if (overlayGranted) "Izin Overlay Sudah Aktif ✓" else "Buka Pengaturan Izin Overlay", color = TextPrimary)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("2. SHIZUKU BRIDGE (TRUE FPS & GPU)", if (shizukuConnected) "TERHUBUNG" else "OPSIONAL", if (shizukuConnected) EmeraldNeon else CyberCyan)
                            Text(
                                text = "Menghubungkan ke SurfaceFlinger Android untuk membaca FPS asli game, True 1% Low, serta bypass blokir SELinux CPU/GPU tanpa root.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
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
                                    label = if (shizukuConnected) "Izin: Granted" else "Izin: Belum",
                                    color = if (shizukuConnected) EmeraldNeon else AmberVolt
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    try {
                                        if (!Shizuku.pingBinder()) {
                                            Toast.makeText(context, "Binder belum masuk. Tutup FramePulse dari Recent Apps lalu buka ulang (pastikan Shizuku running).", Toast.LENGTH_LONG).show()
                                        } else if (Shizuku.isPreV11()) {
                                            Toast.makeText(context, "Versi Shizuku terlalu lama. Update Shizuku dulu.", Toast.LENGTH_LONG).show()
                                        } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                            Shizuku.requestPermission(100)
                                        } else {
                                            shizukuConnected = true
                                            Toast.makeText(context, "Shizuku sudah terhubung!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (t: Throwable) {
                                        Toast.makeText(context, "Shizuku error: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                            ) {
                                Text(if (shizukuConnected) "Shizuku Terhubung ✓" else "Hubungkan Izin Shizuku", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("3. QUICK SETTINGS TILE (ONE UI)", "FITUR PRO", CyberCyan)
                            Text(
                                text = "Tarik panel notifikasi dari atas layar -> tap ikon Pensil (Edit tombol) -> seret ubin 'FramePulse HUD' ke panel cepat agar bisa menyalakan/mematikan HUD kapan saja saat main game.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("4. DIAGNOSTIK & CRASH GUARD", if (lastCrashLog.isBlank()) "BERSIH" else "ADA LOG", if (lastCrashLog.isBlank()) EmeraldNeon else CrimsonAccent)
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
                                        Toast.makeText(context, "Info SurfaceFlinger disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text("Salin Info SurfaceFlinger (Debug FPS)", color = TextPrimary, fontSize = 12.sp)
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
                                            Toast.makeText(context, "Log crash disalin", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                                    ) {
                                        Text("Salin Log", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            Prefs.lastCrashLog = ""
                                            lastCrashLog = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        Text("Hapus Log", color = TextPrimary, fontSize = 12.sp)
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
fun MetricMiniCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
        )
    }
}

@Composable
fun SensorToggleRow(label: String, liveValue: String, checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(liveValue, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accent)
        )
    }
}
