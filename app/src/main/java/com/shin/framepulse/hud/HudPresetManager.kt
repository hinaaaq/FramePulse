package com.shin.framepulse.hud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class HudDensityMode {
    COMPACT,
    DETAILED
}

data class HudGamePreset(
    val gameKey: String,
    val displayName: String,
    val densityMode: HudDensityMode = HudDensityMode.COMPACT,
    val opacityPercent: Int = 85,
    val show1PercentLow: Boolean = true,
    val showPeakTemp: Boolean = true,
    val showRecordButton: Boolean = true
) {
    fun normalizedOpacity(): Int = opacityPercent.coerceIn(30, 100)
}

class HudPresetManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePreset(preset: HudGamePreset): List<HudGamePreset> {
        val cleanKey = normalizeKey(preset.gameKey.ifBlank { preset.displayName })
        val cleanPreset = preset.copy(
            gameKey = cleanKey,
            displayName = preset.displayName.ifBlank { "Default" },
            opacityPercent = preset.normalizedOpacity()
        )
        val updated = loadAllPresets()
            .filterNot { normalizeKey(it.gameKey) == cleanKey }
            .toMutableList()
            .apply { add(0, cleanPreset) }
            .take(MAX_PRESETS)

        persistPresets(updated)
        prefs.edit().putString(KEY_ACTIVE_GAME, cleanKey).apply()
        return updated
    }

    fun getPresetForGame(gameName: String): HudGamePreset {
        val cleanKey = normalizeKey(gameName)
        return loadAllPresets().firstOrNull { normalizeKey(it.gameKey) == cleanKey }
            ?: HudGamePreset(
                gameKey = cleanKey.ifBlank { "default" },
                displayName = gameName.ifBlank { "Default" }
            )
    }

    fun getActivePreset(): HudGamePreset {
        val activeKey = prefs.getString(KEY_ACTIVE_GAME, "default") ?: "default"
        return getPresetForGame(activeKey)
    }

    fun setActiveGame(gameName: String): HudGamePreset {
        val cleanKey = normalizeKey(gameName).ifBlank { "default" }
        prefs.edit().putString(KEY_ACTIVE_GAME, cleanKey).apply()
        return getPresetForGame(cleanKey)
    }

    fun loadAllPresets(): List<HudGamePreset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val key = obj.optString("gameKey", "")
                    if (key.isBlank()) continue
                    val modeName = obj.optString("densityMode", HudDensityMode.COMPACT.name)
                    val mode = runCatching { HudDensityMode.valueOf(modeName) }
                        .getOrDefault(HudDensityMode.COMPACT)
                    add(
                        HudGamePreset(
                            gameKey = key,
                            displayName = obj.optString("displayName", key),
                            densityMode = mode,
                            opacityPercent = obj.optInt("opacityPercent", 85).coerceIn(30, 100),
                            show1PercentLow = obj.optBoolean("show1PercentLow", true),
                            showPeakTemp = obj.optBoolean("showPeakTemp", true),
                            showRecordButton = obj.optBoolean("showRecordButton", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistPresets(presets: List<HudGamePreset>) {
        val arr = JSONArray()
        for (item in presets) {
            val obj = JSONObject().apply {
                put("gameKey", item.gameKey)
                put("displayName", item.displayName)
                put("densityMode", item.densityMode.name)
                put("opacityPercent", item.opacityPercent)
                put("show1PercentLow", item.show1PercentLow)
                put("showPeakTemp", item.showPeakTemp)
                put("showRecordButton", item.showRecordButton)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    companion object {
        private const val PREFS_NAME = "framepulse_hud_presets"
        private const val KEY_PRESETS = "hud_presets_json_v1"
        private const val KEY_ACTIVE_GAME = "active_game_key"
        private const val MAX_PRESETS = 30
    }
}
