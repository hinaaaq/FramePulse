# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 2.5.1 (`versionCode = 5`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Build:** GitHub Actions (`.github/workflows/build.yml`).

## Fixed in v2.5.2
- Fixed Shizuku Binder IPC crash (`IllegalArgumentException: process hasn't exited` in `SystemMonitor.ensureShell()`).
- Restored `ShizukuProvider` in `AndroidManifest.xml` so Shizuku binder connects properly (`Binder: OK`).
- Fixed BIG/EFF core threshold (`0.95`) for Helio G99 (2.2GHz vs 2.0GHz) and added defensive `collect()` guards.
- Fixed GPU sysfs scan in `SystemMonitor.kt` (removed `exit 0` that could terminate persistent Shizuku shell, added MediaTek/Mali sysfs paths).
- Improved SurfaceFlinger layer filtering for One UI (prioritizes `SurfaceView` over generic `BLAST`, excludes `cocktailbarservice`, `gametools`, `systemui`, `launcher`, floating overlays).
- Fixed SurfaceFlinger FPS under-reporting after idle pauses or 127-frame ring-buffer wraparound.
- Fixed Android 12/13/14+ touch-through blocking in `OverlayService.kt` by setting window `alpha = 0.78f` when HUD touch lock is active.
- Fixed `Int.MIN_VALUE` overflow guard in battery current readout and benchmark history auto-refresh when stopping recording from the floating overlay.

## Next Roadmap (Phase 3)
- Per-game auto-HUD trigger (detect foreground package via Shizuku and auto-start/stop HUD).
- Export benchmark session reports (CSV/shareable summary card).
- Frame-time jitter graph option alongside FPS sparkline.
