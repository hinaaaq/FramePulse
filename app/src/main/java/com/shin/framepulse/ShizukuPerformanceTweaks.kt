package com.shin.framepulse

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

    private fun runShizukuShell(command: String): Pair<Boolean, String> {
        return runCatching {
            if (!Shizuku.pingBinder()) {
                return Pair(false, "Shizuku belum aktif")
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
            Pair(false, e.message ?: "Gagal menjalankan perintah Shizuku")
        }
    }

    /**
     * 1. Smart Standby Bucket: Batasi aplikasi berat latar belakang tanpa kill-all paksa.
     */
    fun applySmartStandbyBucket(excludePkg: String?): String {
        var affected = 0
        for (pkg in heavyBackgroundApps) {
            if (pkg == excludePkg) continue
            val (ok, _) = runShizukuShell("am set-standby-bucket $pkg restricted")
            if (ok) affected++
        }
        return if (affected > 0) {
            "Smart Standby aktif ($affected app latar belakang dibatasi)"
        } else {
            "Pastikan Shizuku aktif untuk Smart Standby Bucket"
        }
    }

    fun restoreStandbyBuckets(): String {
        var restored = 0
        for (pkg in heavyBackgroundApps) {
            val (ok, _) = runShizukuShell("am set-standby-bucket $pkg active")
            if (ok) restored++
        }
        return if (restored > 0) "Standby Bucket dipulihkan ($restored app)" else "Gagal memulihkan (cek Shizuku)"
    }

    /**
     * 2. Refresh Rate Lock: Kunci 60Hz / 90Hz atau Reset ke Auto (0).
     */
    fun applyRefreshRateLock(hz: Int): String {
        val cmd = if (hz <= 0) {
            "settings delete system min_refresh_rate; settings delete system peak_refresh_rate"
        } else {
            "settings put system min_refresh_rate $hz.0 && settings put system peak_refresh_rate $hz.0"
        }
        val (ok, err) = runShizukuShell(cmd)
        return if (ok) {
            Prefs.lockedRefreshRateHz = hz
            if (hz <= 0) "Refresh Rate dikembalikan ke Auto" else "Refresh Rate dikunci ke ${hz}Hz"
        } else {
            "Gagal atur Refresh Rate: ${err.ifBlank { "Cek izin Shizuku" }}"
        }
    }

    /**
     * 3. Gaming Visual Mode: Matikan Window Blur & ringankan skala animasi (0.5x).
     */
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
            if (enabled) "Gaming Visual Mode AKTIF (Blur OFF, Animasi 0.5x)" else "Gaming Visual Mode NONAKTIF (Normal)"
        } else {
            "Gagal ubah Visual Mode: ${err.ifBlank { "Cek izin Shizuku" }}"
        }
    }

    /**
     * 4. Deep ART Compile (speed) + Trim Cache khusus 1 game target.
     */
    fun runDeepArtCompileAndTrim(targetPkg: String): String {
        if (targetPkg.isBlank() || targetPkg == "com.shin.framepulse") {
            return "Buka game dulu sekali agar package terdeteksi"
        }
        val (okTrim, _) = runShizukuShell("pm trim-caches 512M")
        val (okArt, err) = runShizukuShell("cmd package compile -m speed -f $targetPkg")
        return if (okArt) {
            val trimNote = if (okTrim) " + Cache bersih" else ""
            "Deep ART (speed) selesai untuk $targetPkg$trimNote"
        } else {
            "Deep ART gagal: ${err.ifBlank { "Cek izin Shizuku" }}"
        }
    }
}
