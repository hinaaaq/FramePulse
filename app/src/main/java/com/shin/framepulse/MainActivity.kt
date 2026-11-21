package com.shin.framepulse

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
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
import rikka.shizuku.ShizukuProvider
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku aktif: True Game FPS siap!", Toast.LENGTH_SHORT).show()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        try {
            ShizukuProvider.requestBinderForNonProviderProcess(this)
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
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
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
    var shizukuConnected by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(TelemetryHub.isRecording) }
    var lastBenchmark by remember { mutableStateOf(TelemetryHub.lastSummary) }

    var layoutMode by remember { mutableStateOf(Prefs.layoutMode) }
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var bgAlpha by remember { mutableIntStateOf(Prefs.bgAlpha) }
    var textSizeSp by remember { mutableFloatStateOf(Prefs.textSizeSp) }
    var intervalMs by remember { mutableLongStateOf(Prefs.updateIntervalMs) }
    var showHudGraph by remember { mutableStateOf(Prefs.showHudGraph) }

    var sFps by remember { mutableStateOf(Prefs.showFps) }
    var sFrameTime by remember { mutableStateOf(Prefs.showFrameTime) }
    var sCpu by remember { mutableStateOf(Prefs.showCpu) }
    var sClock by remember { mutableStateOf(Prefs.showClock) }
    var sGpu by remember { mutableStateOf(Prefs.showGpu) }
    var sRam by remember { mutableStateOf(Prefs.showRam) }
    var sTemp by remember { mutableStateOf(Prefs.showTemp) }
    var sWatt by remember { mutableStateOf(Prefs.showWatt) }
    var sNet by remember { mutableStateOf(Prefs.showNet) }

    val activeAccent = when (themeMode) {
        Prefs.THEME_EMERALD -> EmeraldNeon
        Prefs.THEME_CRIMSON -> CrimsonAccent
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
                    shizukuConnected = localMonitor.isShizukuReady()
                    if (OverlayService.isRunning) localMonitor.stop() else localMonitor.start()
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
            if (running) localMonitor.stop()
            val data = if (running) {
                TelemetryHub.latest
            } else {
                withContext(Dispatchers.IO) { localMonitor.collect() }
            }
            telemetry = data
            fpsHistory = synchronized(TelemetryHub.fpsHistory) { TelemetryHub.fpsHistory.toList() }
            serviceRunning = running
            touchLocked = OverlayService.isTouchLocked
            isRecording = TelemetryHub.isRecording
            lastBenchmark = TelemetryHub.lastSummary
            shizukuConnected = localMonitor.isShizukuReady()
            delay(500L)
        }
    }
    Scaffold(
        containerColor = BgObsidian,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A101D))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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
                                .clip(RoundedCornerShape(5.dp))
                                .background(activeAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("PRO", color = activeAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = if (shizukuConnected) "Engine: SurfaceFlinger True FPS" else "Engine: Standard Sensor Mode",
                        color = if (shizukuConnected) EmeraldNeon else TextSecondary,
                        fontSize = 11.sp
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
                containerColor = Color(0xFF0A101D),
                tonalElevation = 0.dp
            ) {
                val tabs = listOf("Monitor", "HUD Studio", "Sensors", "Setup")
                tabs.forEachIndexed { idx, label ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = {
                            val badgeChar = when (idx) {
                                0 -> "⚡"
                                1 -> "🎛"
                                2 -> "📊"
                                else -> "⚙"
                            }
                            Text(badgeChar, fontSize = 16.sp)
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = activeAccent,
                            selectedTextColor = activeAccent,
                            indicatorColor = activeAccent.copy(alpha = 0.15f),
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
                                        color = if (telemetry.fps >= 50) EmeraldNeon else AmberVolt,
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = " FPS",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs),
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text("Frame Time", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            ComposeSparkline(points = fpsHistory, accent = activeAccent)
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle(
                                title = "SESSION BENCHMARK RECORDER",
                                badge = if (isRecording) "● RECORDING" else "READY",
                                accent = if (isRecording) CrimsonAccent else EmeraldNeon
                            )
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        lastBenchmark = TelemetryHub.stopRecording()
                                        isRecording = false
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
                                    text = if (isRecording) "STOP & HITUNG HASIL BENCHMARK" else "MULAI REKAM SESI GAME (AVG & 1% LOW)",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            lastBenchmark?.let { res ->
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricMiniCol("AVG FPS", String.format(Locale.US, "%.1f", res.avgFps), EmeraldNeon)
                                    MetricMiniCol("1% LOW", "${res.onePercentLowFps}", AmberVolt)
                                    MetricMiniCol("MIN/MAX", "${res.minFps}/${res.maxFps}", CyberCyan)
                                    MetricMiniCol("PEAK °C", String.format(Locale.US, "%.1f°", res.peakTempC), CrimsonAccent)
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
                                Text("CPU & GPU LOAD", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "CPU ${telemetry.cpuLoadPercent}%",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "GPU ${telemetry.gpuLoadPercent?.let { "$it%" } ?: "N/A"} • ${String.format(Locale.US, "%.2f", telemetry.cpuMaxGhz)}GHz",
                                    color = PurpleGpu,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            CyberCard(modifier = Modifier.weight(1f)) {
                                Text("THERMAL & POWER", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f °C", telemetry.batteryTempC),
                                    color = if (telemetry.batteryTempC >= 42f) CrimsonAccent else CyberCyan,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f W • %d mA", telemetry.powerWatts, telemetry.currentMa),
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
                                    color = Color(0xFFF472B6),
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
                                SectionTitle("8-CORE CPU FREQUENCY MATRIX", "BIG.LITTLE", activeAccent)
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
                                                        text = "C${core.coreIndex} ${if (core.isBigCore) "A76" else "A55"}",
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
                            SectionTitle("LIVE HUD PREVIEW", layoutMode, activeAccent)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF04070D))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF090E1A).copy(alpha = bgAlpha / 255f))
                                        .border(
                                            1.dp,
                                            if (bgAlpha > 20) activeAccent.copy(alpha = 0.5f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "FPS ${telemetry.fps} │ CPU ${telemetry.cpuLoadPercent}% │ ${String.format(Locale.US, "%.1f°C", telemetry.batteryTempC)}",
                                        color = activeAccent,
                                        fontSize = textSizeSp.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    item {
                        CyberCard {
                            SectionTitle("BENTUK & TEMA WARNA HUD")
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(Prefs.THEME_CYBER, "Cyber", CyberCyan),
                                    Triple(Prefs.THEME_EMERALD, "Emerald", EmeraldNeon),
                                    Triple(Prefs.THEME_CRIMSON, "Crimson", CrimsonAccent)
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
                            SectionTitle("TRANSPARANSI, UKURAN & REFRESH")
                            val pct = ((bgAlpha / 255f) * 100).roundToInt()
                            Text("Opacity Background: $pct% (0% = Teks Melayang Tanpa Kotak)", color = TextSecondary, fontSize = 12.sp)
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(250L to "0.25s (Cepat)", 500L to "0.5s (Normal)", 1000L to "1.0s (Hemat)").forEach { (ms, lbl) ->
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
                            ToggleRow("Tampilkan Grafik Mini di dalam HUD", showHudGraph, activeAccent) {
                                showHudGraph = it
                                Prefs.showHudGraph = it
                                pushOverlayUpdate()
                            }
                            ToggleRow("Kunci Sentuhan HUD (Touch-Through ke Game)", touchLocked, activeAccent) {
                                if (OverlayService.isRunning && OverlayService.isTouchLocked != it) {
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
                        CyberCard {
                            SectionTitle("PRESET POSISI LAYAR (ATAU GESER MANUAL)")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Kiri Atas", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = 24; Prefs.posY = 42; pushOverlayUpdate()
                                }
                                PillChip("Tengah Atas", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = (dm.widthPixels * 0.35f).toInt(); Prefs.posY = 42; pushOverlayUpdate()
                                }
                                PillChip("Kanan Atas", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = (dm.widthPixels * 0.65f).toInt(); Prefs.posY = 42; pushOverlayUpdate()
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Kiri Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = 24; Prefs.posY = (dm.heightPixels * 0.85f).toInt(); pushOverlayUpdate()
                                }
                                PillChip("Tengah Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = (dm.widthPixels * 0.35f).toInt(); Prefs.posY = (dm.heightPixels * 0.85f).toInt(); pushOverlayUpdate()
                                }
                                PillChip("Kanan Bawah", false, activeAccent, Modifier.weight(1f)) {
                                    Prefs.posX = (dm.widthPixels * 0.65f).toInt(); Prefs.posY = (dm.heightPixels * 0.85f).toInt(); pushOverlayUpdate()
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        CyberCard {
                            SectionTitle("PILIH DATA SENSOR DI OVERLAY")
                            ToggleRow("FPS (Frame Per Second)", sFps, activeAccent) { sFps = it; Prefs.showFps = it; pushOverlayUpdate() }
                            ToggleRow("Frame Time (ms)", sFrameTime, activeAccent) { sFrameTime = it; Prefs.showFrameTime = it; pushOverlayUpdate() }
                            ToggleRow("CPU Total Load (%)", sCpu, activeAccent) { sCpu = it; Prefs.showCpu = it; pushOverlayUpdate() }
                            ToggleRow("CPU Peak Clock (GHz)", sClock, activeAccent) { sClock = it; Prefs.showClock = it; pushOverlayUpdate() }
                            ToggleRow("GPU Load (%)", sGpu, activeAccent) { sGpu = it; Prefs.showGpu = it; pushOverlayUpdate() }
                            ToggleRow("RAM Terpakai (GB)", sRam, activeAccent) { sRam = it; Prefs.showRam = it; pushOverlayUpdate() }
                            ToggleRow("Suhu Baterai (°C)", sTemp, activeAccent) { sTemp = it; Prefs.showTemp = it; pushOverlayUpdate() }
                            ToggleRow("Konsumsi Daya (Watt)", sWatt, activeAccent) { sWatt = it; Prefs.showWatt = it; pushOverlayUpdate() }
                            ToggleRow("Kecepatan Download (KB/s)", sNet, activeAccent) { sNet = it; Prefs.showNet = it; pushOverlayUpdate() }
                        }
                    }
                }

                else -> {
                    item {
                        CyberCard {
                            SectionTitle("1. IZIN FLOATING OVERLAY", if (overlayGranted) "AKTIF" else "WAJIB", activeAccent)
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
                            SectionTitle("2. SHIZUKU BRIDGE (TRUE GAME FPS)", if (shizukuConnected) "TERHUBUNG" else "OPSIONAL", EmeraldNeon)
                            Text(
                                text = "Menghubungkan ke SurfaceFlinger Android untuk membaca FPS asli game & 1% Low tanpa root.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    try {
                                        if (!Shizuku.pingBinder()) {
                                            ShizukuProvider.requestBinderForNonProviderProcess(context)
                                        }
                                        if (!Shizuku.pingBinder()) {
                                            Toast.makeText(context, "Binder Shizuku belum merespons. Pastikan Shizuku aktif lalu coba tap lagi.", Toast.LENGTH_LONG).show()
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
                                Text(if (shizukuConnected) "Shizuku Terhubung ✓" else "Hubungkan Izin Shizuku", color = TextPrimary)
                            }
                        }
                    }

                    item {
                        CyberCard {
                            SectionTitle("3. QUICK SETTINGS TILE (ONE UI)", "FITUR PRO", CyberCyan)
                            Text(
                                text = "Tarik panel notifikasi Samsung dari atas layar -> tap ikon Pensil (Edit tombol) -> seret ubin 'FramePulse HUD' ke panel cepat agar bisa menyalakan/mematikan HUD kapan saja saat main game.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
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
