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
        val avgFt = if (summary.avgFps.isFinite() && summary.avgFps > 0.1f) 1000f / summary.avgFps else 0f
        val valid = fpsSamples.filter { it > 0 }
        val threshold = (summary.avgFps * 0.78f).coerceAtLeast(24f)
        val stutters = if (valid.isNotEmpty()) {
            valid.count { it < threshold }
        } else {
            val gap = (summary.avgFps - summary.onePercentLowFps).coerceAtLeast(0f)
            (gap / 4f).toInt()
        }
        val stability = if (summary.avgFps.isFinite() && summary.avgFps > 0f) {
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
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                val written = context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(sb.toString().toByteArray(Charsets.UTF_8))
                    true
                } ?: false
                if (!written) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    return@runCatching null
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                "Download/FramePulse/$fileName"
            } else {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                val dir = File(baseDir, "Benchmarks").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                file.absolutePath
            }
        }.getOrNull()
    }

    fun exportPngCard(context: Context, summary: BenchmarkSummary, fpsSamples: List<Int>): String? {
        var bmp: Bitmap? = null
        return runCatching {
            val isId = AppStrings.resolveLangCode() == "id"
            val metrics = computeMetrics(summary, fpsSamples)
            val width = 1080
            val height = 640
            val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp = created
            val canvas = Canvas(created)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xFF070B14.toInt(), 0xFF0F172A.toInt(), 0xFF091326.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = 0xFF22D3EE.toInt()
                alpha = 110
            }
            canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 28f, 28f, borderPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF22D3EE.toInt()
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("FRAMEPULSE BENCHMARK", 60f, 96f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF94A3B8.toInt()
                textSize = 24f
                typeface = Typeface.MONOSPACE
            }
            val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
            canvas.drawText(deviceLabel, 60f, 136f, subPaint)

            val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111C33.toInt()
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF94A3B8.toInt()
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 46f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            fun drawBox(x: Float, y: Float, w: Float, h: Float, label: String, value: String, accentColor: Int) {
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), 20f, 20f, boxBg)
                valPaint.color = accentColor
                canvas.drawText(label, x + 24f, y + 46f, labelPaint)
                canvas.drawText(value, x + 24f, y + 108f, valPaint)
            }

            drawBox(60f, 176f, 300f, 140f, "AVG FPS", String.format(Locale.US, "%.1f", summary.avgFps), 0xFF10B981.toInt())
            drawBox(390f, 176f, 300f, 140f, "1% LOW FPS", "${summary.onePercentLowFps}", 0xFF22D3EE.toInt())
            drawBox(720f, 176f, 300f, 140f, "FRAMETIME", String.format(Locale.US, "%.1f ms", metrics.avgFrameTimeMs), 0xFF60A5FA.toInt())

            drawBox(60f, 344f, 300f, 140f, "STUTTER EVENTS", "${metrics.stutterCount}x", 0xFFF59E0B.toInt())
            drawBox(390f, 344f, 300f, 140f, "PEAK TEMP", String.format(Locale.US, "%.1f°C", summary.peakTempC), 0xFFF87171.toInt())
            drawBox(720f, 344f, 300f, 140f, "STABILITY", "${metrics.stabilityPercent}%", 0xFFA78BFA.toInt())

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 22f
                typeface = Typeface.MONOSPACE
            }
            val durLabel = if (isId) "Durasi" else "Duration"
            canvas.drawText(
                "Min ${summary.minFps} / Max ${summary.maxFps} FPS • $durLabel ${summary.durationSec}s • ${summary.sourceLabel}",
                60f,
                554f,
                footerPaint
            )
            canvas.drawText("github.com/hinaaaq/FramePulse", 60f, 592f, footerPaint)

            val fileName = "FramePulse_Card_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FramePulse")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            val ok = context.contentResolver.openOutputStream(uri)?.use { os ->
                created.compress(Bitmap.CompressFormat.PNG, 100, os)
            } ?: false
            if (!ok) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return@runCatching null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            "Pictures/FramePulse/$fileName"
        }.also {
            runCatching { bmp?.recycle() }
        }.getOrNull()
    }
}
