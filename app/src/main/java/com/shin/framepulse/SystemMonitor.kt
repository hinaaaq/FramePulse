package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.math.abs

data class CoreInfo(
    val coreIndex: Int,
    val curMhz: Int,
    val loadPercent: Int,
    val isBigCore: Boolean
)

data class TelemetryData(
    val fps: Int = 0,
    val frameTimeMs: Float = 0f,
    val isTrueGameFps: Boolean = false,
    val cpuLoadPercent: Int = 0,
    val cpuMaxGhz: Float = 0f,
    val gpuLoadPercent: Int? = null,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val powerWatts: Float = 0f,
    val currentMa: Int = 0,
    val netDownKbps: Float = 0f,
    val netUpKbps: Float = 0f,
    val cores: List<CoreInfo> = emptyList()
)

data class BenchmarkSummary(
    val avgFps: Float,
    val onePercentLowFps: Int,
    val minFps: Int,
    val maxFps: Int,
    val peakTempC: Float
)

object TelemetryHub {
    @Volatile var latest: TelemetryData = TelemetryData()
    val fpsHistory: MutableList<Int> = mutableListOf()

    @Volatile var isRecording: Boolean = false
    @Volatile var lastSummary: BenchmarkSummary? = null
    @Volatile var isAppForeground: Boolean = true

    private val recordedFps = mutableListOf<Int>()
    private var peakRecordedTemp = 0f

    fun startRecording() {
        synchronized(recordedFps) {
            recordedFps.clear()
            peakRecordedTemp = 0f
            isRecording = true
        }
    }

