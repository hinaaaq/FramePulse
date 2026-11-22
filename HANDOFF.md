# FramePulse Pro — Project Handoff

## Current Status
- **Version:** 2.6 (`versionCode = 8`)
- **Package:** `com.shin.framepulse`
- **Stack:** Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material 3), AGP 8.5.2, compileSdk 35, targetSdk 34, minSdk 26, Shizuku 13.1.5.
- **Device Tested:** Samsung Galaxy A24 (`SM-A245F`, Android 16 / API 36, 90Hz, Helio G99).

## Completed in v2.6 (Deep Bug Audit + Shizuku Game Turbo Engine)
1. **Critical Bug Fixes:**
   - Fixed Landscape HUD drag clamping on Android 12–16 (`OverlayService.kt` now uses `windowManager.currentWindowMetrics.bounds` on API 30+ instead of stale `resources.displayMetrics`).
   - Fixed floating bubble / IME stealing foreground package detection (`SystemMonitor.kt` now prioritizes `mFocusedApp` and `topResumedActivity` over `mCurrentFocus`).
   - Fixed dual-clock (`CLOCK_MONOTONIC` + `CLOCK_BOOTTIME`) freshness check for SurfaceFlinger timestamps across vendor kernels.
   - Fixed permanent GPU `N/A` if first shell scan fails transiently (now retries up to 3 times) and added batched Shizuku fallback for `/sys/devices/system/cpu` core frequencies.
   - Cut in-game monitoring overhead by >60% (no periodic `system_server` dumpsys every 4s while locked game SurfaceView is actively rendering; re-checks every 12s or on silence).
   - Fixed notification tap (`setContentIntent`), preset top-Y punch-hole clearance, and `~` fallback prefix when all sensors are toggled off.
2. **Game Turbo Engine (`GameBooster.kt`):**
   - Safe RAM & cache trim (`am kill-all` + `pm trim-caches 512M`) with real before/after freed MB reporting.
   - Official Android GameManager Performance Mode (`cmd game mode performance <pkg>`), auto-applied on active games when Auto-Boost is enabled.
   - Power HAL Fixed Performance Mode (`cmd power set-fixed-performance-mode-enabled true`) with **Auto-Thermal Guard (>= 43°C)** and automatic reset when HUD stops.
   - One-tap ART `speed-profile` compilation for the detected game + 1-tap Ultra-Light HUD gaming preset + quick `[BST]` button directly on the floating overlay.

## Next Roadmap (Phase 3)
- Per-game whitelist / auto-HUD start-stop trigger.
- Export benchmark session reports (CSV / shareable summary card).
- Frame-time jitter graph toggle alongside FPS sparkline.
