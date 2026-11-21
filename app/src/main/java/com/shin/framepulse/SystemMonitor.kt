package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.view.Choreographer
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class TelemetryData(
    val fps: Int,
    val isTrueGameFps: Boolean,
    val cpuLoadPercent: Int,
    val cpuMaxGhz: Float,
    val gpuLoadPercent: Int?,
    val ramUsedGb: Float,
    val ramTotalGb: Float,
    val batteryTempC: Float,
    val powerWatts: Float
)

class SystemMonitor(private val context: Context) {

    private var uiFrameCount = 0
    private var lastUiFpsTimestamp = System.nanoTime()
    private var latestUiFps = 0
    private var trackingChoreographer = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!trackingChoreographer) return
            uiFrameCount++
            val elapsed = frameTimeNanos - lastUiFpsTimestamp
            if (elapsed >= 400_000_000L) {
                latestUiFps = ((uiFrameCount * 1_000_000_000.0) / elapsed).roundToInt()
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

        val sfFps = if (shizukuActive) getSurfaceFlingerFps() else null
        val finalFps = sfFps ?: latestUiFps

        val (freqLoad, peakGhz) = readCpuFreqStats()
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
        val watts = abs((voltageMv / 1000f) * (currentMa / 1000f))

        return TelemetryData(
            fps = finalFps,
            isTrueGameFps = sfFps != null,
            cpuLoadPercent = finalCpuLoad,
            cpuMaxGhz = peakGhz,
            gpuLoadPercent = gpuLoad,
            ramUsedGb = usedGb,
            ramTotalGb = totalGb,
            batteryTempC = tempC,
            powerWatts = watts
        )
    }

    private fun readCpuFreqStats(): Pair<Int, Float> {
        var curSum = 0L
        var maxSum = 0L
        var highestKhz = 0L

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
                    }
                }
            } catch (_: Exception) {
            }
        }
        val percent = if (maxSum > 0) ((curSum * 100.0) / maxSum).roundToInt() else 0
        val peakGhz = highestKhz / 1_000_000f
        return percent to peakGhz
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

    private fun getSurfaceFlingerFps(): Int? {
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
        val framesInLastSec = timestamps.count { it > oneSecAgo }
        return framesInLastSec.coerceAtLeast(1)
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
