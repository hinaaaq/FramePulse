package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class CoreInfo(
    val coreIndex: Int,
    val curMhz: Int,
    val maxMhz: Int,
    val loadPercent: Int,
    val isBigCore: Boolean
)

data class TelemetryData(
    val fps: Int = 0,
    val frameTimeMs: Float = 0f,
    val isTrueGameFps: Boolean = false,
    val activeSurfaceName: String = "",
    val displayRefreshHz: Int = 60,
    val cpuLoadPercent: Int = 0,
    val cpuSource: String = "FREQ",
    val cpuMaxGhz: Float = 0f,
    val gpuLoadPercent: Int? = null,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val powerWatts: Float = 0f,
    val currentMa: Int = 0,
    val netDownKbps: Float = 0f,
    val netUpKbps: Float = 0f,
    val cores: List<CoreInfo> = emptyList()
)

data class BenchmarkSummary(
    val timestamp: String,
    val durationSec: Int,
    val avgFps: Float,
    val onePercentLowFps: Int,
    val minFps: Int,
    val maxFps: Int,
    val peakTempC: Float,
    val sourceLabel: String
)

object TelemetryHub {
    @Volatile var latest: TelemetryData = TelemetryData()
    val fpsHistory: MutableList<Int> = mutableListOf()

    @Volatile var isRecording: Boolean = false
    @Volatile var recordingStartMs: Long = 0L
    @Volatile var lastSummary: BenchmarkSummary? = null
    @Volatile var isAppForeground: Boolean = true
    @Volatile var activeLockedLayer: String? = null
    @Volatile var activeForegroundPkg: String? = null

    private val recordedFps = mutableListOf<Int>()
    private val recordedFrameTimesMs = mutableListOf<Float>()
    private var peakRecordedTemp = 0f
    private var usedTrueFpsInSession = false

    fun getRecordingElapsedSec(): Int {
        if (!isRecording || recordingStartMs <= 0L) return 0
        return ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(0)
    }

    fun startRecording() {
        synchronized(recordedFps) {
            recordedFps.clear()
            recordedFrameTimesMs.clear()
            peakRecordedTemp = 0f
            usedTrueFpsInSession = false
            recordingStartMs = SystemClock.elapsedRealtime()
            isRecording = true
        }
    }

    fun recordFrameTimes(frameTimesMs: List<Float>) {
        if (!isRecording || frameTimesMs.isEmpty()) return
        synchronized(recordedFps) {
            if (!isRecording) return
            usedTrueFpsInSession = true
            for (ft in frameTimesMs) {
                if (ft in 2f..250f) {
                    recordedFrameTimesMs.add(ft)
                }
            }
            if (recordedFrameTimesMs.size > 12000) {
                recordedFrameTimesMs.subList(0, recordedFrameTimesMs.size - 12000).clear()
            }
        }
    }

