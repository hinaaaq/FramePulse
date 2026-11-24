package com.shin.framepulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class BenchmarkAdvancedMetrics(
    val avgFrameTimeMs: Float,
    val stutterCount: Int,
    val stabilityPercent: Int
)

object BenchmarkExporter {

    fun computeMetrics(summary: BenchmarkSummary, fpsSamples: List<Int>): BenchmarkAdvancedMetrics {
        val avgFt = if (summary.avgFps > 0.1f) 1000f / summary.avgFps else 0f
        val valid = fpsSamples.filter { it > 0 }
        val threshold = (summary.avgFps * 0.78f).coerceAtLeast(24f)
        val stutters = if (valid.isNotEmpty()) {
            valid.count { it < threshold }
        } else {
            val gap = (summary.avgFps - summary.onePercentLowFps).coerceAtLeast(0f)
            (gap / 4f).toInt()
        }
        val stability = if (summary.avgFps > 0f) {
            ((summary.onePercentLowFps / summary.avgFps) * 100f).toInt().coerceIn(0, 100)
        } else 0

        return BenchmarkAdvancedMetrics(
            avgFrameTimeMs = avgFt,
            stutterCount = stutters,
            stabilityPercent = stability
        )
    }

    fun exportCsv(context: Context, summary: BenchmarkSummary, fpsSamples: List<Int>): String? {
        return runCatching {
            val metrics = computeMetrics(summary, fpsSamples)
            val safeTs = System.currentTimeMillis()
            val fileName = "FramePulse_Benchmark_${safeTs}.csv"

            val sb = StringBuilder()
            sb.appendLine("Metric,Value")
            sb.appendLine("Device,\"${Build.MANUFACTURER} ${Build.MODEL}\"")
            sb.appendLine("Android,\"Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\"")
            sb.appendLine("Timestamp,\"${summary.timestamp}\"")
            sb.appendLine("DurationSec,${summary.durationSec}")
            sb.appendLine("AvgFPS,${String.format(Locale.US, "%.1f", summary.avgFps)}")
            sb.appendLine("OnePercentLowFPS,${summary.onePercentLowFps}")
            sb.appendLine("MinFPS,${summary.minFps}")
            sb.appendLine("MaxFPS,${summary.maxFps}")
            sb.appendLine("AvgFrameTimeMs,${String.format(Locale.US, "%.2f", metrics.avgFrameTimeMs)}")
            sb.appendLine("StutterEvents,${metrics.stutterCount}")
            sb.appendLine("StabilityPercent,${metrics.stabilityPercent}%")
            sb.appendLine("PeakTempC,${String.format(Locale.US, "%.1f", summary.peakTempC)}")
            sb.appendLine("Source,\"${summary.sourceLabel}\"")
            sb.appendLine()
            sb.appendLine("SampleIndex,FPS,FrameTimeMs")
            fpsSamples.forEachIndexed { idx, fps ->
                val ft = if (fps > 0) 1000f / fps else 0f
                sb.appendLine("${idx + 1},$fps,${String.format(Locale.US, "%.2f", ft)}")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FramePulse")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                "Download/FramePulse/$fileName"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FramePulse")
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                file.absolutePath
            }
        }.getOrNull()
    }

    fun exportPngCard(context: Context, summary: BenchmarkSummary, fpsSamples: List<Int>): String? {
        return runCatching {
            val metrics = computeMetrics(summary, fpsSamples)
            val width = 1080
            val height = 640
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Background gradient
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#070B14"), Color.parseColor("#0F172A"), Color.parseColor("#091326")),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Outer card border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.parseColor("#22D3EE")
                alpha = 110
            }
            canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 28f, 28f, borderPaint)

            // Header
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#22D3EE")
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("FRAMEPULSE BENCHMARK", 60f, 96f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 24f
                typeface = Typeface.MONOSPACE
            }
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
            canvas.drawText(deviceLabel, 60f, 136f, subPaint)

            // Stat boxes
            val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#111C33")
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 46f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            fun drawBox(x: Float, y: Float, w: Float, h: Float, label: String, value: String, accentHex: String) {
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), 20f, 20f, boxBg)
                valPaint.color = Color.parseColor(accentHex)
                canvas.drawText(label, x + 24f, y + 46f, labelPaint)
                canvas.drawText(value, x + 24f, y + 108f, valPaint)
            }

            drawBox(60f, 176f, 300f, 140f, "AVG FPS", String.format(Locale.US, "%.1f", summary.avgFps), "#10B981")
            drawBox(390f, 176f, 300f, 140f, "1% LOW FPS", "${summary.onePercentLowFps}", "#22D3EE")
            drawBox(720f, 176f, 300f, 140f, "FRAMETIME", String.format(Locale.US, "%.1f ms", metrics.avgFrameTimeMs), "#60A5FA")

            drawBox(60f, 344f, 300f, 140f, "STUTTER EVENTS", "${metrics.stutterCount}x", "#F59E0B")
            drawBox(390f, 344f, 300f, 140f, "PEAK TEMP", String.format(Locale.US, "%.1f°C", summary.peakTempC), "#F87171")
            drawBox(720f, 344f, 300f, 140f, "STABILITY", "${metrics.stabilityPercent}%", "#A78BFA")

            // Footer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 22f
                typeface = Typeface.MONOSPACE
            }
            canvas.drawText(
                "Min ${summary.minFps} / Max ${summary.maxFps} FPS • Durasi ${summary.durationSec}s • ${summary.sourceLabel}",
                60f,
                554f,
                footerPaint
            )
            canvas.drawText("github.com/hinaaaq/FramePulse", 60f, 592f, footerPaint)

            val fileName = "FramePulse_Card_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FramePulse")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
                "Pictures/FramePulse/$fileName"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FramePulse")
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                file.absolutePath
            }
        }.getOrNull()
    }
}
