package com.shin.framepulse

import android.app.Application
import android.content.Intent
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FramePulseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        if (!isCrashProcess()) {
            installCrashHandler()
        }
    }

    private fun isCrashProcess(): Boolean {
        val procName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            null
        }
        return procName?.endsWith(":crash") == true
    }

    private fun resolveVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val ver = resolveVersionName()
                val report = buildString {
                    appendLine("FramePulse v$ver Crash Report")
                    appendLine("Time: $ts")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name}")
                    appendLine("---")
                    append(sw.toString())
                }
                Prefs.saveCrashLogSync(report)

                if (TelemetryHub.isAppForeground) {
                    val safeExtra = report.take(64_000)
                    val intent = Intent(this, CrashActivity::class.java).apply {
                        putExtra(CrashActivity.EXTRA_LOG, safeExtra)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                }
            } catch (_: Throwable) {
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
