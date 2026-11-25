package com.shin.framepulse.v110

import com.shin.framepulse.AppStrings
import com.shin.framepulse.benchmark.BenchmarkComparison
import com.shin.framepulse.benchmark.BenchmarkSessionRecord
import com.shin.framepulse.hud.HudDensityMode
import kotlin.math.roundToInt

data class HudDisplayLines(
    val primaryFpsLabel: String,
    val secondaryLine: String?,
    val thermalLine: String?,
    val alpha: Float
)

object FramePulseSessionInsights {

    fun buildHudLines(
        config: HudRuntimeConfig,
        liveFps: Float,
        low1PercentFps: Float,
        tempC: Float,
        isRecording: Boolean,
        elapsedSec: Int
    ): HudDisplayLines {
        val fpsText = "${fmt1(liveFps)} FPS"
        val recBadge = if (isRecording) " • REC ${elapsedSec}s" else ""
        val primary = "$fpsText$recBadge"

        val secondary = when (config.densityMode) {
            HudDensityMode.COMPACT -> {
                if (config.show1PercentLow) "1%L ${fmt1(low1PercentFps)}" else null
            }
            HudDensityMode.DETAILED -> {
                val parts = mutableListOf<String>()
                if (config.show1PercentLow) parts.add("1% Low: ${fmt1(low1PercentFps)} FPS")
                parts.add("Game: ${config.activeGame}")
                parts.joinToString(" • ")
            }
        }

        val thermal = if (config.showPeakTemp || config.densityMode == HudDensityMode.DETAILED) {
            "Temp: ${fmt1(tempC)}°C"
        } else {
            null
        }

        val alpha = (config.normalizedOpacityPercent() / 100f).coerceIn(0.3f, 1.0f)
        return HudDisplayLines(
            primaryFpsLabel = primary,
            secondaryLine = secondary,
            thermalLine = thermal,
            alpha = alpha
        )
    }

    fun formatComparisonHeadline(comparison: BenchmarkComparison?, isId: Boolean = AppStrings.resolveLangCode() == "id"): String {
        if (comparison == null) {
            return if (isId) "Belum ada sesi pembanding untuk selisih metrik."
            else "Record at least 2 sessions to compare Δ FPS & thermal shifts."
        }
        val lowDelta = comparison.deltaLow1PercentFps
        val tempDelta = comparison.deltaPeakTempC
        val lowNote = if (isId) {
            when {
                lowDelta >= 2.0f -> "1% Low naik (+${fmt1(lowDelta)} FPS)"
                lowDelta <= -2.0f -> "1% Low turun (${fmt1(lowDelta)} FPS)"
                else -> "1% Low stabil (${if (lowDelta >= 0f) "+" else ""}${fmt1(lowDelta)} FPS)"
            }
        } else {
            when {
                lowDelta >= 2.0f -> "1% Low up (+${fmt1(lowDelta)} FPS)"
                lowDelta <= -2.0f -> "1% Low down (${fmt1(lowDelta)} FPS)"
                else -> "1% Low steady (${if (lowDelta >= 0f) "+" else ""}${fmt1(lowDelta)} FPS)"
            }
        }
        val tempNote = if (isId) {
            when {
                tempDelta <= -1.5f -> "suhu lebih adem (${fmt1(tempDelta)}°C)"
                tempDelta >= 1.5f -> "suhu puncak naik (+${fmt1(tempDelta)}°C)"
                else -> "suhu stabil (${if (tempDelta >= 0f) "+" else ""}${fmt1(tempDelta)}°C)"
            }
        } else {
            when {
                tempDelta <= -1.5f -> "cooler peak (${fmt1(tempDelta)}°C)"
                tempDelta >= 1.5f -> "warmer peak (+${fmt1(tempDelta)}°C)"
                else -> "steady temp (${if (tempDelta >= 0f) "+" else ""}${fmt1(tempDelta)}°C)"
            }
        }
        return "$lowNote • $tempNote"
    }

    fun buildShareableSessionReport(
        record: BenchmarkSessionRecord,
        comparison: BenchmarkComparison?
    ): String = buildString {
        appendLine("FramePulse Benchmark — ${record.gameOrLabel}")
        appendLine("Avg FPS: ${fmt1(record.avgFps)} | 1% Low: ${fmt1(record.low1PercentFps)} | Peak: ${fmt1(record.peakTempC)}°C (${record.durationSec}s)")
        appendLine("Thermal Throttle: ${if (record.thermalThrottleDetected) "Detected" else "None"} | Verified: ${if (record.verified) "Yes" else "Unverified"}")
        if (comparison != null) {
            appendLine("Delta: ${comparison.summaryText()}")
        }
    }.trim()

    private fun HudRuntimeConfig.normalizedOpacityPercent(): Int = opacityPercent.coerceIn(30, 100)

    private fun fmt1(value: Float): String {
        if (!value.isFinite()) return "0.0"
        return ((value * 10f).roundToInt() / 10f).toString()
    }
}