    fun stopRecording(): BenchmarkSummary? {
        synchronized(recordedFps) {
            isRecording = false
            val duration = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
            recordingStartMs = 0L
            if (recordedFps.isEmpty() && recordedFrameTimesMs.isEmpty()) return null

            val avg: Float
            val onePctLow: Int
            val minFps: Int
            val maxFps: Int

            if (recordedFrameTimesMs.size >= 15) {
                val sortedFt = recordedFrameTimesMs.sorted()
                val avgFt = sortedFt.average().toFloat().coerceAtLeast(1f)
                avg = (1000f / avgFt).coerceIn(1f, 144f)
                val worstCount = maxOf(1, (sortedFt.size * 0.01).toInt())
                val worstAvgFt = sortedFt.takeLast(worstCount).average().toFloat().coerceAtLeast(1f)
                onePctLow = (1000f / worstAvgFt).roundToInt().coerceIn(1, 144)
                minFps = (1000f / sortedFt.last().coerceAtLeast(1f)).roundToInt().coerceIn(1, 144)
                maxFps = (1000f / sortedFt.first().coerceAtLeast(1f)).roundToInt().coerceIn(1, 144)
            } else {
                val sorted = recordedFps.sorted()
                avg = sorted.average().toFloat()
                minFps = sorted.first()
                maxFps = sorted.last()
                val onePctCount = maxOf(1, (sorted.size * 0.01).toInt())
                onePctLow = sorted.take(onePctCount).average().roundToInt()
            }

            val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date())
            val summary = BenchmarkSummary(
                timestamp = ts,
                durationSec = duration,
                avgFps = avg,
                onePercentLowFps = onePctLow,
                minFps = minFps,
                maxFps = maxFps,
                peakTempC = peakRecordedTemp,
                sourceLabel = if (usedTrueFpsInSession) "SF" else "STD"
            )
            lastSummary = summary
            saveHistoryItem(summary)
            return summary
        }
    }

    fun loadHistory(): List<BenchmarkSummary> {
        val raw = try { Prefs.benchmarkHistoryRaw } catch (_: Throwable) { "" }
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 8) return@mapNotNull null
            try {
                BenchmarkSummary(
                    timestamp = p[0],
                    durationSec = p[1].toInt(),
                    avgFps = p[2].toFloat(),
                    onePercentLowFps = p[3].toInt(),
                    minFps = p[4].toInt(),
                    maxFps = p[5].toInt(),
                    peakTempC = p[6].toFloat(),
                    sourceLabel = p[7]
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun clearHistory() {
        lastSummary = null
        try { Prefs.benchmarkHistoryRaw = "" } catch (_: Throwable) {}
    }

    private fun saveHistoryItem(item: BenchmarkSummary) {
        try {
            val current = loadHistory().toMutableList()
            current.add(0, item)
            val trimmed = current.take(8)
            val serialized = trimmed.joinToString("\n") {
                listOf(
                    it.timestamp,
                    it.durationSec.toString(),
                    String.format(Locale.US, "%.1f", it.avgFps),
                    it.onePercentLowFps.toString(),
                    it.minFps.toString(),
                    it.maxFps.toString(),
                    String.format(Locale.US, "%.1f", it.peakTempC),
                    it.sourceLabel
                ).joinToString("|")
            }
            Prefs.benchmarkHistoryRaw = serialized
        } catch (_: Throwable) {}
    }

    fun publish(data: TelemetryData) {
        latest = data
        synchronized(fpsHistory) {
            fpsHistory.add(data.fps)
            if (fpsHistory.size > 60) {
                fpsHistory.removeAt(0)
            }
        }
        if (isRecording && data.fps > 0) {
            synchronized(recordedFps) {
                if (isRecording) {
                    recordedFps.add(data.fps)
                    if (data.batteryTempC > peakRecordedTemp) {
                        peakRecordedTemp = data.batteryTempC
                    }
                }
            }
        }
    }
}

class SystemMonitor(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shellLock = Any()

    @Volatile private var running = false
    @Volatile private var choreographerActive = false
    private var frameCount = 0
    private var lastChoreoNs = 0L
    private var choreoFps = 0
    private var choreoFrameMs = 0f

    @Volatile private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null
    private var activeGameLayer: String? = null
    private var activeSurfaceLabel: String = ""
    private var activeForegroundPkg: String? = null
    private var lastLayerScanMs = 0L
    private var lastFocusCheckMs = 0L
    private var lastPresentNs = 0L
    private var lastSfQueryMs = 0L
    private var sfFps = 0
    private var sfFrameMs = 0f
    private val pkgRegex = Regex("""\b([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/""")

    private var lastCpuQueryMs = 0L
    private var cachedCpuLoad = 0
    private var cachedCpuSource = "FREQ"
    private var cachedCpuMaxGhz = 0f
    private var cachedGpuLoad: Int? = null
    private var discoveredGpuPath: String? = null
    private var gpuScanAttempts = 0
    private var lastAutoBoostedPkg: String? = null
    private var cachedCores: List<CoreInfo> = emptyList()
    private var prevTotalCpu = 0L
    private var prevIdleCpu = 0L

    private var lastSlowQueryMs = 0L
    private var cachedRamUsed = 0f
    private var cachedRamTotal = 0f
    private var cachedTempC = 0f
    private var cachedBattPct = 0
    private var cachedIsCharging = false
    private var cachedWatts = 0f
    private var cachedCurrentMa = 0
    private var cachedDownKbps = 0f
    private var cachedUpKbps = 0f
    private var prevRxBytes = -1L
    private var prevTxBytes = -1L
    private var prevNetMs = 0L

    private val coreCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)

    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !choreographerActive) return
            if (lastChoreoNs == 0L) {
                lastChoreoNs = frameTimeNanos
            }
            frameCount++
            val elapsedNs = frameTimeNanos - lastChoreoNs
            if (elapsedNs >= 500_000_000L) {
                val secs = elapsedNs / 1_000_000_000f
                val maxHz = detectDisplayRefreshHz()
                choreoFps = (frameCount / secs).roundToInt().coerceIn(0, maxHz)
                choreoFrameMs = if (choreoFps > 0) 1000f / choreoFps else 0f
                frameCount = 0
                lastChoreoNs = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        setChoreographerEnabled(true)
    }

    fun isRunning(): Boolean = running

    fun stop() {
        if (!running && shellProcess == null) return
        running = false
        setChoreographerEnabled(false)
        closeShell()
    }

    private fun setChoreographerEnabled(enable: Boolean) {
        if (choreographerActive == enable) return
        choreographerActive = enable
        if (enable) {
            frameCount = 0
            lastChoreoNs = 0L
            mainHandler.post {
                if (running && choreographerActive) {
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }
        }
    }

    fun detectDisplayRefreshHz(): Int {
        val rate = try {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
        } catch (_: Throwable) {
            60f
        }
        return rate.roundToInt().coerceIn(60, 144)
    }

    fun isShizukuBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun ensureShell(): Boolean = synchronized(shellLock) {
        if (!isShizukuReady()) {
            closeShellInternal()
            return false
        }
        val proc = shellProcess
        if (proc != null) {
            return try {
                proc.exitValue()
                closeShellInternal()
                openShellInternal()
            } catch (_: IllegalThreadStateException) {
                true
            } catch (e: IllegalArgumentException) {
                // Over Android Binder IPC, IllegalThreadStateException is deserialized as IllegalArgumentException
                if (e.message?.contains("hasn't exited", ignoreCase = true) == true) {
                    true
                } else {
                    closeShellInternal()
                    openShellInternal()
                }
            } catch (_: Throwable) {
                closeShellInternal()
                openShellInternal()
            }
        }
        return openShellInternal()
    }

    private fun openShellInternal(): Boolean {
        return try {
            val proc = newProcessMethod.invoke(null, arrayOf("sh"), null, null) as Process
            shellProcess = proc
            shellWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
            shellReader = BufferedReader(InputStreamReader(proc.inputStream))
            true
        } catch (_: Throwable) {
            closeShellInternal()
            false
        }
    }

    private fun closeShell() {
        // Destroy process first outside lock so any blocked readLine() unblocks immediately without UI ANR
        val proc = shellProcess
        if (proc != null) {
            try { proc.destroy() } catch (_: Throwable) {}
        }
        synchronized(shellLock) {
            closeShellInternal()
        }
    }

    private fun closeShellInternal() {
        try { shellWriter?.close() } catch (_: Throwable) {}
        try { shellReader?.close() } catch (_: Throwable) {}
        try { shellProcess?.destroy() } catch (_: Throwable) {}
        shellWriter = null
        shellReader = null
        shellProcess = null
        activeGameLayer = null
        activeSurfaceLabel = ""
        activeForegroundPkg = null
    }

    private fun runShellCmd(cmd: String): List<String> = synchronized(shellLock) {
        val writer = shellWriter ?: return emptyList()
        val reader = shellReader ?: return emptyList()
        return try {
            writer.write("{ $cmd ; } 2>/dev/null\nprintf '\\n__FP_END__\\n'\n")
            writer.flush()
            val out = ArrayList<String>(132)
            while (true) {
                val line = reader.readLine() ?: break
                if (line == "__FP_END__") break
                out.add(line)
            }
            out
        } catch (_: Throwable) {
            closeShellInternal()
            emptyList()
        }
    }

    private fun extractSurfaceLabel(layer: String): String {
        val bracketIdx = layer.indexOf('[')
        val endBracket = layer.indexOf(']')
        val core = if (bracketIdx >= 0 && endBracket > bracketIdx) {
            layer.substring(bracketIdx + 1, endBracket)
        } else {
            layer
        }
        val pkg = core.substringBefore('/').substringBefore('#').trim()
        val shortName = pkg.substringAfterLast('.').ifBlank { pkg }
        return shortName.take(22)
    }

    private fun detectForegroundPackage(): String? {
        val ignoredPkgs = setOf(
            "com.shin.framepulse",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.game.gametools",
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin"
        )
        fun extractFromLines(lines: List<String>, preferPrefix: String? = null): String? {
            for (line in lines) {
                if (preferPrefix != null && !line.contains(preferPrefix)) continue
                val match = pkgRegex.find(line) ?: continue
                val pkg = match.groupValues[1]
                if (pkg !in ignoredPkgs && !pkg.startsWith("com.android.systemui")) {
                    return pkg
                }
            }
            return null
        }
        // Prefer mFocusedApp (actual fullscreen Activity/Game) before mCurrentFocus (which can be a floating bubble/IME)
        val winLines = runShellCmd("dumpsys window displays | grep -E 'mFocusedApp|mCurrentFocus'")
        extractFromLines(winLines, "mFocusedApp")?.let { return it }
        val actLines = runShellCmd("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
        extractFromLines(actLines)?.let { return it }
        return extractFromLines(winLines, "mCurrentFocus")
    }

    private fun parseLatencyTimestamps(lines: List<String>): List<Long> {
        if (lines.size < 3) return emptyList()
        val raw = ArrayList<Long>(128)
        for (i in 1 until lines.size) {
            val parts = lines[i].split('\t')
            if (parts.size >= 2) {
                val presentNs = parts[1].trim().toLongOrNull() ?: 0L
                if (presentNs > 0L && presentNs < Long.MAX_VALUE / 2) {
                    raw.add(presentNs)
                }
            }
        }
        if (raw.isEmpty()) return emptyList()
        raw.sort()
        val dedup = ArrayList<Long>(raw.size)
        var prev = -1L
        for (ts in raw) {
            if (ts != prev) {
                dedup.add(ts)
                prev = ts
            }
        }
        return dedup
    }

    private fun isExcludedLayer(line: String): Boolean {
        val excluded = listOf(
            "Background for", "Bounds for", "Container", "RelativeParent",
            "FramePulse", "com.shin.framepulse", "NavigationBar", "StatusBar",
            "ScreenDecor", "Wallpaper", "NotificationShade", "InputMethod",
            "Taskbar", "Dim Layer", "Letterbox", "Transition", "Splash Screen",
            "cocktailbarservice", "gametools", "systemui", "launcher",
            "edgepanel", "smartcapture", "Screenshot", "Toast", "PopupWindow",
            "assist", "floating", "pip"
        )
        return excluded.any { bad -> line.contains(bad, ignoreCase = true) }
    }

    private fun scanBestActiveLayer(): Pair<String, List<Long>>? {
        val fgPkg = detectForegroundPackage()
        activeForegroundPkg = fgPkg
        TelemetryHub.activeForegroundPkg = fgPkg
        val allLayers = runShellCmd("dumpsys SurfaceFlinger --list")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isExcludedLayer(it) }
        if (allLayers.isEmpty()) return null

        val candidates = LinkedHashSet<String>()
        if (fgPkg != null) {
            allLayers.filter { it.contains(fgPkg) && it.contains("SurfaceView") && it.contains("BLAST") }.asReversed().forEach { candidates.add(it) }
            allLayers.filter { it.contains(fgPkg) && it.contains("SurfaceView") }.asReversed().forEach { candidates.add(it) }
            allLayers.filter { it.contains(fgPkg) && it.contains("BLAST") }.asReversed().forEach { candidates.add(it) }
            allLayers.filter { it.contains(fgPkg) }.asReversed().forEach { candidates.add(it) }
        }
        allLayers.filter { it.contains("SurfaceView") && it.contains("BLAST") }.asReversed().forEach { candidates.add(it) }
        allLayers.filter { it.contains("SurfaceView") }.asReversed().forEach { candidates.add(it) }
        if (candidates.isEmpty()) {
            allLayers.filter { it.contains("BLAST") }.asReversed().take(4).forEach { candidates.add(it) }
        }

        val monoNs = System.nanoTime()
        val bootNs = SystemClock.elapsedRealtimeNanos()
        var bestLayer: String? = null
        var bestTimestamps: List<Long> = emptyList()
        var bestNewestNs = 0L
        var bestIsFg = false
        var bestIsFresh = false

        for (cand in candidates.take(8)) {
            val safe = cand.replace("'", "'\\''")
            val ts = parseLatencyTimestamps(runShellCmd("dumpsys SurfaceFlinger --latency '$safe'"))
            if (ts.size < 2) continue
            val newest = ts.last()
            val isFg = fgPkg != null && cand.contains(fgPkg)
            val isFresh = (abs(monoNs - newest) <= 2_000_000_000L) || (abs(bootNs - newest) <= 2_000_000_000L)

            val isBetter = when {
                bestLayer == null -> true
                isFresh && !bestIsFresh -> true
                !isFresh && bestIsFresh -> false
                isFg && !bestIsFg -> newest >= bestNewestNs - 500_000_000L
                !isFg && bestIsFg -> newest > bestNewestNs + 2_000_000_000L
                else -> newest > bestNewestNs
            }
            if (isBetter) {
                bestLayer = cand
                bestTimestamps = ts
                bestNewestNs = newest
                bestIsFg = isFg
                bestIsFresh = isFresh
                // Only fast-break when a foreground SurfaceView is confirmed actively rendering right now
                if (isFg && isFresh && cand.contains("SurfaceView") && ts.size >= 5) break
            }
        }
        return if (bestLayer != null) bestLayer to bestTimestamps else null
    }

    fun dumpSurfaceFlingerDiagnostics(): String {
        if (!isShizukuReady()) return "Shizuku belum aktif / izin belum diberikan."
        val useOwnShell = !ensureShell()
        if (useOwnShell) return "Gagal membuka shell Shizuku."
        return buildString {
            val fg = detectForegroundPackage() ?: TelemetryHub.activeForegroundPkg
            val locked = activeGameLayer ?: TelemetryHub.activeLockedLayer
            appendLine("Foreground Pkg: ${fg ?: "(none)"}")
            appendLine("Locked Layer: ${locked ?: "(none)"}")
            appendLine("Display Hz: ${detectDisplayRefreshHz()}Hz")
            appendLine("Latest HUD FPS: ${TelemetryHub.latest.fps} (isTrue=${TelemetryHub.latest.isTrueGameFps})")
            appendLine("CPU Source: ${TelemetryHub.latest.cpuSource} | GPU: ${TelemetryHub.latest.gpuLoadPercent ?: "N/A"}")
            appendLine("--- Focus ---")
            runShellCmd("dumpsys window displays | grep -E 'mCurrentFocus|mFocusedApp'").take(4).forEach { appendLine(it.trim()) }
            appendLine("--- SurfaceFlinger Layers (filtered) ---")
            val layers = runShellCmd("dumpsys SurfaceFlinger --list")
                .map { it.trim() }
                .filter { it.contains("SurfaceView") || it.contains("BLAST") || (fg != null && it.contains(fg)) }
            layers.takeLast(15).forEach { appendLine(it) }
            locked?.let { layer ->
                appendLine("--- Latency Sample ---")
                val safe = layer.replace("'", "'\\''")
                runShellCmd("dumpsys SurfaceFlinger --latency '$safe'").take(8).forEach { appendLine(it) }
            }
        }
    }

    private fun querySurfaceFlingerFps(nowMs: Long, maxHz: Int): Boolean {
        if (!ensureShell()) return false

        var validTimestamps: List<Long>? = null
        val lockedLayer = activeGameLayer
        val layerSilent = lastSfQueryMs > 0L && (nowMs - lastSfQueryMs >= 1500L)

        var needFullScan = lockedLayer == null || layerSilent

        // While locked game layer is actively producing frames, minimize system_server overhead (re-check every 12s)
        if (!needFullScan && lockedLayer != null && (nowMs - lastFocusCheckMs >= 12000L)) {
            lastFocusCheckMs = nowMs
            val currentFg = detectForegroundPackage()
            if (currentFg != null) {
                activeForegroundPkg = currentFg
                TelemetryHub.activeForegroundPkg = currentFg
                if (!lockedLayer.contains(currentFg)) {
                    needFullScan = true
                }
            }
        }

        if (needFullScan && (lockedLayer == null || nowMs - lastLayerScanMs >= 1500L)) {
            lastLayerScanMs = nowMs
            lastFocusCheckMs = nowMs
            val scanned = scanBestActiveLayer()
            if (scanned != null) {
                val (foundLayer, foundTs) = scanned
                if (foundLayer != activeGameLayer) {
                    activeGameLayer = foundLayer
                    activeSurfaceLabel = extractSurfaceLabel(foundLayer)
                    TelemetryHub.activeLockedLayer = foundLayer
                    lastPresentNs = 0L
                    lastSfQueryMs = 0L
                    val detectedPkg = activeForegroundPkg
                    if (!detectedPkg.isNullOrBlank() &&
                        foundLayer.contains("SurfaceView") &&
                        detectedPkg.matches(Regex("^[a-zA-Z0-9_.]+$")) &&
                        detectedPkg != "com.shin.framepulse"
                    ) {
                        Prefs.lastGamePackage = detectedPkg
                        if (Prefs.turboAutoBoost && lastAutoBoostedPkg != detectedPkg) {
                            lastAutoBoostedPkg = detectedPkg
                            runShellCmd("cmd game mode performance '$detectedPkg'")
                        }
                    }
                }
                validTimestamps = foundTs
            } else {
                activeGameLayer = null
                activeSurfaceLabel = ""
                TelemetryHub.activeLockedLayer = null
                return false
            }
        }

        val layer = activeGameLayer ?: return false
        if (validTimestamps == null) {
            val safeLayer = layer.replace("'", "'\\''")
            validTimestamps = parseLatencyTimestamps(runShellCmd("dumpsys SurfaceFlinger --latency '$safeLayer'"))
            if (validTimestamps.isEmpty()) {
                activeGameLayer = null
                activeSurfaceLabel = ""
                TelemetryHub.activeLockedLayer = null
                return false
            }
        }

        val newestNs = validTimestamps.last()

        if (lastPresentNs == 0L) {
            lastPresentNs = newestNs
            lastSfQueryMs = nowMs
            val window = validTimestamps.filter { it >= newestNs - 1_000_000_000L }
            if (window.size >= 2) {
                val spanNs = window.last() - window.first()
                if (spanNs > 50_000_000L) {
                    val est = (((window.size - 1) * 1_000_000_000.0) / spanNs).roundToInt().coerceIn(1, maxHz)
                    sfFps = est
                    sfFrameMs = 1000f / est
                }
            }
            return true
        }

        if (newestNs <= lastPresentNs) {
            if (lastSfQueryMs > 0L && nowMs - lastSfQueryMs > 1200L) {
                sfFps = 0
                sfFrameMs = 0f
            }
            return true
        }

        val freshTimestamps = validTimestamps.filter { it > lastPresentNs }
        if (freshTimestamps.isNotEmpty()) {
            val frameTimes = ArrayList<Float>(freshTimestamps.size)
            var prev = lastPresentNs
            for (ts in freshTimestamps) {
                val dtMs = (ts - prev) / 1_000_000f
                if (dtMs in 2f..250f) {
                    frameTimes.add(dtMs)
                }
                prev = ts
            }
            if (frameTimes.isNotEmpty()) {
                TelemetryHub.recordFrameTimes(frameTimes)
            }

            // Rolling 1-second window over SurfaceFlinger timestamps for accurate, jitter-free FPS
            val window = validTimestamps.filter { it >= newestNs - 1_000_000_000L }
            val measured: Int? = if (window.size >= 2) {
                val spanNs = window.last() - window.first()
                if (spanNs > 50_000_000L) {
                    (((window.size - 1) * 1_000_000_000.0) / spanNs).roundToInt().coerceIn(1, maxHz)
                } else null
            } else {
                val deltaNs = newestNs - lastPresentNs
                if (deltaNs > 0L) {
                    ((freshTimestamps.size * 1_000_000_000.0) / deltaNs).roundToInt().coerceIn(1, maxHz)
                } else null
            }

            if (measured != null) {
                sfFps = measured
                sfFrameMs = if (frameTimes.isNotEmpty()) frameTimes.average().toFloat() else (1000f / measured)
                lastPresentNs = newestNs
                lastSfQueryMs = nowMs
            }
        }
        return true
    }

    private fun queryGfxInfoFps(nowMs: Long, maxHz: Int): Boolean {
        if (!ensureShell()) return false
        val pkg = detectForegroundPackage() ?: return false
        if (pkg == "com.shin.framepulse") return false
        val lines = runShellCmd("dumpsys gfxinfo '$pkg' framestats")
        if (lines.isEmpty()) return false
        val stamps = ArrayList<Long>(128)
        var inData = false
        for (line in lines) {
            val t = line.trim()
            if (t == "---PROFILEDATA---") {
                inData = !inData
                continue
            }
            if (!inData || t.startsWith("Flags")) continue
            val cols = t.split(',')
            if (cols.size >= 14) {
                val completed = cols[13].trim().toLongOrNull() ?: 0L
                if (completed > 0L && completed < Long.MAX_VALUE / 2) {
                    stamps.add(completed)
                }
            }
        }
        if (stamps.size < 3) return false
        stamps.sort()
        val newest = stamps.last()
        val window = stamps.filter { it > newest - 1_000_000_000L }
        if (window.size < 2) return false
        val spanNs = window.last() - window.first()
        if (spanNs <= 50_000_000L) return false
        val est = (((window.size - 1) * 1_000_000_000.0) / spanNs).roundToInt().coerceIn(1, maxHz)
        sfFps = est
        sfFrameMs = 1000f / est
        lastSfQueryMs = nowMs
        return true
    }

    fun collect(): TelemetryData {
        val nowMs = SystemClock.elapsedRealtime()
        val fg = TelemetryHub.isAppForeground
        val maxHz = detectDisplayRefreshHz()

        val trueFpsOk = querySurfaceFlingerFps(nowMs, maxHz) || queryGfxInfoFps(nowMs, maxHz)
        setChoreographerEnabled(!trueFpsOk)
        val fps = if (trueFpsOk) sfFps else choreoFps
        val frameMs = if (trueFpsOk) sfFrameMs else choreoFrameMs

        if (nowMs - lastCpuQueryMs >= 1000L || lastCpuQueryMs == 0L) {
            lastCpuQueryMs = nowMs
            if (fg || Prefs.showCpu || Prefs.showClock) {
                cachedCores = readAllCores()
                cachedCpuMaxGhz = (cachedCores.maxOfOrNull { it.curMhz } ?: 0) / 1000f
            }
            if (fg || Prefs.showCpu) cachedCpuLoad = readCpuLoad()
            if (fg || Prefs.showGpu) cachedGpuLoad = readGpuLoad()
        }

        if (nowMs - lastSlowQueryMs >= 2000L || lastSlowQueryMs == 0L) {
            lastSlowQueryMs = nowMs
            if (fg || Prefs.showRam) readRam()
            if (fg || Prefs.showTemp || Prefs.showWatt || Prefs.showBatteryPct) readBatteryAndThermal()
            if (fg || Prefs.showNet) readNetwork(nowMs)
        }

        val data = TelemetryData(
            fps = fps,
            frameTimeMs = frameMs,
            isTrueGameFps = trueFpsOk,
            activeSurfaceName = activeSurfaceLabel,
            displayRefreshHz = maxHz,
            cpuLoadPercent = cachedCpuLoad,
            cpuSource = cachedCpuSource,
            cpuMaxGhz = cachedCpuMaxGhz,
            gpuLoadPercent = cachedGpuLoad,
            ramUsedGb = cachedRamUsed,
            ramTotalGb = cachedRamTotal,
            batteryTempC = cachedTempC,
            batteryPercent = cachedBattPct,
            isCharging = cachedIsCharging,
            powerWatts = cachedWatts,
            currentMa = cachedCurrentMa,
            netDownKbps = cachedDownKbps,
            netUpKbps = cachedUpKbps,
            cores = if (fg) cachedCores else emptyList()
        )
        TelemetryHub.publish(data)
        return data
    }

    private fun parseProcStatLine(line: String): Int? {
        val toks = line.trim().split(Regex("\\s+"))
        if (toks.size < 5 || !toks[0].startsWith("cpu")) return null
        val user = toks[1].toLongOrNull() ?: return null
        val nice = toks[2].toLongOrNull() ?: return null
        val sys = toks[3].toLongOrNull() ?: return null
        val idle = toks[4].toLongOrNull() ?: return null
        val iowait = toks.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = toks.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = toks.getOrNull(7)?.toLongOrNull() ?: 0L
        val total = user + nice + sys + idle + iowait + irq + softirq
        val idleAll = idle + iowait
        val isFirstSample = (prevTotalCpu == 0L)
        val diffTotal = total - prevTotalCpu
        val diffIdle = idleAll - prevIdleCpu
        prevTotalCpu = total
        prevIdleCpu = idleAll
        if (isFirstSample) {
            return if (cachedCores.isNotEmpty()) {
                cachedCores.map { it.loadPercent }.average().roundToInt().coerceIn(0, 100)
            } else {
                cachedCpuLoad
            }
        }
        return if (diffTotal > 0L) {
            (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(0, 100)
        } else {
            cachedCpuLoad
        }
    }

    private fun readCpuLoad(): Int {
        try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() }
            if (!line.isNullOrBlank()) {
                parseProcStatLine(line)?.let {
                    cachedCpuSource = "PROC"
                    return it
                }
            }
        } catch (_: Throwable) {}

        if (ensureShell()) {
            val lines = runShellCmd("head -n 1 /proc/stat")
            val first = lines.firstOrNull()
            if (!first.isNullOrBlank()) {
                parseProcStatLine(first)?.let {
                    cachedCpuSource = "SHIZUKU"
                    return it
                }
            }
        }

        if (cachedCores.isNotEmpty()) {
            cachedCpuSource = "FREQ"
            return cachedCores.map { it.loadPercent }.average().roundToInt().coerceIn(0, 100)
        }
        return cachedCpuLoad
    }

    private fun readGpuLoad(): Int? {
        val candidatePaths = listOf(
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/class/misc/mali0/device/utilization",
            "/proc/mali/utilization",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/devices/platform/13000000.mali/utilization",
            "/sys/kernel/gpu/gpu_busy"
        )

        val pathsToTry = discoveredGpuPath?.let { listOf(it) } ?: candidatePaths
        for (path in pathsToTry) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim().split(Regex("[\\s%,]+")).firstOrNull()?.toIntOrNull()
                    if (raw != null) {
                        discoveredGpuPath = path
                        return raw.coerceIn(0, 100)
                    }
                }
            } catch (_: Throwable) {}
        }

        if (ensureShell()) {
            val known = discoveredGpuPath
            if (known != null) {
                val out = runShellCmd("cat '$known'")
                val parsed = out.firstOrNull()?.trim()?.split(Regex("[\\s%,]+"))?.firstOrNull()?.toIntOrNull()
                if (parsed != null) return parsed.coerceIn(0, 100)
            } else if (gpuScanAttempts < 3) {
                gpuScanAttempts++
                val cmd = "for p in " + candidatePaths.joinToString(" ") + "; do if [ -r \"\$p\" ]; then echo \"\$p\"; cat \"\$p\"; break; fi; done"
                val out = runShellCmd(cmd)
                if (out.size >= 2) {
                    val path = out[0].trim()
                    val parsed = out[1].trim().split(Regex("[\\s%,]+")).firstOrNull()?.toIntOrNull()
                    if (parsed != null) {
                        discoveredGpuPath = path
                        return parsed.coerceIn(0, 100)
                    }
                }
            }
        }
        return null
    }

    private fun readAllCores(): List<CoreInfo> {
        val raw = ArrayList<Triple<Int, Long, Long>>(coreCount)
        var maxClusterKhz = 0L
        var minClusterKhz = Long.MAX_VALUE
        for (i in 0 until coreCount) {
            val cur = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val max = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").coerceAtLeast(1L)
            if (cur > 0L) {
                raw.add(Triple(i, cur, max))
                if (max > maxClusterKhz) maxClusterKhz = max
                if (max < minClusterKhz) minClusterKhz = max
            }
        }
        // If direct sysfs read was blocked by SELinux, try a single batched Shizuku shell read
        if (raw.isEmpty() && ensureShell()) {
            val indices = (0 until coreCount).joinToString(" ")
            val cmd = "for i in $indices; do " +
                "c=\$(cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_cur_freq 2>/dev/null); " +
                "m=\$(cat /sys/devices/system/cpu/cpu\$i/cpufreq/cpuinfo_max_freq 2>/dev/null); " +
                "[ -n \"\$c\" ] && echo \"\$i \$c \${m:-1}\"; done"
            for (line in runShellCmd(cmd)) {
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 3) {
                    val idx = p[0].toIntOrNull() ?: continue
                    val cur = p[1].toLongOrNull() ?: continue
                    val max = (p[2].toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    if (cur > 0L) {
                        raw.add(Triple(idx, cur, max))
                        if (max > maxClusterKhz) maxClusterKhz = max
                        if (max < minClusterKhz) minClusterKhz = max
                    }
                }
            }
        }
        val hasHeterogeneous = maxClusterKhz > minClusterKhz
        return raw.map { (idx, cur, max) ->
            val pct = ((cur * 100L) / max).toInt().coerceIn(0, 100)
            val isBig = if (hasHeterogeneous) max >= (maxClusterKhz * 0.95).toLong() else false
            CoreInfo(
                coreIndex = idx,
                curMhz = (cur / 1000L).toInt(),
                maxMhz = (max / 1000L).toInt(),
                loadPercent = pct,
                isBigCore = isBig
            )
        }
    }

    private fun readRam() {
        val mi = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(mi)
        val total = mi.totalMem / (1024f * 1024f * 1024f)
        val avail = mi.availMem / (1024f * 1024f * 1024f)
        cachedRamTotal = total
        cachedRamUsed = (total - avail).coerceAtLeast(0f)
    }

    private fun readBatteryAndThermal() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (tempTenths > 0) cachedTempC = tempTenths / 10f
        if (level >= 0 && scale > 0) cachedBattPct = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        cachedIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)

        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = if (rawCurrent == Int.MIN_VALUE) {
            0
        } else {
            val absRaw = abs(rawCurrent.toLong())
            if (absRaw > 10_000L) (absRaw / 1000L).toInt() else absRaw.toInt()
        }
        cachedCurrentMa = ma
        val voltageV = when {
            rawVoltage > 100_000 -> rawVoltage / 1_000_000f
            rawVoltage > 0 -> rawVoltage / 1000f
            else -> 0f
        }
        cachedWatts = if (voltageV > 0f && ma > 0) voltageV * (ma / 1000f) else 0f
        if (cachedTempC > 0f) GameBooster.checkThermalGuard(cachedTempC)
    }

    private fun readNetwork(nowMs: Long) {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx >= 0L && tx >= 0L && prevRxBytes >= 0L && nowMs > prevNetMs) {
            val dt = (nowMs - prevNetMs) / 1000f
            cachedDownKbps = ((rx - prevRxBytes) / 1024f / dt).coerceAtLeast(0f)
            cachedUpKbps = ((tx - prevTxBytes) / 1024f / dt).coerceAtLeast(0f)
        }
        prevRxBytes = rx
        prevTxBytes = tx
        prevNetMs = nowMs
    }

    private fun readLongFile(path: String): Long {
        return try {
            File(path).readText().trim().toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun formatFloat(value: Float, decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}
