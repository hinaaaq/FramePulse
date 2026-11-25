package com.shin.framepulse

import android.app.ActivityManager
import android.content.Context
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

    private fun isId(): Boolean = AppStrings.resolveLangCode() == "id"

    private fun getAvailableRamMb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (mi.availMem / (1024L * 1024L)).toInt()
    }

    fun resolveTargetGamePackage(): String {
        val live = ShizukuShell.sanitizePackage(TelemetryHub.activeForegroundPkg)
        if (live.isNotEmpty() && !isSystemOrSelfPkg(live)) {
            Prefs.lastGamePackage = live
            return live
        }
        return ShizukuShell.sanitizePackage(Prefs.lastGamePackage)
    }

    fun isSystemOrSelfPkg(pkg: String): Boolean {
        return pkg == "com.shin.framepulse" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("trebuchet", ignoreCase = true) ||
            pkg == "com.android.settings" ||
            pkg == "moe.shizuku.privileged.api"
    }

    fun runQuickBoost(
        context: Context,
        targetPkgRaw: String = resolveTargetGamePackage(),
        enableFixedPerf: Boolean = Prefs.turboFixedPerf
    ): BoostResult {
        val beforeMb = getAvailableRamMb(context)
        val pkg = ShizukuShell.sanitizePackage(targetPkgRaw)

        if (!ShizukuShell.isReady()) {
            val msg = if (isId()) "Aktifkan & izinkan Shizuku dulu untuk Game Turbo" else "Enable & grant Shizuku first for Game Turbo"
            lastBoostSummary = msg
            return BoostResult(0, false, false, pkg, msg)
        }

        // 1. Safe storage/app cache trim (never run 'am kill-all' which respawns system services on One UI)
        ShizukuShell.exec("pm trim-caches 512M >/dev/null 2>&1; true", timeoutMs = 12_000L)

        // 2. Android 12-16 GameManagerService Performance Mode
        var gameModeOk = false
        if (pkg.isNotEmpty() && !isSystemOrSelfPkg(pkg)) {
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
        if (freed > 0) parts.add("RAM +${freed}MB") else parts.add("Trim OK")
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
        val pkg = ShizukuShell.sanitizePackage(pkgRaw)
        if (pkg.isEmpty() || !ShizukuShell.isReady()) return false
        val (c1, out1) = ShizukuShell.exec("cmd game mode performance '$pkg'", timeoutMs = 8_000L)
        if (c1 && !out1.contains("error", ignoreCase = true) && !out1.contains("unknown", ignoreCase = true)) {
            return true
        }
        val (c2, out2) = ShizukuShell.exec("cmd game set --mode performance '$pkg'", timeoutMs = 8_000L)
        return c2 && !out2.contains("error", ignoreCase = true) && !out2.contains("unknown", ignoreCase = true)
    }

    fun setFixedPerformanceMode(enable: Boolean): Boolean {
        if (!ShizukuShell.isReady()) {
            if (!enable) isFixedPerfActive = false
            return false
        }
        val flag = if (enable) "true" else "false"
        val (codeOk, out) = ShizukuShell.exec("cmd power set-fixed-performance-mode-enabled $flag", timeoutMs = 8_000L)
        val unsupported = out.contains("not supported", ignoreCase = true) ||
            out.contains("unknown", ignoreCase = true) ||
            out.contains("exception", ignoreCase = true)
        val ok = (codeOk && !unsupported)
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
        val pkg = ShizukuShell.sanitizePackage(pkgRaw)
        if (!ShizukuShell.isReady()) {
            return if (isId()) "Shizuku belum aktif atau belum diizinkan." else "Shizuku is not active or permitted."
        }
        if (pkg.isEmpty() || isSystemOrSelfPkg(pkg)) {
            return if (isId()) {
                "Belum ada game terdeteksi. Buka game sebentar dengan HUD aktif dulu."
            } else {
                "No game detected yet. Launch your game briefly with HUD active first."
            }
        }
        val (ok, out) = ShizukuShell.exec("cmd package compile -m speed-profile -f '$pkg'", timeoutMs = 90_000L)
        return if (ok && !out.contains("Failure", ignoreCase = true)) {
            if (isId()) "ART speed-profile OK untuk ${pkg.substringAfterLast('.')}"
            else "ART speed-profile OK for ${pkg.substringAfterLast('.')}"
        } else {
            val fallback = if (isId()) "tidak didukung" else "unsupported"
            if (isId()) "ART gagal: ${out.ifBlank { fallback }.take(60)}"
            else "ART failed: ${out.ifBlank { fallback }.take(60)}"
        }
    }

    fun shutdownSafely() {
        if (isFixedPerfActive) {
            setFixedPerformanceMode(false)
        }
        isThermalCooldown = false
    }

    fun applyGamePreset(preset: GamePreset): Pair<Boolean, String> {
        if (!ShizukuShell.isReady()) {
            return false to if (isId()) "Shizuku belum aktif. Jalankan & izinkan Shizuku dulu." else "Shizuku is not active. Start & grant Shizuku first."
        }
        val (ok, out) = ShizukuShell.exec(GameProfileEngine.buildApplyScript(preset), timeoutMs = 15_000L)
        val okCount = out.lines().lastOrNull()?.trim()?.toIntOrNull() ?: 0
        if (!ok || okCount <= 0) {
            return false to if (isId()) {
                "Gagal menerapkan preset ${preset.name} (cek izin Shizuku)"
            } else {
                "Failed to apply ${preset.name} preset (check Shizuku permission)"
            }
        }
        preset.packages.firstOrNull()?.let { firstPkg ->
            val safe = ShizukuShell.sanitizePackage(firstPkg)
            if (safe.isNotEmpty() && Prefs.lastGamePackage.isBlank()) {
                Prefs.lastGamePackage = safe
            }
        }
        lastBoostSummary = "${preset.name} (${preset.downscale}x • ${preset.targetFps} FPS)"
        val msg = if (isId()) {
            "Preset ${preset.name} aktif (Scale ${preset.downscale}x • Target ${preset.targetFps} FPS)"
        } else {
            "${preset.name} preset active (Scale ${preset.downscale}x • Target ${preset.targetFps} FPS)"
        }
        return true to msg
    }

    fun resetGamePreset(preset: GamePreset): Pair<Boolean, String> {
        if (!ShizukuShell.isReady()) {
            return false to if (isId()) "Shizuku belum aktif." else "Shizuku is not active."
        }
        val (ok, _) = ShizukuShell.exec(GameProfileEngine.buildResetScript(preset), timeoutMs = 12_000L)
        return if (ok) {
            true to if (isId()) "Setelan ${preset.name} dikembalikan ke normal" else "${preset.name} settings restored to default"
        } else {
            false to if (isId()) "Gagal mereset preset ${preset.name}" else "Failed to reset ${preset.name} preset"
        }
    }
}
