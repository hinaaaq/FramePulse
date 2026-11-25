package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
    val onePercentLowFps: Int = 0,
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
    val thermalStatus: Int = 0,
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

    private val _fpsHistory = ArrayDeque<Int>(64)
    val fpsHistory: List<Int>
        get() = getFpsHistorySnapshot()

    fun getFpsHistorySnapshot(): List<Int> = synchronized(_fpsHistory) { _fpsHistory.toList() }

    @Volatile var lastSessionSamples: List<Int> = emptyList()
        private set

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

    private val gamePkgCache = ConcurrentHashMap<String, Boolean>()
    private val nonGameUtilityPkgs = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.google.android.youtube",
        "com.android.chrome",
        "com.google.android.gm",
        "com.shopee.id",
        "com.tokopedia.tkpd"
    )

    fun isLikelyGamePackage(context: Context, pkg: String): Boolean {
        val clean = ShizukuShell.sanitizePackage(pkg)
        if (clean.isEmpty() || GameBooster.isSystemOrSelfPkg(clean) || clean in nonGameUtilityPkgs) {
            return false
        }
        if (clean in GameProfileEngine.allKnownGamePackages || clean == Prefs.lastGamePackage) {
            return true
        }
        return gamePkgCache.getOrPut(clean) {
            runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(clean, 0)
                val byCat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info.category == ApplicationInfo.CATEGORY_GAME
                } else false
                @Suppress("DEPRECATION")
                val byFlag = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                byCat || byFlag
            }.getOrDefault(false)
        }
    }

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
                if (ft.isFinite() && ft in 1.5f..2500f) {
                    recordedFrameTimesMs.add(ft)
                }
            }
            if (recordedFrameTimesMs.size > 18000) {
                recordedFrameTimesMs.subList(0, recordedFrameTimesMs.size - 18000).clear()
            }
        }
    }

    fun stopRecording(): BenchmarkSummary? {
        synchronized(recordedFps) {
            isRecording = false
            val duration = ((SystemClock.elapsedRealtime() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
            recordingStartMs = 0L
            if (recordedFps.isEmpty() && recordedFrameTimesMs.isEmpty()) return null

            lastSessionSamples = if (recordedFps.isNotEmpty()) {
                recordedFps.toList()
            } else {
                recordedFrameTimesMs.map { (1000f / it.coerceAtLeast(1f)).roundToInt().coerceIn(1, 240) }
            }

            val avg: Float
            val onePctLow: Int
            val minFps: Int
            val maxFps: Int

            if (recordedFrameTimesMs.size >= 15) {
                val sortedFt = recordedFrameTimesMs.sorted()
                val avgFt = sortedFt.average().toFloat().coerceAtLeast(1f)
                avg = (1000f / avgFt).coerceIn(1f, 240f)
                val worstCount = maxOf(1, (sortedFt.size * 0.01).toInt())
                val worstAvgFt = sortedFt.takeLast(worstCount).average().toFloat().coerceAtLeast(1f)
                onePctLow = (1000f / worstAvgFt).roundToInt().coerceIn(1, 240)
                minFps = (1000f / sortedFt.last().coerceAtLeast(1f)).roundToInt().coerceIn(1, 240)
                maxFps = (1000f / sortedFt.first().coerceAtLeast(1f)).roundToInt().coerceIn(1, 240)
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
                peakTempC = peakRecordedTemp.coerceAtLeast(latest.batteryTempC),
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
        lastSessionSamples = emptyList()
        try { Prefs.benchmarkHistoryRaw = "" } catch (_: Throwable) {}
    }

    private fun saveHistoryItem(item: BenchmarkSummary) {
        try {
            val current = loadHistory().toMutableList()
            current.add(0, item)
            val trimmed = current.take(12)
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
        synchronized(_fpsHistory) {
            _fpsHistory.addLast(data.fps)
            while (_fpsHistory.size > 60) {
                _fpsHistory.removeFirst()
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
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shellLock = Any()
    private val cmdSeq = AtomicLong(0L)

    companion object {
        private val shellWatchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "FramePulse-MonitorWatchdog").apply { isDaemon = true }
        }
    }

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
    private var sfOnePctLow = 0
    private var sfFrameMs = 0f
    private val rollingFrameTimesMs = ArrayDeque<Float>(240)
    private val pkgRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+)/""")
    private val firstIntRegex = Regex("""(\d+)""")

    private var lastCpuQueryMs = 0L
    private var cachedCpuLoad = 0
    private var cachedCpuSource = "FREQ"
    private var cachedCpuMaxGhz = 0f
    private var cachedGpuLoad: Int? = null
    private var discoveredGpuPath: String? = null
    private var lastGpuScanMs = 0L
    private var lastAutoBoostedPkg: String? = null
    private var cachedCores: List<CoreInfo> = emptyList()
    private var prevTotalCpu = 0L
    private var prevIdleCpu = 0L

    private var lastSlowQueryMs = 0L
    private var cachedRamUsed = 0f
    private var cachedRamTotal = 0f
    private var cachedTempC = 0f
    private var cachedThermalStatus = 0
    private var cachedBattPct = 0
    private var cachedIsCharging = false
    private var cachedWatts = 0f
    private var cachedCurrentMa = 0
    private var currentInMicroAmps: Boolean = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    private var cachedDownKbps = 0f
    private var cachedUpKbps = 0f
    private var prevRxBytes = -1L
    private var prevTxBytes = -1L
    private var prevNetMs = 0L

    private val coreCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)

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
        return rate.roundToInt().coerceIn(60, 240)
    }

    fun isShizukuBinderAlive(): Boolean = ShizukuShell.isBinderAlive()

    fun isShizukuReady(): Boolean = ShizukuShell.isReady()

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
            val proc = ShizukuShell.openPersistentShell() ?: return false
            runCatching { proc.errorStream.close() }
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
        val proc = shellProcess ?: return emptyList()
        val writer = shellWriter ?: return emptyList()
        val reader = shellReader ?: return emptyList()
        val sentinel = "__FP_END_${cmdSeq.incrementAndGet()}_${SystemClock.elapsedRealtimeNanos()}__"
        val timer = shellWatchdog.schedule({
            runCatching { proc.destroy() }
        }, 3_500L, TimeUnit.MILLISECONDS)

        return try {
            writer.write("{ $cmd ; } 2>/dev/null\nprintf '\\n%s\\n' '$sentinel'\n")
            writer.flush()
            val out = ArrayList<String>(132)
            while (true) {
                val line = reader.readLine() ?: break
                if (line == sentinel) break
                if (out.size < 2048) {
                    out.add(line)
                }
            }
            out
        } catch (_: Throwable) {
            closeShellInternal()
            emptyList()
        } finally {
            timer.cancel(false)
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

    /**
     * Returns the actual focused foreground app package (including Home launchers and FramePulse itself),
     * skipping only floating system overlays, keyboards, and sidebars.
     */
    private fun detectForegroundPackage(): String? {
        val overlayOnlyPkgs = setOf(
            "com.android.systemui",
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
                val pkg = ShizukuShell.sanitizePackage(match.groupValues[1])
                if (pkg.isNotEmpty() && pkg !in overlayOnlyPkgs && !pkg.startsWith("com.android.systemui")) {
                    return pkg
                }
            }
            return null
        }
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

    private fun normalizeLayerName(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("RequestedLayerState{")) return t
        val inner = t.removePrefix("RequestedLayerState{").removeSuffix("}")
        val cut = listOf(" parentId=", " z=", " layerStack=", " flags=")
            .map { inner.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
        return (if (cut != null) inner.substring(0, cut) else inner).trim()
    }

    private fun isExcludedLayer(line: String): Boolean {
        val excluded = listOf(
            "Background for", "Bounds for", "RelativeParent",
            "FramePulse", "com.shin.framepulse", "NavigationBar", "StatusBar",
            "ScreenDecor", "Wallpaper", "NotificationShade", "InputMethod",
            "Taskbar", "Dim Layer", "Letterbox", "Transition", "Splash Screen",
            "cocktailbarservice", "gametools", "systemui", "launcher",
            "edgepanel", "smartcapture", "Screenshot", "Toast", "PopupWindow",
            "AssistOverlay", "PipMenu", "PipContent", "MagnificationOverlay"
        )
        return excluded.any { bad -> line.contains(bad, ignoreCase = true) }
    }

    private fun scanBestActiveLayer(): Pair<String, List<Long>>? {
        val fgPkg = detectForegroundPackage()
        activeForegroundPkg = fgPkg
        TelemetryHub.activeForegroundPkg = fgPkg

        // Do not lock onto SurfaceFlinger layers when user is inside FramePulse or Home Launcher
        val isFgTargetApp = fgPkg != null && !GameBooster.isSystemOrSelfPkg(fgPkg)
        if (fgPkg != null && GameBooster.isSystemOrSelfPkg(fgPkg)) {
            return null
        }

        val allLayers = runShellCmd("dumpsys SurfaceFlinger --list")
            .map { normalizeLayerName(it) }
            .filter { it.isNotEmpty() && !isExcludedLayer(it) }
        if (allLayers.isEmpty()) return null

        val candidates = LinkedHashSet<String>()
        if (isFgTargetApp && fgPkg != null) {
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
            val isFg = isFgTargetApp && fgPkg != null && cand.contains(fgPkg)
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
                if (isFg && isFresh && cand.contains("SurfaceView") && ts.size >= 5) break
            }
        }
        return if (bestLayer != null) bestLayer to bestTimestamps else null
    }

    fun dumpSurfaceFlingerDiagnostics(): String {
        val isId = AppStrings.resolveLangCode() == "id"
        if (!isShizukuReady()) {
            return if (isId) "Shizuku belum aktif / izin belum diberikan." else "Shizuku is not active or permission not granted."
        }
        if (!ensureShell()) {
            return if (isId) "Gagal membuka shell Shizuku." else "Failed to open Shizuku shell."
        }
        return buildString {
            val fg = detectForegroundPackage() ?: TelemetryHub.activeForegroundPkg
            val locked = activeGameLayer ?: TelemetryHub.activeLockedLayer
            appendLine("Foreground Pkg: ${fg ?: "(none)"}")
            appendLine("Locked Layer: ${locked ?: "(none)"}")
            appendLine("Display Hz: ${detectDisplayRefreshHz()}Hz")
            appendLine("Latest HUD FPS: ${TelemetryHub.latest.fps} | 1% Low: ${TelemetryHub.latest.onePercentLowFps} (isTrue=${TelemetryHub.latest.isTrueGameFps})")
            appendLine("CPU Source: ${TelemetryHub.latest.cpuSource} | GPU: ${TelemetryHub.latest.gpuLoadPercent ?: "N/A"} | ThermalState: ${TelemetryHub.latest.thermalStatus}")
            appendLine("--- Focus ---")
            runShellCmd("dumpsys window displays | grep -E 'mCurrentFocus|mFocusedApp'").take(4).forEach { appendLine(it.trim()) }
            appendLine("--- SurfaceFlinger Layers (filtered) ---")
            val layers = runShellCmd("dumpsys SurfaceFlinger --list")
                .map { normalizeLayerName(it) }
                .filter { it.contains("SurfaceView") || it.contains("BLAST") || (fg != null && it.contains(fg)) }
            layers.takeLast(15).forEach { appendLine(it) }
            locked?.let { layer ->
                appendLine("--- Latency Sample ---")
                val safe = layer.replace("'", "'\\''")
                runShellCmd("dumpsys SurfaceFlinger --latency '$safe'").take(8).forEach { appendLine(it) }
            }
        }
    }

    private fun computeRollingOnePercentLow(fallbackFps: Int, maxHz: Int): Int {
        if (rollingFrameTimesMs.size < 8) return fallbackFps
        val sorted = rollingFrameTimesMs.sorted()
        val worstCount = maxOf(1, (sorted.size * 0.01).toInt())
        val worstAvgMs = sorted.takeLast(worstCount).average().toFloat().coerceAtLeast(1f)
        return (1000f / worstAvgMs).roundToInt().coerceIn(1, maxHz)
    }

    private fun querySurfaceFlingerFps(nowMs: Long, maxHz: Int): Boolean {
        if (!ensureShell()) {
            TelemetryHub.activeForegroundPkg = null
            return false
        }

        var validTimestamps: List<Long>? = null
        val lockedLayer = activeGameLayer
        val layerSilent = lastSfQueryMs > 0L && (nowMs - lastSfQueryMs >= 1500L)
        var needFullScan = lockedLayer == null || layerSilent

        val focusIntervalMs = if (Prefs.autoShowHudInGames) 1500L else 8000L
        if (!needFullScan && lockedLayer != null && (nowMs - lastFocusCheckMs >= focusIntervalMs)) {
            lastFocusCheckMs = nowMs
            val currentFg = detectForegroundPackage()
            activeForegroundPkg = currentFg
            TelemetryHub.activeForegroundPkg = currentFg
            if (currentFg == null || GameBooster.isSystemOrSelfPkg(currentFg) || !lockedLayer.contains(currentFg)) {
                needFullScan = true
            }
        }

        if (needFullScan && (lockedLayer == null || nowMs - lastLayerScanMs >= 1200L)) {
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
                    rollingFrameTimesMs.clear()
                    val detectedPkg = ShizukuShell.sanitizePackage(activeForegroundPkg)
                    if (detectedPkg.isNotEmpty() &&
                        !GameBooster.isSystemOrSelfPkg(detectedPkg) &&
                        (foundLayer.contains("SurfaceView") || TelemetryHub.isLikelyGamePackage(context, detectedPkg))
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
                rollingFrameTimesMs.clear()
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
                    sfOnePctLow = est
                    sfFrameMs = 1000f / est
                }
            }
            return true
        }

        if (newestNs <= lastPresentNs) {
            if (lastSfQueryMs > 0L && nowMs - lastSfQueryMs > 900L) {
                sfFps = 0
                sfOnePctLow = 0
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
                if (dtMs in 1.5f..2500f) {
                    frameTimes.add(dtMs)
                    rollingFrameTimesMs.addLast(dtMs)
                    while (rollingFrameTimesMs.size > 240) {
                        rollingFrameTimesMs.removeFirst()
                    }
                }
                prev = ts
            }
            lastPresentNs = newestNs
            lastSfQueryMs = nowMs

            if (frameTimes.isNotEmpty()) {
                TelemetryHub.recordFrameTimes(frameTimes)
            }

            val window = validTimestamps.filter { it >= newestNs - 1_000_000_000L }
            val measured: Int? = if (window.size >= 2) {
                val spanNs = window.last() - window.first()
                if (spanNs > 40_000_000L) {
                    (((window.size - 1) * 1_000_000_000.0) / spanNs).roundToInt().coerceIn(1, maxHz)
                } else null
            } else if (frameTimes.isNotEmpty()) {
                val avgDt = frameTimes.average().toFloat().coerceAtLeast(1f)
                (1000f / avgDt).roundToInt().coerceIn(1, maxHz)
            } else null

            if (measured != null) {
                sfFps = measured
                sfFrameMs = if (frameTimes.isNotEmpty()) frameTimes.average().toFloat() else (1000f / measured)
                sfOnePctLow = computeRollingOnePercentLow(measured, maxHz)
            }
        }
        return true
    }

    fun collect(): TelemetryData {
        val nowMs = SystemClock.elapsedRealtime()
        val fg = TelemetryHub.isAppForeground
        val maxHz = detectDisplayRefreshHz()

        val trueFpsOk = querySurfaceFlingerFps(nowMs, maxHz)
        setChoreographerEnabled(!trueFpsOk && (fg || Prefs.showFps))
        val fps = if (trueFpsOk) sfFps else choreoFps
        val frameMs = if (trueFpsOk) sfFrameMs else choreoFrameMs
        val onePctLow = if (trueFpsOk) sfOnePctLow else fps

        if (nowMs - lastCpuQueryMs >= 1000L || lastCpuQueryMs == 0L) {
            lastCpuQueryMs = nowMs
            if (fg || Prefs.showCpu || Prefs.showClock) {
                cachedCores = readAllCores()
                cachedCpuMaxGhz = (cachedCores.maxOfOrNull { it.curMhz } ?: 0) / 1000f
            }
            if (fg || Prefs.showCpu) cachedCpuLoad = readCpuLoad()
            if (fg || Prefs.showGpu) cachedGpuLoad = readGpuLoad(nowMs)
        }

        if (nowMs - lastSlowQueryMs >= 2000L || lastSlowQueryMs == 0L) {
            lastSlowQueryMs = nowMs
            if (fg || Prefs.showRam) readRam()
            if (fg || Prefs.showTemp || Prefs.showWatt || Prefs.showBatteryPct || Prefs.thermalWarningEnabled) {
                readBatteryAndThermal()
            }
            if (fg || Prefs.showNet) readNetwork(nowMs)
        }

        val data = TelemetryData(
            fps = fps,
            onePercentLowFps = onePctLow,
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
            thermalStatus = cachedThermalStatus,
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
            val first = runShellCmd("head -n 1 /proc/stat").firstOrNull()
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

    private fun extractFirstPercent(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val m = firstIntRegex.find(raw) ?: return null
        return m.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
    }

    private fun readGpuLoad(nowMs: Long): Int? {
        val candidatePaths = listOf(
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/kernel/ged/hal/dvfs_loading",
            "/sys/devices/platform/13000000.mali/utilization",
            "/sys/class/misc/mali0/device/utilization",
            "/proc/mali/utilization",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/kernel/gpu/gpu_busy"
        )

        val pathsToTry = discoveredGpuPath?.let { listOf(it) } ?: candidatePaths
        for (path in pathsToTry) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val parsed = extractFirstPercent(f.readText())
                    if (parsed != null) {
                        discoveredGpuPath = path
                        return parsed
                    }
                }
            } catch (_: Throwable) {}
        }

        if (ensureShell()) {
            val known = discoveredGpuPath
            if (known != null) {
                val safe = known.replace("'", "'\\''")
                val out = runShellCmd("cat '$safe'").joinToString(" ")
                extractFirstPercent(out)?.let { return it }
                discoveredGpuPath = null
            }
            if (lastGpuScanMs == 0L || nowMs - lastGpuScanMs >= 30_000L) {
                lastGpuScanMs = nowMs
                val cmd = "for p in " + candidatePaths.joinToString(" ") +
                    "; do if [ -r \"\$p\" ]; then v=\$(head -n 1 \"\$p\" 2>/dev/null); [ -n \"\$v\" ] && echo \"\$p|\$v\"; fi; done"
                for (line in runShellCmd(cmd)) {
                    val sep = line.indexOf('|')
                    if (sep <= 0) continue
                    val path = line.substring(0, sep).trim()
                    val parsed = extractFirstPercent(line.substring(sep + 1))
                    if (parsed != null) {
                        discoveredGpuPath = path
                        return parsed
                    }
                }
            }
        }
        return null
    }

    private data class RawCoreFreq(val idx: Int, val cur: Long, val min: Long, val max: Long)

    private fun readAllCores(): List<CoreInfo> {
        val raw = ArrayList<RawCoreFreq>(coreCount)
        var maxClusterKhz = 0L
        var minClusterKhz = Long.MAX_VALUE
        for (i in 0 until coreCount) {
            val cur = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val min = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            val max = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").coerceAtLeast(1L)
            if (cur > 0L) {
                raw.add(RawCoreFreq(i, cur, min, max))
                if (max > maxClusterKhz) maxClusterKhz = max
                if (max < minClusterKhz) minClusterKhz = max
            }
        }
        if (raw.isEmpty() && ensureShell()) {
            val indices = (0 until coreCount).joinToString(" ")
            val cmd = "for i in $indices; do " +
                "c=\$(cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_cur_freq 2>/dev/null); " +
                "n=\$(cat /sys/devices/system/cpu/cpu\$i/cpufreq/cpuinfo_min_freq 2>/dev/null); " +
                "m=\$(cat /sys/devices/system/cpu/cpu\$i/cpufreq/cpuinfo_max_freq 2>/dev/null); " +
                "[ -n \"\$c\" ] && echo \"\$i \$c \${n:-0} \${m:-1}\"; done"
            for (line in runShellCmd(cmd)) {
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4) {
                    val idx = p[0].toIntOrNull() ?: continue
                    val cur = p[1].toLongOrNull() ?: continue
                    val min = p[2].toLongOrNull() ?: 0L
                    val max = (p[3].toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    if (cur > 0L) {
                        raw.add(RawCoreFreq(idx, cur, min, max))
                        if (max > maxClusterKhz) maxClusterKhz = max
                        if (max < minClusterKhz) minClusterKhz = max
                    }
                }
            }
        }
        val hasHeterogeneous = maxClusterKhz > minClusterKhz
        return raw.map { item ->
            val span = item.max - item.min
            val pct = if (span > 100_000L && item.min > 0L) {
                (((item.cur - item.min).coerceAtLeast(0L) * 100L) / span).toInt().coerceIn(0, 100)
            } else {
                ((item.cur * 100L) / item.max).toInt().coerceIn(0, 100)
            }
            val isBig = if (hasHeterogeneous) item.max >= (maxClusterKhz * 0.95).toLong() else false
            CoreInfo(
                coreIndex = item.idx,
                curMhz = (item.cur / 1000L).toInt(),
                maxMhz = (item.max / 1000L).toInt(),
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cachedThermalStatus = runCatching { powerManager?.currentThermalStatus ?: 0 }.getOrDefault(0)
        }

        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = if (rawCurrent == Int.MIN_VALUE) {
            0
        } else {
            val absRaw = abs(rawCurrent.toLong())
            if (absRaw > 20_000L) currentInMicroAmps = true
            if (currentInMicroAmps) (absRaw / 1000L).toInt() else absRaw.toInt()
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
        val safe = if (value.isFinite()) value else 0f
        return String.format(Locale.US, "%.${decimals}f", safe)
    }
}
