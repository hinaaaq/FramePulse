package com.shin.framepulse.v110

import android.content.Context
import com.shin.framepulse.benchmark.BenchmarkComparison
import com.shin.framepulse.benchmark.BenchmarkHistoryStore
import com.shin.framepulse.benchmark.BenchmarkSessionRecord
import com.shin.framepulse.hud.HudDensityMode
import com.shin.framepulse.hud.HudGamePreset
import com.shin.framepulse.hud.HudPresetManager

data class HudRuntimeConfig(
    val activeGame: String,
    val densityMode: HudDensityMode,
    val opacityPercent: Int,
    val show1PercentLow: Boolean,
    val showPeakTemp: Boolean,
    val showRecordButton: Boolean
)

class FramePulseV110Bridge(context: Context) {

    private val historyStore = BenchmarkHistoryStore(context)
    private val presetManager = HudPresetManager(context)

    fun recordCompletedSession(
        sessionId: String,
        timestampMs: Long,
        gameOrLabel: String,
        durationSec: Int,
        avgFps: Float,
        low1PercentFps: Float,
        peakTempC: Float,
        thermalThrottleDetected: Boolean,
        verified: Boolean
    ): Pair<List<BenchmarkSessionRecord>, BenchmarkComparison?> {
        val cleanGame = gameOrLabel.trim().ifBlank {
            presetManager.getActivePreset().displayName
        }
        val record = BenchmarkSessionRecord(
            sessionId = sessionId,
            timestampMs = timestampMs,
            gameOrLabel = cleanGame,
            durationSec = durationSec.coerceAtLeast(0),
            avgFps = avgFps,
            low1PercentFps = low1PercentFps,
            peakTempC = peakTempC,
            thermalThrottleDetected = thermalThrottleDetected,
            verified = verified
        )
        val sessions = historyStore.saveSession(record)
        val comparison = historyStore.compareWithPrevious(sessionId)
        return sessions to comparison
    }

    fun recentSessions(): List<BenchmarkSessionRecord> = historyStore.loadSessions()

    fun latestComparison(): BenchmarkComparison? = historyStore.compareWithPrevious(null)

    fun clearSessionHistory() = historyStore.clearHistory()

    fun currentHudConfig(): HudRuntimeConfig {
        val preset = presetManager.getActivePreset()
        return preset.toRuntimeConfig()
    }

    fun selectGameProfile(gameName: String): HudRuntimeConfig {
        val preset = presetManager.setActiveGame(gameName)
        return preset.toRuntimeConfig()
    }

    fun updateHudDensity(mode: HudDensityMode): HudRuntimeConfig {
        val current = presetManager.getActivePreset()
        val updated = current.copy(densityMode = mode)
        presetManager.savePreset(updated)
        return updated.toRuntimeConfig()
    }

    fun saveGameHudPreset(
        gameName: String,
        densityMode: HudDensityMode,
        opacityPercent: Int,
        show1PercentLow: Boolean,
        showPeakTemp: Boolean,
        showRecordButton: Boolean
    ): HudRuntimeConfig {
        val cleanName = gameName.trim().ifBlank { "Default" }
        val preset = HudGamePreset(
            gameKey = cleanName,
            displayName = cleanName,
            densityMode = densityMode,
            opacityPercent = opacityPercent,
            show1PercentLow = show1PercentLow,
            showPeakTemp = showPeakTemp,
            showRecordButton = showRecordButton
        )
        presetManager.savePreset(preset)
        return preset.toRuntimeConfig()
    }

    private fun HudGamePreset.toRuntimeConfig(): HudRuntimeConfig = HudRuntimeConfig(
        activeGame = displayName,
        densityMode = densityMode,
        opacityPercent = normalizedOpacity(),
        show1PercentLow = show1PercentLow,
        showPeakTemp = showPeakTemp,
        showRecordButton = showRecordButton
    )
}
