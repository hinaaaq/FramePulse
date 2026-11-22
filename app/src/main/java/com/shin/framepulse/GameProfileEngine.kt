package com.shin.framepulse

data class GamePreset(
    val id: String,
    val name: String,
    val packages: List<String>,
    val downscale: String,
    val targetFps: Int,
    val gameMode: String,
    val summary: String
)

object GameProfileEngine {
    val presets = listOf(
        GamePreset(
            id = "valm",
            name = "Valorant Mobile",
            packages = listOf("com.tencent.tmgp.codev", "com.riotgames.valorant.mobile"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summary = "Mode Perf • Scale 85% • Anti-stutter saat peek/smoke"
        ),
        GamePreset(
            id = "mlbb",
            name = "Mobile Legends (MLBB)",
            packages = listOf("com.mobile.legends"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summary = "Mode Perf • Scale 85% • Stabil saat war 5v5"
        ),
        GamePreset(
            id = "ff",
            name = "Free Fire / FF Max",
            packages = listOf("com.dts.freefireth", "com.dts.freefiremax"),
            downscale = "0.90",
            targetFps = 90,
            gameMode = "performance",
            summary = "Mode Perf • Scale 90% • Tajam jarak jauh & low latency"
        ),
        GamePreset(
            id = "pubg",
            name = "PUBG Mobile",
            packages = listOf("com.tencent.ig", "com.pubg.imobile"),
            downscale = "0.80",
            targetFps = 60,
            gameMode = "performance",
            summary = "Mode Perf • Scale 80% • Adem buat match 30 menit"
        ),
        GamePreset(
            id = "hok",
            name = "Honor of Kings (HOK)",
            packages = listOf("com.levelinfinite.sgameGlobal"),
            downscale = "0.85",
            targetFps = 90,
            gameMode = "performance",
            summary = "Mode Perf • Scale 85% • 90 FPS smooth teamfight"
        ),
        GamePreset(
            id = "genshin",
            name = "Genshin Impact",
            packages = listOf("com.miHoYo.GenshinImpact"),
            downscale = "0.75",
            targetFps = 60,
            gameMode = "battery",
            summary = "Thermal Saver • Scale 75% • Cegah overheat & throttling"
        )
    )

    fun buildApplyCommands(preset: GamePreset): List<String> {
        val cmds = mutableListOf(
            "cmd activity kill-all",
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0"
        )
        for (pkg in preset.packages) {
            cmds.add("cmd game mode ${preset.gameMode} $pkg")
            cmds.add("cmd game set --mode 2 --downscale ${preset.downscale} --fps ${preset.targetFps} $pkg")
        }
        return cmds
    }

    fun buildResetCommands(preset: GamePreset): List<String> {
        val cmds = mutableListOf(
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1"
        )
        for (pkg in preset.packages) {
            cmds.add("cmd game mode standard $pkg")
            cmds.add("cmd game set --mode 1 --downscale disable $pkg")
        }
        return cmds
    }
}
