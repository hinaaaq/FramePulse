package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import android.view.Choreographer
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class CoreStat(
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
    val cpuLoadPercent: Int = 0,
    val cpuMaxGhz: Float = 0f,
    val cores: List<CoreStat> = emptyList(),
    val gpuLoadPercent: Int? = null,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val powerWatts: Float = 0f,
    val voltageVolts: Float = 0f,
    val currentMa: Int = 0,
    val netDownKbps: Float = 0f,
    val netUpKbps: Float = 0f
)

data class BenchmarkSummary(
    val durationSec: Int,
    val sampleCount: Int,
    val avgFps: Float,
    val onePercentLowFps: Int,
    val minFps: Int,
    val maxFps: Int,
    val peakTempC: Float,
    val avgWatts: Float
)

object TelemetryHub {
    @Volatile var latest: TelemetryData = TelemetryData()
    val fpsHistory = ArrayDeque<Int>(48)

    @Volatile var isRecording = false
        private set
    private var recordStartMs = 0L
    private val recordedFps = mutableListOf<Int>()
    private var peakTemp = 0f
    private var wattSum = 0f
    private var sampleCounter = 0

    @Volatile var lastSummary: BenchmarkSummary? = null

    fun pushSample(data: TelemetryData) {
        latest = data
        synchronized(fpsHistory) {
            if (fpsHistory.size >= 45) fpsHistory.removeFirst()
            fpsHistory.addLast(data.fps)
        }
        if (isRecording && data.fps > 0) {
            synchronized(recordedFps) {
                recordedFps.add(data.fps)
                if (data.batteryTempC > peakTemp) peakTemp = data.batteryTempC
                wattSum += data.powerWatts
                sampleCounter++
            }
        }
    }

    fun startRecording() {
        synchronized(recordedFps) {
            recordedFps.clear()
            peakTemp = 0f
            wattSum = 0f
            sampleCounter = 0
            recordStartMs = SystemClock.elapsedRealtime()
            isRecording = true
        }
    }

    fun stopRecording(): BenchmarkSummary? {
        synchronized(recordedFps) {
            isRecording = false
            if (recordedFps.isEmpty()) return null
            val sorted = recordedFps.sorted()
            val onePctIndex = (sorted.size * 0.01).toInt().coerceIn(0, sorted.lastIndex)
            val duration = ((SystemClock.elapsedRealtime() - recordStartMs) / 1000L).toInt().coerceAtLeast(1)
            val summary = BenchmarkSummary(
                durationSec = duration,
                sampleCount = sorted.size,
                avgFps = sorted.average().toFloat(),
                onePercentLowFps = sorted[onePctIndex],
                minFps = sorted.first(),
                maxFps = sorted.last(),
                peakTempC = peakTemp,
                avgWatts = if (sampleCounter > 0) wattSum / sampleCounter else 0f
            )
            lastSummary = summary
            return summary
        }
    }
}

class SystemMonitor(private val context: Context) {

    private var uiFrameCount = 0
    private var lastUiFpsTimestamp = System.nanoTime()
    private var latestUiFps = 0
    private var latestUiFrameTime = 0f
    private var trackingChoreographer = false

