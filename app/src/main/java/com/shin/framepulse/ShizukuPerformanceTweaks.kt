package com.shin.framepulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

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

    private fun isId(): Boolean =
        Prefs.hasChosenLanguage && Prefs.appLanguage.equals("id", ignoreCase = true)

    fun isShizukuReady(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun openShizuku(context: Context): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
            if (isId()) "Shizuku belum aktif — membuka aplikasi Shizuku..." else "Shizuku not active — opening Shizuku app..."
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webIntent) }
            if (isId()) "Shizuku belum terpasang — membuka halaman unduh..." else "Shizuku not installed — opening download page..."
        }
    }

    private fun runShizukuShell(command: String): Pair<Boolean, String> {
        return runCatching {
            if (!Shizuku.pingBinder()) {
                return Pair(false, if (isId()) "Shizuku belum aktif" else "Shizuku is not active")
            }
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            val err = BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() }
            val code = proc.waitFor()
            val combined = (out + "\n" + err).trim()
            Pair(code == 0, combined)
        }.getOrElse { e ->
            Pair(false, e.message ?: if (isId()) "Gagal menjalankan perintah Shizuku" else "Failed to run Shizuku command")
        }
    }

    fun applySmartStandbyBucket(excludePkg: String?): String {
        var affected = 0
        for (pkg in heavyBackgroundApps) {
            if (pkg == excludePkg) continue
            val (ok, _) = runShizukuShell("am set-standby-bucket $pkg restricted")
            if (ok) affected++
        }
        return if (affected > 0) {
            if (isId()) "Smart Standby aktif ($affected app latar belakang dibatasi)"
            else "Smart Standby active ($affected background apps restricted)"
        } else {
            if (isId()) "Pastikan Shizuku aktif untuk Smart Standby Bucket"
            else "Ensure Shizuku is active for Smart Standby Bucket"
        }
    }

    fun restoreStandbyBuckets(): String {
        var restored = 0
        for (pkg in heavyBackgroundApps) {
            val (ok, _) = runShizukuShell("am set-standby-bucket $pkg active")
            if (ok) restored++
        }
        return if (restored > 0) {
            if (isId()) "Standby Bucket dipulihkan ($restored app)"
            else "Standby Buckets restored ($restored apps)"
        } else {
            if (isId()) "Gagal memulihkan (cek Shizuku)" else "Failed to restore (check Shizuku)"
        }
    }

    fun applyRefreshRateLock(hz: Int): String {
        val cmd = if (hz <= 0) {
            "settings delete system min_refresh_rate; settings delete system peak_refresh_rate"
        } else {
            "settings put system min_refresh_rate $hz.0 && settings put system peak_refresh_rate $hz.0"
        }
        val (ok, err) = runShizukuShell(cmd)
        return if (ok) {
            Prefs.lockedRefreshRateHz = hz
            if (hz <= 0) {
                if (isId()) "Refresh Rate dikembalikan ke Auto" else "Refresh Rate reset to Auto"
            } else {
                if (isId()) "Refresh Rate dikunci ke ${hz}Hz" else "Refresh Rate locked to ${hz}Hz"
            }
        } else {
            val fallback = if (isId()) "Cek izin Shizuku" else "Check Shizuku permission"
            if (isId()) "Gagal atur Refresh Rate: ${err.ifBlank { fallback }}"
            else "Failed to set Refresh Rate: ${err.ifBlank { fallback }}"
        }
    }

    fun setGamingVisualMode(enabled: Boolean): String {
        val blur = if (enabled) 1 else 0
        val scale = if (enabled) "0.5" else "1.0"
        val cmd = "settings put global disable_window_blurs $blur && " +
            "settings put global window_animation_scale $scale && " +
            "settings put global transition_animation_scale $scale && " +
            "settings put global animator_duration_scale $scale"
        val (ok, err) = runShizukuShell(cmd)
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
            if (isId()) "Gagal ubah Visual Mode: ${err.ifBlank { fallback }}"
            else "Failed to change Visual Mode: ${err.ifBlank { fallback }}"
        }
    }

    fun runDeepArtCompileAndTrim(targetPkg: String): String {
        if (targetPkg.isBlank() || targetPkg == "com.shin.framepulse") {
            return if (isId()) "Buka game dulu sekali agar package terdeteksi"
            else "Open your game once first so its package is detected"
        }
        val (okTrim, _) = runShizukuShell("pm trim-caches 512M")
        val (okArt, err) = runShizukuShell("cmd package compile -m speed -f $targetPkg")
        return if (okArt) {
            val trimNote = if (okTrim) (if (isId()) " + Cache bersih" else " + Cache trimmed") else ""
            if (isId()) "Deep ART (speed) selesai untuk $targetPkg$trimNote"
            else "Deep ART (speed) finished for $targetPkg$trimNote"
        } else {
            val fallback = if (isId()) "Cek izin Shizuku" else "Check Shizuku permission"
            if (isId()) "Deep ART gagal: ${err.ifBlank { fallback }}"
            else "Deep ART failed: ${err.ifBlank { fallback }}"
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
