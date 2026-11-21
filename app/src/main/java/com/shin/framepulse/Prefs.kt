package com.shin.framepulse

import android.content.Context

object Prefs {
    private const val PREF_NAME = "framepulse_prefs"

    const val LAYOUT_HORIZONTAL = "HORIZONTAL"
    const val LAYOUT_VERTICAL = "VERTICAL"
    const val LAYOUT_MINI = "MINI"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var layoutMode: String
        get() = sp(appCtx).getString("layout_mode", LAYOUT_HORIZONTAL) ?: LAYOUT_HORIZONTAL
        set(v) = sp(appCtx).edit().putString("layout_mode", v).apply()

    var showFps: Boolean
        get() = sp(appCtx).getBoolean("show_fps", true)
        set(v) = sp(appCtx).edit().putBoolean("show_fps", v).apply()

    var showCpu: Boolean
        get() = sp(appCtx).getBoolean("show_cpu", true)
        set(v) = sp(appCtx).edit().putBoolean("show_cpu", v).apply()

    var showClock: Boolean
        get() = sp(appCtx).getBoolean("show_clock", false)
        set(v) = sp(appCtx).edit().putBoolean("show_clock", v).apply()

    var showGpu: Boolean
        get() = sp(appCtx).getBoolean("show_gpu", true)
        set(v) = sp(appCtx).edit().putBoolean("show_gpu", v).apply()

    var showRam: Boolean
        get() = sp(appCtx).getBoolean("show_ram", false)
        set(v) = sp(appCtx).edit().putBoolean("show_ram", v).apply()

    var showTemp: Boolean
        get() = sp(appCtx).getBoolean("show_temp", true)
        set(v) = sp(appCtx).edit().putBoolean("show_temp", v).apply()

    var showWatt: Boolean
        get() = sp(appCtx).getBoolean("show_watt", false)
        set(v) = sp(appCtx).edit().putBoolean("show_watt", v).apply()

    var bgAlpha: Int
        get() = sp(appCtx).getInt("bg_alpha", 170)
        set(v) = sp(appCtx).edit().putInt("bg_alpha", v.coerceIn(0, 255)).apply()

    var textSizeSp: Float
        get() = sp(appCtx).getFloat("text_size_sp", 11f)
        set(v) = sp(appCtx).edit().putFloat("text_size_sp", v.coerceIn(8f, 16f)).apply()

    var updateIntervalMs: Long
        get() = sp(appCtx).getLong("update_interval_ms", 500L)
        set(v) = sp(appCtx).edit().putLong("update_interval_ms", v).apply()

    var posX: Int
        get() = sp(appCtx).getInt("pos_x", 24)
        set(v) = sp(appCtx).edit().putInt("pos_x", v).apply()

    var posY: Int
        get() = sp(appCtx).getInt("pos_y", 48)
        set(v) = sp(appCtx).edit().putInt("pos_y", v).apply()

    var showQuickButtons: Boolean
        get() = sp(appCtx).getBoolean("show_quick_buttons", false)
        set(v) = sp(appCtx).edit().putBoolean("show_quick_buttons", v).apply()

    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
    }
}
