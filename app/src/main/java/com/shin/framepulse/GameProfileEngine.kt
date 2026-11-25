package com.shin.framepulse

data class GamePreset(
    val id: String,
    val name: String,
    val packages: List<String>,
    val downscale: String,
    val targetFps: Int,
    val gameMode: String,
    val summaryId: String,
    val summaryEn: String
) {
    val summary: String
        get() = if (AppStrings.resolveLangCode() == "id") summaryId else summaryEn
}

object GameProfileEngine {
    val presets = listOf(
        GamePreset(
            id = "valm",
            name = "Valorant Mobile",
            packages = listOf("com.tencent.tmgp.codev", "com.riotgames.valorant.mobile"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summaryId = "Mode Perf • Scale 85% • Anti-stutter saat peek/smoke",
            summaryEn = "Perf Mode • 85% Scale • Anti-stutter on peek/smoke"
        ),
        GamePreset(
            id = "mlbb",
            name = "Mobile Legends (MLBB)",
            packages = listOf("com.mobile.legends"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summaryId = "Mode Perf • Scale 85% • Stabil saat war 5v5",
            summaryEn = "Perf Mode • 85% Scale • Stable in 5v5 teamfights"
        ),
        GamePreset(
            id = "ff",
            name = "Free Fire / FF Max",
            packages = listOf("com.dts.freefireth", "com.dts.freefiremax"),
            downscale = "0.90",
            targetFps = 90,
            gameMode = "performance",
            summaryId = "Mode Perf • Scale 90% • Tajam jarak jauh & low latency",
            summaryEn = "Perf Mode • 90% Scale • Crisp long-range & low latency"
        ),
        GamePreset(
            id = "pubg",
            name = "PUBG Mobile",
            packages = listOf("com.tencent.ig", "com.pubg.imobile"),
            downscale = "0.80",
            targetFps = 60,
            gameMode = "performance",
            summaryId = "Mode Perf • Scale 80% • Adem buat match 30 menit",
            summaryEn = "Perf Mode • 80% Scale • Cooler for 30-min matches"
        ),
        GamePreset(
            id = "hok",
            name = "Honor of Kings (HOK)",
            packages = listOf("com.levelinfinite.sgameGlobal"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summaryId = "Mode Perf • Scale 85% • 90 FPS smooth teamfight",
            summaryEn = "Perf Mode • 85% Scale • Smooth 90 FPS teamfights"
        ),
        GamePreset(
            id = "genshin",
            name = "Genshin Impact",
            packages = listOf("com.miHoYo.GenshinImpact"),
            downscale = "0.75",
            targetFps = 60,
            gameMode = "battery",
            summaryId = "Thermal Saver • Scale 75% • Cegah overheat & throttling",
            summaryEn = "Thermal Saver • 75% Scale • Prevent overheat & throttling"
        )
    )

    val allKnownGamePackages: Set<String> = presets.flatMap { it.packages }.toSet()

    fun buildApplyScript(preset: GamePreset): String {
        val modeNum = if (preset.gameMode == "battery") 3 else 2
        val modeName = if (preset.gameMode == "battery") "battery" else "performance"
        val scale = preset.downscale.filter { it.isDigit() || it == '.' }.ifBlank { "0.85" }
        val fps = preset.targetFps.coerceIn(30, 144)

        return buildString {
            // Use 0.5x instead of 0x so ValueAnimator-dependent UIs/games never freeze
            append("ok=0; ")
            append("settings put global window_animation_scale 0.5 >/dev/null 2>&1 && ok=$((ok+1)); ")
            append("settings put global transition_animation_scale 0.5 >/dev/null 2>&1; ")
            append("settings put global animator_duration_scale 0.5 >/dev/null 2>&1; ")
            for (rawPkg in preset.packages) {
                val pkg = ShizukuShell.sanitizePackage(rawPkg)
                if (pkg.isEmpty()) continue
                append("if pm path '$pkg' >/dev/null 2>&1; then ")
                append("device_config put game_overlay '$pkg' 'mode=2,downscaleFactor=$scale,fps=$fps:mode=3,downscaleFactor=0.75,fps=60' >/dev/null 2>&1; ")
                append("cmd game mode $modeName '$pkg' >/dev/null 2>&1 && ok=$((ok+1)); ")
                append("cmd game set --mode $modeNum --downscale $scale --fps $fps '$pkg' >/dev/null 2>&1 && ok=$((ok+1)); ")
                append("fi; ")
            }
            append("echo \"\$ok\"")
        }
    }

    fun buildResetScript(preset: GamePreset): String {
        return buildString {
            append("settings put global window_animation_scale 1.0 >/dev/null 2>&1; ")
            append("settings put global transition_animation_scale 1.0 >/dev/null 2>&1; ")
            append("settings put global animator_duration_scale 1.0 >/dev/null 2>&1; ")
            for (rawPkg in preset.packages) {
                val pkg = ShizukuShell.sanitizePackage(rawPkg)
                if (pkg.isEmpty()) continue
                append("if pm path '$pkg' >/dev/null 2>&1; then ")
                append("device_config delete game_overlay '$pkg' >/dev/null 2>&1; ")
                append("cmd game mode standard '$pkg' >/dev/null 2>&1; ")
                append("cmd game set --mode 1 --downscale disable '$pkg' >/dev/null 2>&1; ")
                append("fi; ")
            }
            append("true")
        }
    }
}
