# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 2.5.4 (`versionCode = 7`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Device Tested:** Samsung Galaxy A24 (`SM-A245F`, Android 16 / API 36, 90Hz, Helio G99).

## Deep Audit Fixes in v2.5.4 (Cumulative with v2.5.3)
1. **Zero-Deadlock Shell (`SystemMonitor.kt`):** Uses `printf '\n__FP_END__\n'` so sysfs nodes without trailing newlines never freeze the sensor worker thread.
2. **ANR-Free Shutdown (`SystemMonitor.kt`):** Destroys `@Volatile shellProcess` outside `shellLock` before cleanup so stopping HUD never blocks the Main UI thread.
3. **Stutter-Free & Fresh SurfaceFlinger Locking (`SystemMonitor.kt`):**
   - Verifies timestamp freshness against `System.nanoTime()` (<= 1.5s) so dead lobby/video `SurfaceView` layers are never locked.
   - Replaces heavy 3-second full re-scans with a lightweight 4-second foreground-package check while frames are actively flowing.
4. **Race-Free Diagnostics (`MainActivity.kt`, `TelemetryHub`):** Shares `activeLockedLayer` / `activeForegroundPkg` via `TelemetryHub` and runs diagnostic dumps in an isolated `SystemMonitor` instance.
5. **Gaming Overlay Ergonomics (`OverlayService.kt`, `Prefs.kt`):**
   - Separate Portrait vs Landscape saved HUD coordinates so exiting a horizontal game never resets your gaming HUD spot.
   - Added `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` for fullscreen punch-hole cutout positioning.
   - Instant `[REC]` HUD refresh, Quick Settings Tile auto-sync (`requestListeningState`), and `~` fallback FPS indicator.
6. **Sensor & Rendering Polish (`SystemMonitor.kt`, `HudSparklineView.kt`, `FramePulseApp.kt`, `UiComponents.kt`, `CrashActivity.kt`):**
   - Fixed first-sample `/proc/stat` since-boot CPU spike, `0.0W` reset when `0mA`, microvolt normalization, and charging (`+`/`-`) indicator.
   - Zero-allocation `Path` reuse and cached `LinearGradient` in `HudSparklineView`.
   - Dynamic `versionName` in crash reports, Android 15/16 `statusBarsPadding()`/`navigationBarsPadding()`, and Minimal-mode text shadow in HUD Live Preview.

## Next Roadmap (Phase 3)
- Per-game auto-HUD trigger (detect foreground package via Shizuku and auto-start/stop HUD).
- Export benchmark session reports (CSV/shareable summary card).
- Frame-time jitter graph option alongside FPS sparkline.
