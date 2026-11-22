# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 3.0 (`versionCode = 9`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Device Tested:** Samsung Galaxy A24 (`SM-A245F`, Android 16 / API 36, 90Hz, Helio G99).

## Completed in v3.0 (Complete Visual, Icon, Font, Overlay & Notification Overhaul)
1. **Icons & Theme:**
   - Redesigned adaptive launcher icon with radial gradient cyber-obsidian background, neon cyan-to-emerald hexagonal reticle + ECG pulse foreground, and Android 13–16 `<monochrome>` themed icon support.
   - Redesigned 24dp crisp corner-frame pulse vector (`ic_tile_pulse.xml`) for status bar notification and Quick Settings Tile.
   - Added custom `Theme.FramePulse` (`res/values/themes.xml`) + `enableEdgeToEdge(SystemBarStyle.dark)` to eliminate grey splash flash and enforce OLED dark system bars.
2. **Typography & Fonts:**
   - Added custom `FramePulseTypography` with tabular numerals (`fontFeatureSettings = "tnum"`) across all live telemetry counters so numbers never jitter horizontally.
   - Added 4 selectable HUD fonts (`Sport` condensed bold, `Mono` bold, `Clean` sans bold, `Tech` mono) applied both in real overlay and 1:1 HUD Live Preview.
3. **Overlay & Sparkline Redesign (`OverlayService.kt`, `HudSparklineView.kt`):**
   - 4 polished HUD styles: `Glass` (gradient card + glowing accent edge bar), `Pill` (sleek capsule + live SF/REC status dot), `Pro Deck` (structured header + telemetry card), and `Minimal` (pure shadowed floating text).
   - Label/value typographic contrast (muted micro-labels + bold colored values + clean dot separators) + 5 themes (`Cyber`, `Neon`, `Fury`, `Volt Amber`, `Hyper Violet`).
   - Upgraded both HUD and in-app sparklines to smooth cubic Bezier curves with outer neon glow halo and zero per-frame allocations.
4. **Live Dynamic Notification (`OverlayService.kt`):**
   - Updates silently every 2s with real-time FPS, SF/STD source, CPU%, GPU%, temp, and lock status, accented with active theme color and 3 quick actions (`Lock/Unlock`, `Boost`, `Stop`).
5. **Main App UI Overhaul (`MainActivity.kt`, `UiComponents.kt`):**
   - Custom vector `FramePulseLogoMark`, dual-tone active/inactive vector nav icons, mini vector card icons, color-swatch theme chips, 1-tap Sensor Presets (`Esports`, `Benchmark`, `Full Pro`), configurable Thermal Guard chips (`41°C/43°C/45°C`), and tactical grid 1:1 HUD preview.
   - Replaced `seq` shell command in `SystemMonitor.kt` `readAllCores()` with pure POSIX loop for universal Android compatibility.
