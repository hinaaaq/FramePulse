package com.shin.framepulse

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "framepulse_pro_prefs"

    const val LAYOUT_HORIZONTAL = "HORIZONTAL"
    const val LAYOUT_VERTICAL = "VERTICAL"
    const val LAYOUT_MINI = "MINI"

    const val STYLE_GLASS = "GLASS"
    const val STYLE_PILL = "PILL"
    const val STYLE_MINIMAL = "MINIMAL"

    const val THEME_CYBER = "CYBER"
    const val THEME_EMERALD = "EMERALD"
    const val THEME_CRIMSON = "CRIMSON"
    const val THEME_AMBER = "AMBER"

    @Volatile
    private var appCtx: Context? = null

    fun init(context: Context) {
        if (appCtx == null) {
            appCtx = context.applicationContext
        }
    }

    private fun sp(): SharedPreferences {
        val ctx = appCtx ?: throw IllegalStateException("Prefs.init() must be called first")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var layoutMode: String
        get() = sp().getString("layout_mode", LAYOUT_HORIZONTAL) ?: LAYOUT_HORIZONTAL
        set(v) = sp().edit().putString("layout_mode", v).apply()

    var hudStyle: String
        get() = sp().getString("hud_style", STYLE_GLASS) ?: STYLE_GLASS
        set(v) = sp().edit().putString("hud_style", v).apply()

    var themeMode: String
        get() = sp().getString("theme_mode", THEME_CYBER) ?: THEME_CYBER
        set(v) = sp().edit().putString("theme_mode", v).apply()

    var showFps: Boolean
        get() = sp().getBoolean("show_fps", true)
        set(v) = sp().edit().putBoolean("show_fps", v).apply()

    var showFrameTime: Boolean
        get() = sp().getBoolean("show_frametime", true)
        set(v) = sp().edit().putBoolean("show_frametime", v).apply()

    var showCpu: Boolean
        get() = sp().getBoolean("show_cpu", true)
        set(v) = sp().edit().putBoolean("show_cpu", v).apply()

    var showClock: Boolean
        get() = sp().getBoolean("show_clock", false)
        set(v) = sp().edit().putBoolean("show_clock", v).apply()

    var showGpu: Boolean
        get() = sp().getBoolean("show_gpu", true)
        set(v) = sp().edit().putBoolean("show_gpu", v).apply()

    var showRam: Boolean
        get() = sp().getBoolean("show_ram", false)
        set(v) = sp().edit().putBoolean("show_ram", v).apply()

    var showTemp: Boolean
        get() = sp().getBoolean("show_temp", true)
        set(v) = sp().edit().putBoolean("show_temp", v).apply()

    var showBatteryPct: Boolean
        get() = sp().getBoolean("show_battery_pct", false)
        set(v) = sp().edit().putBoolean("show_battery_pct", v).apply()

    var showWatt: Boolean
        get() = sp().getBoolean("show_watt", false)
        set(v) = sp().edit().putBoolean("show_watt", v).apply()

    var showNet: Boolean
        get() = sp().getBoolean("show_net", false)
        set(v) = sp().edit().putBoolean("show_net", v).apply()

    var showHudGraph: Boolean
        get() = sp().getBoolean("show_hud_graph", true)
        set(v) = sp().edit().putBoolean("show_hud_graph", v).apply()

    var snapToEdge: Boolean
        get() = sp().getBoolean("snap_to_edge", true)
        set(v) = sp().edit().putBoolean("snap_to_edge", v).apply()

    var graphMaxFps: Int
        get() = sp().getInt("graph_max_fps", 0) // 0 = Auto screen refresh rate
        set(v) = sp().edit().putInt("graph_max_fps", v).apply()

    var bgAlpha: Int
        get() = sp().getInt("bg_alpha", 175)
        set(v) = sp().edit().putInt("bg_alpha", v.coerceIn(0, 255)).apply()

    var textSizeSp: Float
        get() = sp().getFloat("text_size_sp", 10.5f)
        set(v) = sp().edit().putFloat("text_size_sp", v.coerceIn(8f, 16f)).apply()

    var updateIntervalMs: Long
        get() = sp().getLong("update_interval_ms", 500L)
        set(v) = sp().edit().putLong("update_interval_ms", v).apply()

    var posX: Int
        get() = sp().getInt("pos_x", 24)
        set(v) = sp().edit().putInt("pos_x", v).apply()

    var posY: Int
        get() = sp().getInt("pos_y", 48)
        set(v) = sp().edit().putInt("pos_y", v).apply()

    var showQuickButtons: Boolean
        get() = sp().getBoolean("show_quick_buttons", false)
        set(v) = sp().edit().putBoolean("show_quick_buttons", v).apply()

    var lastCrashLog: String
        get() = sp().getString("last_crash_log", "") ?: ""
        set(v) = sp().edit().putString("last_crash_log", v).apply()

    fun saveCrashLogSync(log: String) {
        if (appCtx == null) return
        sp().edit().putString("last_crash_log", log).commit()
    }

    var benchmarkHistoryRaw: String
        get() = sp().getString("benchmark_history_raw", "") ?: ""
        set(v) = sp().edit().putString("benchmark_history_raw", v).apply()
}
