package com.shin.framepulse

import android.content.Context

object Prefs {
    private const val PREF_NAME = "framepulse_pro_prefs"

    const val LAYOUT_HORIZONTAL = "HORIZONTAL"
    const val LAYOUT_VERTICAL = "VERTICAL"
    const val LAYOUT_MINI = "MINI"

    const val THEME_CYBER = "CYBER"
    const val THEME_EMERALD = "EMERALD"
    const val THEME_CRIMSON = "CRIMSON"

    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    private fun sp() = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var layoutMode: String
        get() = sp().getString("layout_mode", LAYOUT_HORIZONTAL) ?: LAYOUT_HORIZONTAL
        set(v) = sp().edit().putString("layout_mode", v).apply()

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

    var showWatt: Boolean
        get() = sp().getBoolean("show_watt", false)
        set(v) = sp().edit().putBoolean("show_watt", v).apply()

    var showNet: Boolean
        get() = sp().getBoolean("show_net", false)
        set(v) = sp().edit().putBoolean("show_net", v).apply()

    var showHudGraph: Boolean
        get() = sp().getBoolean("show_hud_graph", true)
        set(v) = sp().edit().putBoolean("show_hud_graph", v).apply()

    var bgAlpha: Int
        get() = sp().getInt("bg_alpha", 165)
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
        get() = sp().getInt("pos_y", 42)
        set(v) = sp().edit().putInt("pos_y", v).apply()

    var showQuickButtons: Boolean
        get() = sp().getBoolean("show_quick_buttons", false)
        set(v) = sp().edit().putBoolean("show_quick_buttons", v).apply()
}
