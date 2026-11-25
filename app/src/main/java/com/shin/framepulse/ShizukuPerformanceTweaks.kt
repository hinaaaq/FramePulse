package com.shin.framepulse

import android.content.Context

object ShizukuPerformanceTweaks {

    private val heavyBackgroundApps = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.shopee.id",
        "com.tokopedia.tkpd",
        "com.google.android.youtube",
        "com.android.chrome"
    )

    private fun isId(): Boolean = AppStrings.resolveLangCode() == "id"

    fun isShizukuReady(): Boolean = ShizukuShell.isReady()

    fun openShizuku(context: Context): String =
        ShizukuShell.ensureReadyOrPrompt(context) ?: ShizukuShell.openShizukuApp(context, notRunning = false)

    fun applySmartStandbyBucket(excludePkg: String?): String {
        if (!isShizukuReady()) {
            return if (isId()) "Pastikan Shizuku aktif & diizinkan untuk Smart Standby" else "Ensure Shizuku is active & permitted for Smart Standby"
        }
        val safeExclude = ShizukuShell.sanitizePackage(excludePkg)
        val targets = heavyBackgroundApps.filter { it != safeExclude }
        val script = buildString {
            append("count=0; ")
            for (pkg in targets) {
                append("if pm path '").append(pkg).append("' >/dev/null 2>&1 && am set-standby-bucket '")
                    .append(pkg).append("' restricted >/dev/null 2>&1; then count=$((count+1)); fi; ")
            }
            append("echo \"\$count\"")
        }
        val (ok, out) = ShizukuShell.exec(script, timeoutMs = 15_000L)
        val affected = out.lines().lastOrNull()?.trim()?.toIntOrNull() ?: 0
        return when {
            ok && affected > 0 -> {
                if (isId()) "Smart Standby aktif ($affected app latar belakang dibatasi)"
                else "Smart Standby active ($affected background apps restricted)"
            }
            ok -> {
                if (isId()) "Smart Standby selesai (tidak ada app berat yang terpasang)"
                else "Smart Standby complete (no listed heavy apps installed)"
            }
            else -> {
                if (isId()) "Pastikan Shizuku aktif untuk Smart Standby Bucket"
                else "Ensure Shizuku is active for Smart Standby Bucket"
            }
        }
    }

    fun restoreStandbyBuckets(): String {
        if (!isShizukuReady()) {
            return if (isId()) "Shizuku belum aktif" else "Shizuku is not active"
        }
        val script = buildString {
            append("count=0; ")
            for (pkg in heavyBackgroundApps) {
                append("if pm path '").append(pkg).append("' >/dev/null 2>&1 && am set-standby-bucket '")
                    .append(pkg).append("' active >/dev/null 2>&1; then count=$((count+1)); fi; ")
            }
            append("echo \"\$count\"")
        }
        val (ok, out) = ShizukuShell.exec(script, timeoutMs = 15_000L)
        val restored = out.lines().lastOrNull()?.trim()?.toIntOrNull() ?: 0
        return if (ok) {
            if (isId()) "Standby Bucket dipulihkan ($restored app)"
            else "Standby Buckets restored ($restored apps)"
        } else {
            if (isId()) "Gagal memulihkan Standby Bucket (cek Shizuku)" else "Failed to restore Standby Buckets (check Shizuku)"
        }
    }

    fun applyRefreshRateLock(hz: Int): String {
        val safeHz = hz.coerceIn(0, 240)
        val cmd = if (safeHz <= 0) {
            "settings delete system min_refresh_rate >/dev/null 2>&1; settings delete system peak_refresh_rate >/dev/null 2>&1; true"
        } else {
            "settings put system min_refresh_rate $safeHz.0 && settings put system peak_refresh_rate $safeHz.0"
        }
        val (ok, err) = ShizukuShell.exec(cmd)
        return if (ok) {
            Prefs.lockedRefreshRateHz = safeHz
            if (safeHz <= 0) {
                if (isId()) "Refresh Rate dikembalikan ke Auto" else "Refresh Rate reset to Auto"
            } else {
                if (isId()) "Refresh Rate dikunci ke ${safeHz}Hz" else "Refresh Rate locked to ${safeHz}Hz"
            }
        } else {
            val fallback = if (isId()) "Cek izin Shizuku" else "Check Shizuku permission"
            if (isId()) "Gagal atur Refresh Rate: ${err.ifBlank { fallback }.take(80)}"
            else "Failed to set Refresh Rate: ${err.ifBlank { fallback }.take(80)}"
        }
    }

    fun setGamingVisualMode(enabled: Boolean): String {
        val blur = if (enabled) 1 else 0
        val scale = if (enabled) "0.5" else "1.0"
        val cmd = "settings put global disable_window_blurs $blur && " +
            "settings put global window_animation_scale $scale && " +
            "settings put global transition_animation_scale $scale && " +
            "settings put global animator_duration_scale $scale"
        val (ok, err) = ShizukuShell.exec(cmd)
        return if (ok) {
            Prefs.gamingVisualMode = enabled
            if (enabled) {
                if (isId()) "Gaming Visual Mode AKTIF (Blur OFF, Animasi 0.5x)"
                else "Gaming Visual Mode ON (Blur OFF, 0.5x Anim)"
            } else {
                if (isId()) "Gaming Visual Mode NONAKTIF (Normal)"
                else "Gaming Visual Mode OFF (Normal)"
            }
        } else {
            val fallback = if (isId()) "Cek izin Shizuku" else "Check Shizuku permission"
            if (isId()) "Gagal ubah Visual Mode: ${err.ifBlank { fallback }.take(80)}"
            else "Failed to change Visual Mode: ${err.ifBlank { fallback }.take(80)}"
        }
    }

    fun runDeepArtCompileAndTrim(targetPkg: String): String {
        if (!isShizukuReady()) {
            return if (isId()) "Shizuku belum aktif atau izin belum diberikan" else "Shizuku is not active or permission not granted"
        }
        val safePkg = ShizukuShell.sanitizePackage(targetPkg)
        if (safePkg.isBlank() || safePkg == "com.shin.framepulse") {
            return if (isId()) "Buka game dulu sekali agar package terdeteksi"
            else "Open your game once first so its package is detected"
        }
        val (okTrim, _) = ShizukuShell.exec("pm trim-caches 512M", timeoutMs = 15_000L)
        val (okArt, err) = ShizukuShell.exec(
            "cmd package compile -m speed -f '$safePkg'",
            timeoutMs = 90_000L
        )
        return if (okArt && !err.contains("Failure", ignoreCase = true)) {
            val trimNote = if (okTrim) (if (isId()) " + Cache bersih" else " + Cache trimmed") else ""
            if (isId()) "Deep ART (speed) selesai untuk $safePkg$trimNote"
            else "Deep ART (speed) finished for $safePkg$trimNote"
        } else {
            val fallback = if (isId()) "Cek izin Shizuku" else "Check Shizuku permission"
            if (isId()) "Deep ART gagal: ${err.ifBlank { fallback }.take(80)}"
            else "Deep ART failed: ${err.ifBlank { fallback }.take(80)}"
        }
    }

    /**
     * 1-Tap Reset: Pulihkan Standby Bucket, Refresh Rate (Auto), dan Gaming Visual Mode (Normal).
     */
    fun resetAllTweaks(): String {
        if (!isShizukuReady()) {
            return if (isId()) "Shizuku belum aktif" else "Shizuku is not active"
        }
        restoreStandbyBuckets()
        applyRefreshRateLock(0)
        setGamingVisualMode(false)
        return if (isId()) {
            "Semua tweak dipulihkan (Standby Active, Hz Auto, Visual Normal)"
        } else {
            "All tweaks restored (Standby Active, Auto Hz, Normal Visuals)"
        }
    }
}