    fun stopRecording(): BenchmarkSummary? {
        synchronized(recordedFps) {
            isRecording = false
            if (recordedFps.isEmpty()) return null
            val sorted = recordedFps.sorted()
            val avg = sorted.average().toFloat()
            val min = sorted.first()
            val max = sorted.last()
            val onePctCount = maxOf(1, (sorted.size * 0.01).toInt())
            val onePctLow = sorted.take(onePctCount).average().toInt()
            val summary = BenchmarkSummary(
                avgFps = avg,
                onePercentLowFps = onePctLow,
                minFps = min,
                maxFps = max,
                peakTempC = peakRecordedTemp
            )
            lastSummary = summary
            return summary
        }
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
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var choreographerActive = false
    private var frameCount = 0
    private var lastChoreoNs = 0L
    private var choreoFps = 0
    private var choreoFrameMs = 0f

    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null
    private var shellReader: BufferedReader? = null
    private var activeGameLayer: String? = null
    private var lastLayerScanMs = 0L
    private var lastPresentNs = 0L
    private var lastSfQueryMs = 0L
    private var sfFps = 0
    private var sfFrameMs = 0f

    private var lastCpuQueryMs = 0L
    private var cachedCpuLoad = 0
    private var cachedCpuMaxGhz = 0f
    private var cachedGpuLoad: Int? = null
    private var cachedCores: List<CoreInfo> = emptyList()
    private var prevTotalCpu = 0L
    private var prevIdleCpu = 0L

    private var lastSlowQueryMs = 0L
    private var cachedRamUsed = 0f
    private var cachedRamTotal = 0f
    private var cachedTempC = 0f
    private var cachedWatts = 0f
    private var cachedCurrentMa = 0
    private var cachedDownKbps = 0f
    private var cachedUpKbps = 0f
    private var prevRxBytes = -1L
    private var prevTxBytes = -1L
    private var prevNetMs = 0L

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
                choreoFps = (frameCount / secs).toInt().coerceIn(0, 144)
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

    fun stop() {
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

    fun isShizukuReady(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuProvider.requestBinderForNonProviderProcess(context)
            }
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun ensureShell(): Boolean {
        if (!isShizukuReady()) {
            closeShell()
            return false
        }
        val proc = shellProcess
        if (proc != null) {
            return try {
                proc.exitValue()
                closeShell()
                openShell()
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
        return openShell()
    }

    private fun openShell(): Boolean {
        return try {
            val proc = newProcessMethod.invoke(null, arrayOf("sh"), null, null) as Process
            shellProcess = proc
            shellWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
            shellReader = BufferedReader(InputStreamReader(proc.inputStream))
            true
        } catch (_: Throwable) {
            closeShell()
            false
        }
    }

    private fun closeShell() {
        try { shellWriter?.close() } catch (_: Throwable) {}
        try { shellReader?.close() } catch (_: Throwable) {}
        try { shellProcess?.destroy() } catch (_: Throwable) {}
        shellWriter = null
        shellReader = null
        shellProcess = null
        activeGameLayer = null
    }

    private fun runShellCmd(cmd: String): List<String> {
        val writer = shellWriter ?: return emptyList()
        val reader = shellReader ?: return emptyList()
        return try {
            writer.write("$cmd\necho __FP_END__\n")
            writer.flush()
            val out = ArrayList<String>(132)
            while (true) {
                val line = reader.readLine() ?: break
                if (line == "__FP_END__") break
                out.add(line)
            }
            out
        } catch (_: Throwable) {
            closeShell()
            emptyList()
        }
    }
    private fun querySurfaceFlingerFps(nowMs: Long): Boolean {
        if (!ensureShell()) return false

        if (activeGameLayer == null || nowMs - lastLayerScanMs >= 3000L) {
            lastLayerScanMs = nowMs
            val layers = runShellCmd("dumpsys SurfaceFlinger --list")
            val found = layers.lastOrNull { line ->
                (line.contains("SurfaceView") || line.contains("BLAST")) &&
                    !line.contains("Background for") &&
                    !line.contains("FramePulse")
            }?.trim()
            if (found != activeGameLayer) {
                activeGameLayer = found
                lastPresentNs = 0L
                lastSfQueryMs = 0L
            }
        }

        val layer = activeGameLayer ?: return false
        val lines = runShellCmd("dumpsys SurfaceFlinger --latency \"$layer\"")
        if (lines.size < 3) {
            activeGameLayer = null
            return false
        }

        val validTimestamps = ArrayList<Long>(128)
        for (i in 1 until lines.size) {
            val parts = lines[i].split('\t')
            if (parts.size >= 2) {
                val presentNs = parts[1].trim().toLongOrNull() ?: 0L
                if (presentNs > 0L && presentNs < Long.MAX_VALUE) {
                    validTimestamps.add(presentNs)
                }
            }
        }

        if (validTimestamps.isEmpty()) return false
        validTimestamps.sort()
        val newestNs = validTimestamps.last()

        if (lastPresentNs == 0L || newestNs <= lastPresentNs) {
            if (lastSfQueryMs > 0L && nowMs - lastSfQueryMs > 1200L) {
                sfFps = 0
                sfFrameMs = 0f
            }
            if (lastPresentNs == 0L) {
                lastPresentNs = newestNs
                lastSfQueryMs = nowMs
            }
            return true
        }

        val newFrames = validTimestamps.count { it > lastPresentNs }
        val deltaNs = newestNs - lastPresentNs
        if (deltaNs > 0L && newFrames > 0) {
            val measured = ((newFrames * 1_000_000_000.0) / deltaNs).toInt().coerceIn(1, 144)
            sfFps = measured
            sfFrameMs = 1000f / measured
            lastPresentNs = newestNs
            lastSfQueryMs = nowMs
        }
        return true
    }

    fun collect(): TelemetryData {
        val nowMs = SystemClock.elapsedRealtime()
        val fg = TelemetryHub.isAppForeground

        val trueFpsOk = querySurfaceFlingerFps(nowMs)
        setChoreographerEnabled(!trueFpsOk)
        val fps = if (trueFpsOk) sfFps else choreoFps
        val frameMs = if (trueFpsOk) sfFrameMs else choreoFrameMs

        if (nowMs - lastCpuQueryMs >= 1000L || lastCpuQueryMs == 0L) {
            lastCpuQueryMs = nowMs
            if (fg || Prefs.showCpu) cachedCpuLoad = readCpuLoad()
            if (fg || Prefs.showClock) cachedCpuMaxGhz = readCpuMaxGhz()
            if (fg || Prefs.showGpu) cachedGpuLoad = readGpuLoad()
            if (fg) cachedCores = readAllCores()
        }

        if (nowMs - lastSlowQueryMs >= 2000L || lastSlowQueryMs == 0L) {
            lastSlowQueryMs = nowMs
            if (fg || Prefs.showRam) readRam()
            if (fg || Prefs.showTemp || Prefs.showWatt) readBatteryAndThermal()
            if (fg || Prefs.showNet) readNetwork(nowMs)
        }

        val data = TelemetryData(
            fps = fps,
            frameTimeMs = frameMs,
            isTrueGameFps = trueFpsOk,
            cpuLoadPercent = cachedCpuLoad,
            cpuMaxGhz = cachedCpuMaxGhz,
            gpuLoadPercent = cachedGpuLoad,
            ramUsedGb = cachedRamUsed,
            ramTotalGb = cachedRamTotal,
            batteryTempC = cachedTempC,
            powerWatts = cachedWatts,
            currentMa = cachedCurrentMa,
            netDownKbps = cachedDownKbps,
            netUpKbps = cachedUpKbps,
            cores = if (fg) cachedCores else emptyList()
        )
        TelemetryHub.publish(data)
        return data
    }

    private fun readCpuLoad(): Int {
        return try {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return cachedCpuLoad
            val toks = line.trim().split(Regex("\\s+"))
            if (toks.size < 5) return cachedCpuLoad
            val user = toks[1].toLong()
            val nice = toks[2].toLong()
            val sys = toks[3].toLong()
            val idle = toks[4].toLong()
            val iowait = toks.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = toks.getOrNull(6)?.toLongOrNull() ?: 0L
            val softirq = toks.getOrNull(7)?.toLongOrNull() ?: 0L
            val total = user + nice + sys + idle + iowait + irq + softirq
            val idleAll = idle + iowait
            val diffTotal = total - prevTotalCpu
            val diffIdle = idleAll - prevIdleCpu
            prevTotalCpu = total
            prevIdleCpu = idleAll
            if (diffTotal > 0L) (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(0, 100) else cachedCpuLoad
        } catch (_: Throwable) {
            cachedCpuLoad
        }
    }

    private fun readCpuMaxGhz(): Float {
        var maxKhz = 0L
        for (i in 0 until 8) {
            val khz = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (khz > maxKhz) maxKhz = khz
        }
        return if (maxKhz > 0L) maxKhz / 1_000_000f else cachedCpuMaxGhz
    }

    private fun readGpuLoad(): Int? {
        val paths = listOf(
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
        )
        for (path in paths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim().split(Regex("[\\s%]+")).firstOrNull()?.toIntOrNull()
                    if (raw != null) return raw.coerceIn(0, 100)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun readAllCores(): List<CoreInfo> {
        val list = ArrayList<CoreInfo>(8)
        for (i in 0 until 8) {
            val cur = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val max = readLongFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").coerceAtLeast(1L)
            if (cur > 0L) {
                val pct = ((cur * 100L) / max).toInt().coerceIn(0, 100)
                list.add(CoreInfo(i, (cur / 1000L).toInt(), pct, i >= 6))
            }
        }
        return list
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
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (tempTenths > 0) cachedTempC = tempTenths / 10f

        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = if (abs(rawCurrent) > 10_000) abs(rawCurrent) / 1000 else abs(rawCurrent)
        cachedCurrentMa = ma
        if (voltageMv > 0 && ma > 0) {
            cachedWatts = (voltageMv / 1000f) * (ma / 1000f)
        }
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
