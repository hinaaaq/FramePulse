package com.shin.framepulse

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unified, injection-safe, deadlock-free Shizuku shell runner.
 */
object ShizukuShell {

    private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    private val newProcessMethod by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FramePulse-ShizukuWatchdog").apply { isDaemon = true }
    }

    private fun isId(): Boolean = AppStrings.resolveLangCode() == "id"

    fun sanitizePackage(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.length > 128) return ""
        return if (PACKAGE_REGEX.matches(clean)) clean else ""
    }

    fun isBinderAlive(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isReady(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /**
     * Opens Shizuku (if service is stopped) or requests runtime Shizuku permission (if binder is alive
     * but permission is not granted yet). Returns a localized user-facing toast message.
     */
    fun ensureReadyOrPrompt(context: Context, requestCode: Int = 100): String? {
        if (isReady()) return null
        if (!isBinderAlive()) {
            return openShizukuApp(context, notRunning = true)
        }
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        if (preV11) {
            return if (isId()) "Perbarui aplikasi Shizuku ke versi terbaru" else "Please update Shizuku to the latest version"
        }
        runCatching { Shizuku.requestPermission(requestCode) }
        return if (isId()) {
            "Izin Shizuku diperlukan — ketuk Izinkan pada popup lalu coba lagi"
        } else {
            "Shizuku permission needed — tap Allow on the prompt then try again"
        }
    }

    fun openShizukuApp(context: Context, notRunning: Boolean = !isBinderAlive()): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
            if (notRunning) {
                if (isId()) "Shizuku belum aktif — membuka aplikasi Shizuku..." else "Shizuku not active — opening Shizuku app..."
            } else {
                if (isId()) "Membuka aplikasi Shizuku..." else "Opening Shizuku app..."
            }
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(webIntent) }
            if (isId()) "Shizuku belum terpasang — membuka halaman resmi..." else "Shizuku not installed — opening official page..."
        }
    }

    fun openPersistentShell(): Process? {
        if (!isReady()) return null
        return runCatching {
            newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "exec 2>/dev/null; exec sh"),
                null,
                null
            ) as Process
        }.getOrNull()
    }

    fun exec(command: String, timeoutMs: Long = 20_000L): Pair<Boolean, String> {
        if (!isReady()) {
            return false to if (isId()) "Shizuku belum aktif atau izin belum diberikan" else "Shizuku is not active or permission not granted"
        }
        var proc: Process? = null
        var timedOut = false
        val timer = watchdog.schedule({
            timedOut = true
            runCatching { proc?.destroy() }
        }, timeoutMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)

        return try {
            val p = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "{ $command ; } 2>&1"),
                null,
                null
            ) as Process
            proc = p
            runCatching { p.outputStream.close() }

            val sb = StringBuilder()
            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (sb.length < 4096) {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(line)
                    }
                }
            }
            val code = p.waitFor()
            if (timedOut) {
                false to if (isId()) "Perintah Shizuku melewati batas waktu" else "Shizuku command timed out"
            } else {
                (code == 0) to sb.toString().trim()
            }
        } catch (t: Throwable) {
            val causeMsg = (t.cause?.message ?: t.message).orEmpty().trim()
            false to causeMsg.ifBlank {
                if (isId()) "Gagal menjalankan perintah Shizuku" else "Failed to run Shizuku command"
            }
        } finally {
            timer.cancel(false)
            runCatching { proc?.destroy() }
        }
    }
}
