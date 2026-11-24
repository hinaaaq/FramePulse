package com.shin.framepulse.benchmark

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class BenchmarkSessionRecord(
    val sessionId: String,
    val timestampMs: Long,
    val gameOrLabel: String,
    val durationSec: Int,
    val avgFps: Float,
    val low1PercentFps: Float,
    val peakTempC: Float,
    val thermalThrottleDetected: Boolean,
    val verified: Boolean
)

data class BenchmarkComparison(
    val current: BenchmarkSessionRecord,
    val baseline: BenchmarkSessionRecord,
    val deltaAvgFps: Float,
    val deltaLow1PercentFps: Float,
    val deltaPeakTempC: Float
) {
    fun summaryText(): String {
        val fpsSign = if (deltaAvgFps >= 0f) "+" else ""
        val lowSign = if (deltaLow1PercentFps >= 0f) "+" else ""
        val tempSign = if (deltaPeakTempC >= 0f) "+" else ""
        return "vs ${baseline.gameOrLabel} (${baseline.durationSec}s): " +
            "Avg ${fpsSign}${fmt1(deltaAvgFps)} FPS, " +
            "1% Low ${lowSign}${fmt1(deltaLow1PercentFps)} FPS, " +
            "Peak Temp ${tempSign}${fmt1(deltaPeakTempC)}°C"
    }

    private fun fmt1(v: Float): String = ((v * 10f).roundToInt() / 10f).toString()
}

class BenchmarkHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(record: BenchmarkSessionRecord): List<BenchmarkSessionRecord> {
        val currentList = loadSessions().toMutableList()
        currentList.removeAll { it.sessionId == record.sessionId }
        currentList.add(0, record)
        val trimmed = currentList.take(MAX_SESSIONS)
        persistSessions(trimmed)
        return trimmed
    }

    fun loadSessions(): List<BenchmarkSessionRecord> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val sessionId = obj.optString("sessionId", "")
                    if (sessionId.isBlank()) continue
                    add(
                        BenchmarkSessionRecord(
                            sessionId = sessionId,
                            timestampMs = obj.optLong("timestampMs", 0L),
                            gameOrLabel = obj.optString("gameOrLabel", "Unknown Session"),
                            durationSec = obj.optInt("durationSec", 0),
                            avgFps = obj.optDouble("avgFps", 0.0).toFloat(),
                            low1PercentFps = obj.optDouble("low1PercentFps", 0.0).toFloat(),
                            peakTempC = obj.optDouble("peakTempC", 0.0).toFloat(),
                            thermalThrottleDetected = obj.optBoolean("thermalThrottleDetected", false),
                            verified = obj.optBoolean("verified", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun compareWithPrevious(sessionId: String? = null): BenchmarkComparison? {
        val sessions = loadSessions()
        if (sessions.size < 2) return null
        val currentIndex = if (sessionId == null) {
            0
        } else {
            sessions.indexOfFirst { it.sessionId == sessionId }.takeIf { it >= 0 } ?: 0
        }
        val current = sessions[currentIndex]
        val baseline = sessions.drop(currentIndex + 1).firstOrNull {
            it.gameOrLabel.equals(current.gameOrLabel, ignoreCase = true)
        } ?: sessions.getOrNull(currentIndex + 1) ?: return null

        return BenchmarkComparison(
            current = current,
            baseline = baseline,
            deltaAvgFps = current.avgFps - baseline.avgFps,
            deltaLow1PercentFps = current.low1PercentFps - baseline.low1PercentFps,
            deltaPeakTempC = current.peakTempC - baseline.peakTempC
        )
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_SESSIONS).apply()
    }

    private fun persistSessions(sessions: List<BenchmarkSessionRecord>) {
        val arr = JSONArray()
        for (item in sessions) {
            val obj = JSONObject().apply {
                put("sessionId", item.sessionId)
                put("timestampMs", item.timestampMs)
                put("gameOrLabel", item.gameOrLabel)
                put("durationSec", item.durationSec)
                put("avgFps", item.avgFps.toDouble())
                put("low1PercentFps", item.low1PercentFps.toDouble())
                put("peakTempC", item.peakTempC.toDouble())
                put("thermalThrottleDetected", item.thermalThrottleDetected)
                put("verified", item.verified)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "framepulse_benchmark_history"
        private const val KEY_SESSIONS = "sessions_json_v1"
        private const val MAX_SESSIONS = 20
    }
}
