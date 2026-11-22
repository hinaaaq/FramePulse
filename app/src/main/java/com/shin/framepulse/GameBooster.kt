package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.roundToInt

data class BoostResult(
    val ramFreedMb: Int,
    val gameModeApplied: Boolean,
    val fixedPerfApplied: Boolean,
    val targetPackage: String,
    val summaryMessage: String
)

object GameBooster {

    @Volatile var isFixedPerfActive: Boolean = false
        private set

    @Volatile var isThermalCooldown: Boolean = false
        private set

    @Volatile var lastBoostSummary: String = ""
        private set

    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun runOneShotShell(cmd: String): Pair<Int, String> {
        if (!isShizukuReady()) return -1 to "Shizuku belum aktif"
        var proc: Process? = null
        return try {
            proc = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    if (sb.length < 1024) {
                        sb.append(line).append('\n')
                    }
                }
            }
            val code = proc.waitFor()
            code to sb.toString().trim()
        } catch (t: Throwable) {
            -1 to (t.message ?: "error")
        } finally {
            try { proc?.destroy() } catch (_: Throwable) {}
        }
    }

    private fun getAvailableRamMb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (mi.availMem / (1024L * 1024L)).toInt()
    }

    private fun sanitizePackage(pkg: String): String {
        val clean = pkg.trim()
        return if (clean.matches(Regex("^[a-zA-Z0-9_.]+$"))) clean else ""
    }

    fun resolveTargetGamePackage(): String {
        val live = TelemetryHub.activeForegroundPkg?.trim().orEmpty()
        if (live.isNotEmpty() && !isSystemOrSelfPkg(live)) {
            Prefs.lastGamePackage = live
            return live
        }
        return Prefs.lastGamePackage
    }

    private fun isSystemOrSelfPkg(pkg: String): Boolean {
        return pkg == "com.shin.framepulse" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.startsWith("com.sec.android.app.launcher") ||
            pkg.startsWith("com.google.android.apps.nexuslauncher") ||
            pkg == "com.android.settings" ||
            pkg == "moe.shizuku.privileged.api"
    }

    fun runQuickBoost(
        context: Context,
        targetPkgRaw: String = resolveTargetGamePackage(),
        enableFixedPerf: Boolean = Prefs.turboFixedPerf
    ): BoostResult {
        val beforeMb = getAvailableRamMb(context)
        val pkg = sanitizePackage(targetPkgRaw)

        if (!isShizukuReady()) {
            val msg = "Aktifkan Shizuku dulu untuk Game Turbo"
            lastBoostSummary = msg
            return BoostResult(0, false, false, pkg, msg)
        }

        // 1. Safe background cache & empty-process trim (reduces mid-game lmkd stutters)
        runOneShotShell("am kill-all 2>/dev/null; pm trim-caches 512M 2>/dev/null")

        // 2. Android 12-16 GameManagerService Performance Mode
        var gameModeOk = false
        if (pkg.isNotEmpty()) {
            gameModeOk = applyGameModeForPackage(pkg)
        }

        // 3. Power HAL Fixed Performance Mode (guarded by thermal safety limit)
        var fixedPerfOk = false
        val curTemp = TelemetryHub.latest.batteryTempC
        if (enableFixedPerf) {
            if (curTemp >= Prefs.turboThermalLimitC) {
                isThermalCooldown = true
                setFixedPerformanceMode(false)
            } else {
                isThermalCooldown = false
                fixedPerfOk = setFixedPerformanceMode(true)
            }
        }

        val afterMb = getAvailableRamMb(context)
        val freed = (afterMb - beforeMb).coerceAtLeast(0)

        val parts = mutableListOf<String>()
        if (freed > 0) parts.add("RAM +${freed}MB") else parts.add("Cache OK")
        if (gameModeOk) parts.add("GameMode: Perf")
        if (fixedPerfOk) parts.add("PerfHAL: ON")
        else if (enableFixedPerf && isThermalCooldown) parts.add("Cooldown >=${Prefs.turboThermalLimitC.roundToInt()}°C")

        val summary = parts.joinToString(" • ")
        lastBoostSummary = summary
        return BoostResult(
            ramFreedMb = freed,
            gameModeApplied = gameModeOk,
            fixedPerfApplied = fixedPerfOk,
            targetPackage = pkg,
            summaryMessage = summary
        )
    }

    fun applyGameModeForPackage(pkgRaw: String): Boolean {
        val pkg = sanitizePackage(pkgRaw)
        if (pkg.isEmpty() || !isShizukuReady()) return false
        val (c1, out1) = runOneShotShell("cmd game mode performance '$pkg' 2>&1")
        if (c1 == 0 && !out1.contains("error", ignoreCase = true) && !out1.contains("unknown", ignoreCase = true)) {
            return true
        }
        val (c2, out2) = runOneShotShell("cmd game set --mode performance '$pkg' 2>&1")
        return c2 == 0 && !out2.contains("error", ignoreCase = true)
    }

    fun setFixedPerformanceMode(enable: Boolean): Boolean {
        if (!isShizukuReady()) {
            if (!enable) isFixedPerfActive = false
            return false
        }
        val flag = if (enable) "true" else "false"
        val (code, out) = runOneShotShell("cmd power set-fixed-performance-mode-enabled $flag 2>&1")
        val unsupported = out.contains("not supported", ignoreCase = true) ||
            out.contains("unknown", ignoreCase = true) ||
            out.contains("exception", ignoreCase = true)
        val ok = (code == 0 && !unsupported)
        if (!enable) {
            isFixedPerfActive = false
        } else if (ok) {
            isFixedPerfActive = true
        }
        return ok
    }

    fun checkThermalGuard(tempC: Float) {
        if (!isFixedPerfActive) return
        val limit = Prefs.turboThermalLimitC
        if (tempC >= limit) {
            setFixedPerformanceMode(false)
            isThermalCooldown = true
            lastBoostSummary = "Thermal Guard: PerfHAL OFF (${String.format(Locale.US, "%.1f", tempC)}°C)"
        }
    }

    fun optimizeGameArtProfile(pkgRaw: String): String {
        val pkg = sanitizePackage(pkgRaw)
        if (!isShizukuReady()) return "Shizuku belum aktif."
        if (pkg.isEmpty()) return "Belum ada game terdeteksi. Buka game sebentar dengan HUD aktif dulu."
        val (code, out) = runOneShotShell("cmd package compile -m speed-profile -f '$pkg' 2>&1")
        return if (code == 0 && !out.contains("Failure", ignoreCase = true)) {
            "ART speed-profile OK untuk ${pkg.substringAfterLast('.')}"
        } else {
            "ART gagal: ${out.ifBlank { "tidak didukung" }.take(60)}"
        }
    }

    fun shutdownSafely() {
        if (isFixedPerfActive) {
            setFixedPerformanceMode(false)
        }
        isThermalCooldown = false
    }
}
