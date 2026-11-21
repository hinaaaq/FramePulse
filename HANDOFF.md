# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 2.5 (`versionCode = 3`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Build:** GitHub Actions (`.github/workflows/build.yml`).

## Completed in v2.5 (Phase 1 + Phase 2)
1. **Manifest & Startup Stability:**
   - Removed duplicate manual `ShizukuProvider` declaration (now injected cleanly by `dev.rikka.shizuku:provider:13.1.5`).
   - Added `FramePulseApp` + `CrashActivity` (`:crash` process) for automatic crash capture, copyable stacktrace, and diagnostics view in Setup tab.
   - Fixed Android 14 `FramePulseTileService` `startActivityAndCollapse(PendingIntent)` crash.
2. **Core Engine & Shizuku Bridge (`SystemMonitor.kt`):**
   - Safe Shizuku permission/binder checks (removed 500ms `requestBinderForNonProviderProcess` spam).
   - Thread-safe shell execution with `2>/dev/null` stderr suppression to prevent buffer deadlocks.
   - Single-quote escaping for SurfaceFlinger layer names containing `$`.
   - Improved active game layer filtering + active surface label readout.
   - Shizuku SELinux bypass for `/proc/stat` CPU% and GPU sysfs utilization, plus frequency-based CPU% fallback.
   - Dynamic CPU core detection (no longer hardcoded to 8 cores / `i >= 6`) and auto display refresh rate detection via `DisplayManager`.
   - True 1% Low FPS calculated from per-frame SurfaceFlinger intervals + persistent benchmark session history (last 8 runs).
3. **UI & HUD Studio Overhaul:**
   - Custom vector Canvas navigation icons (replaced emojis).
   - True 1:1 Interactive Live HUD Preview reflecting style, layout, theme, opacity, text size, all sensors, and sparkline.
   - Added HUD styles (`Glass Card`, `Esports Pill`, `Minimal`), 4th theme (`Volt Amber`), Snap-to-Edge dragging, Battery % sensor, and quick `[MODE]` switcher directly on the floating overlay.

## Next Roadmap (Phase 3)
- Per-game auto-HUD trigger (detect foreground package via Shizuku and auto-start/stop HUD).
- Export benchmark session reports (CSV/shareable summary card).
- Frame-time jitter graph option alongside FPS sparkline.