    private var prevRxBytes = TrafficStats.getTotalRxBytes()
    private var prevTxBytes = TrafficStats.getTotalTxBytes()
    private var prevNetTimeMs = SystemClock.elapsedRealtime()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!trackingChoreographer) return
            uiFrameCount++
            val elapsed = frameTimeNanos - lastUiFpsTimestamp
            if (elapsed >= 400_000_000L) {
                val fpsVal = ((uiFrameCount * 1_000_000_000.0) / elapsed).roundToInt()
                latestUiFps = fpsVal
                latestUiFrameTime = if (fpsVal > 0) 1000f / fpsVal else 0f
                uiFrameCount = 0
                lastUiFpsTimestamp = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (!trackingChoreographer) {
            trackingChoreographer = true
            lastUiFpsTimestamp = System.nanoTime()
            uiFrameCount = 0
            prevRxBytes = TrafficStats.getTotalRxBytes()
            prevTxBytes = TrafficStats.getTotalTxBytes()
            prevNetTimeMs = SystemClock.elapsedRealtime()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun stop() {
        trackingChoreographer = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun collect(): TelemetryData {
        val shizukuActive = isShizukuReady()

        val sfPair = if (shizukuActive) getSurfaceFlingerFps() else null
        val finalFps = sfPair?.first ?: latestUiFps
        val finalFrameTime = sfPair?.second ?: latestUiFrameTime

        val (coresList, freqLoad, peakGhz) = readCpuDetails()
        val shizukuCpu = if (shizukuActive) readShizukuCpuPercent() else null
        val finalCpuLoad = (shizukuCpu ?: freqLoad).coerceIn(0, 100)

        val gpuLoad = readGpuLoad()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        val usedGb = (memInfo.totalMem - memInfo.availMem) / (1024f * 1024f * 1024f)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (abs(rawCurrent) > 10_000) rawCurrent / 1000f else rawCurrent.toFloat()
        val volts = voltageMv / 1000f
        val watts = abs(volts * (currentMa / 1000f))

        val nowMs = SystemClock.elapsedRealtime()
        val dtSec = ((nowMs - prevNetTimeMs) / 1000f).coerceAtLeast(0.1f)
        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        val downKbps = if (curRx >= prevRxBytes && prevRxBytes > 0) ((curRx - prevRxBytes) / 1024f) / dtSec else 0f
        val upKbps = if (curTx >= prevTxBytes && prevTxBytes > 0) ((curTx - prevTxBytes) / 1024f) / dtSec else 0f
        prevRxBytes = curRx
        prevTxBytes = curTx
        prevNetTimeMs = nowMs

        val data = TelemetryData(
            fps = finalFps,
            frameTimeMs = finalFrameTime,
            isTrueGameFps = sfPair != null,
            cpuLoadPercent = finalCpuLoad,
            cpuMaxGhz = peakGhz,
            cores = coresList,
            gpuLoadPercent = gpuLoad,
            ramUsedGb = usedGb,
            ramTotalGb = totalGb,
            batteryTempC = tempC,
            powerWatts = watts,
            voltageVolts = volts,
            currentMa = currentMa.roundToInt(),
            netDownKbps = downKbps,
            netUpKbps = upKbps
        )
        TelemetryHub.pushSample(data)
        return data
    }

    private fun readCpuDetails(): Triple<List<CoreStat>, Int, Float> {
        var curSum = 0L
        var maxSum = 0L
        var highestKhz = 0L
        val cores = mutableListOf<CoreStat>()

        for (i in 0 until 8) {
            try {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (curFile.exists() && maxFile.exists()) {
                    val cur = curFile.readText().trim().toLongOrNull() ?: 0L
                    val max = maxFile.readText().trim().toLongOrNull() ?: 0L
                    if (max > 0) {
                        curSum += cur
                        maxSum += max
                        if (cur > highestKhz) highestKhz = cur
                        val pct = ((cur * 100.0) / max).roundToInt().coerceIn(0, 100)
                        cores.add(
                            CoreStat(
                                coreIndex = i,
                                curMhz = (cur / 1000L).toInt(),
                                maxMhz = (max / 1000L).toInt(),
                                loadPercent = pct,
                                isBigCore = max >= 2_100_000L
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        val totalPercent = if (maxSum > 0) ((curSum * 100.0) / maxSum).roundToInt() else 0
        val peakGhz = highestKhz / 1_000_000f
        return Triple(cores, totalPercent, peakGhz)
    }

    private fun readGpuLoad(): Int? {
        val paths = listOf(
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/class/misc/mali0/device/dvfs_utilization"
        )
        for (path in paths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.readText().trim().substringBefore(" ").toIntOrNull()
                    if (raw != null && raw in 0..100) return raw
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun readShizukuCpuPercent(): Int? {
        val out = execShizuku("top -n 1 -b | grep -m 1 '%cpu'") ?: return null
        return try {
            val parts = out.trim().split(Regex("\\s+"))
            val totalPart = parts.firstOrNull { it.endsWith("%cpu") }?.removeSuffix("%cpu")?.toFloatOrNull() ?: return null
            val idlePart = parts.firstOrNull { it.endsWith("%idle") }?.removeSuffix("%idle")?.toFloatOrNull() ?: return null
            if (totalPart <= 0f) return null
            (((totalPart - idlePart) / totalPart) * 100f).roundToInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun getSurfaceFlingerFps(): Pair<Int, Float>? {
        val focus = execShizuku("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'") ?: return null
        val pkgMatch = Regex("([a-zA-Z0-9_]+\\.[a-zA-Z0-9_.]+)/").find(focus) ?: return null
        val pkgName = pkgMatch.groupValues[1]
        if (pkgName == context.packageName || pkgName == "com.sec.android.app.launcher") return null

        val layers = execShizuku("dumpsys SurfaceFlinger --list") ?: return null
        val targetLayer = layers.lines().firstOrNull {
            it.contains(pkgName) && (it.contains("SurfaceView") || it.contains("BLAST"))
        } ?: return null

        val latency = execShizuku("dumpsys SurfaceFlinger --latency \"$targetLayer\"") ?: return null
        val timestamps = mutableListOf<Long>()
        for (line in latency.lines().drop(1)) {
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 2) {
                val presentTime = cols[1].toLongOrNull() ?: 0L
                if (presentTime > 0L && presentTime != Long.MAX_VALUE) {
                    timestamps.add(presentTime)
                }
            }
        }
        if (timestamps.size < 2) return null
        val latest = timestamps.last()
        val oneSecAgo = latest - 1_000_000_000L
        val recent = timestamps.filter { it > oneSecAgo }
        val fps = recent.size.coerceAtLeast(1)
        val avgFrameMs = if (recent.size >= 2) {
            ((recent.last() - recent.first()) / 1_000_000f) / (recent.size - 1)
        } else {
            1000f / fps
        }
        return fps to avgFrameMs
    }

    private fun execShizuku(cmd: String): String? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        } catch (_: Throwable) {
            null
        }
    }

    fun formatFloat(value: Float, decimals: Int = 1): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}
