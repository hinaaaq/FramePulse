# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 2.5.3 (`versionCode = 6`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Device Tested:** Samsung Galaxy A24 (`SM-A245F`, Android 16 / API 36, 90Hz, Helio G99).

## Fixed in v2.5.3
- **True Game FPS Engine Overhaul (`SystemMonitor.kt`):**
  - Detects the active foreground package via `dumpsys window displays` (`mCurrentFocus`/`mFocusedApp`) and `dumpsys activity activities`.
  - Probes up to 8 prioritized candidate SurfaceFlinger layers (foreground `SurfaceView`+`BLAST` first) instead of blindly checking only `lastOrNull`, eliminating false fallback to 90Hz Choreographer and preventing floating bubble overlays from stealing the layer lock.
  - Switched live FPS math to a deduplicated 1-second rolling window over SurfaceFlinger timestamps for smooth, exact FPS matching in-game counters (e.g., 60 FPS cap on 90Hz displays).
- **HUD Source Clarity (`OverlayService.kt`):** Shows `~` prefix (`~89FPS`) whenever falling back to Choreographer screen refresh rate, and clean `60FPS` when locked onto True Game SurfaceFlinger frames.
- **SurfaceFlinger Diagnostics (`MainActivity.kt`):** Added one-tap "Salin Info SurfaceFlinger (Debug FPS)" button in Setup tab.

## Next Roadmap (Phase 3)
- Per-game auto-HUD trigger (detect foreground package via Shizuku and auto-start/stop HUD).
- Export benchmark session reports (CSV/shareable summary card).
- Frame-time jitter graph option alongside FPS sparkline.
