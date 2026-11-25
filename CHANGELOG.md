# Changelog

## [1.2.2] - 2026-11-25

### Security & Stability
- **Unified Safe `ShizukuShell` Engine:** Sanitasi ketat nama paket, pemeriksaan izin Shizuku penuh (`pingBinder` + `checkSelfPermission`), penggabungan stream `stdout`/`stderr` bebas *pipe deadlock*, serta *timeout watchdog* otomatis.
- **Android 14–16 Overlay & Service Hardening:** Perbaikan *early-exit* Foreground Service, proteksi *multi-instance lifecycle race*, penghapusan `am kill-all` pada Quick Boost, serta pemindahan seluruh operasi ekspor CSV/PNG & MediaStore ke `Dispatchers.IO`.
- **Cloudflare Worker Security:** Penutupan celah autentikasi *fail-open* pada webhook donasi/pembayaran, deduplikasi *replay* transaksi, serta verifikasi bukti kriptografis *Leaderboard* & status Supporter langsung di sisi server.
- **Null-Safety & Multi-Process Crash Guard:** Penghapusan *non-null assertion* (`!!`) pada `QrisPaymentDialog`, penyimpanan log crash berbasis file yang aman lintas proses, serta aturan `usesCleartextTraffic="false"` & *backup exclusion*.

### Telemetry & UX Improvements
- **Live 1% Low FPS & Hardware Thermal Throttling:** Perhitungan *rolling* 1% Low FPS langsung dari `SurfaceFlinger` dan pembacaan status termal perangkat keras via `PowerManager` di Android 10–16.
- **Accurate Game-Only Auto-Show HUD:** Deteksi aplikasi *foreground* yang presisi di One UI / Android 16 (`ApplicationInfo.CATEGORY_GAME` + aktif `SurfaceView`) sehingga HUD langsung bersembunyi saat kembali ke *Home Screen*.
- **Full EN/ID Localization & Synced Setup Layout:** Lokalisasi penuh Bahasa Inggris & Indonesia di seluruh layar (*Welcome Onboarding*, *QRIS*, *Leaderboard*, *Presets*, *Theme Picker*, *Crash Guard*), perapihan struktur kartu tab Setup, serta *live global typography* & perbandingan delta sesi benchmark (`Δ FPS` / `Δ Temp`).

## [1.2.0] - 2026-11-24

### Added
- **Shizuku Pro Tweaks (Non-Root):** Smart Standby Bucket (`restricted` untuk app latar belakang berat), Refresh Rate Lock (`Auto / 60Hz / 90Hz`), Gaming Visual Mode (Blur OFF & Animasi `0.5x`), Deep ART Compile (`speed`) + Trim Cache khusus game, serta tombol **1-Tap Reset Tweaks** & **Smart Shizuku Redirect**.
- **HUD Thermal Warning:** Peringatan otomatis di layar HUD saat suhu baterai menyentuh `>= 42.0°C`.
- **Quick Collapse HUD:** Ketuk dua kali (*Double-Tap*) pada *floating HUD* untuk beralih instan ke mode Mini dengan *haptic feedback*.
- **Auto-Show HUD Khusus Game:** Otomatis sembunyi di *Home screen* / *Launcher* dan muncul kembali saat masuk ke game.
- **Benchmark Pro & Export:** Metrik Frametime (`ms`), Stutter Counter, Stabilitas (`%`), **Export CSV** ke `Download/FramePulse/`, dan **Simpan Card PNG** (1080×640) ke Galeri.
- **First-Launch Language Picker & What's New Popup:** Pilihan bahasa (English-first) saat pertama kali install, dan popup *"What's New"* otomatis khusus saat update dari versi lama (bisa dibuka ulang dari footer versi di tab Setup).
- **Pro Update Checker:** Popup update dengan *Markdown cleaner*, ukuran APK, tanggal rilis, opsi **Lewati Versi Ini**, dan tombol cek manual.

## [1.1.0] — Session Insights & HUD Presets
- **Benchmark History & Delta Comparison (`BenchmarkHistoryStore`):** Menyimpan hingga 20 sesi benchmark lokal dengan perhitungan selisih `Δ Avg FPS`, `Δ 1% Low`, dan `Δ Peak °C`.
- **HUD Density Modes (`HudPresetManager`):** Dukungan mode `COMPACT` dan `DETAILED` serta penyimpanan profil per game.

## [1.0.1] — Maintenance & Experience Update
- **In-App Update Checker:** Pengecekan otomatis rilis stabil terbaru di GitHub Releases.
- **Benchmark Recorder untuk Semua User:** Fitur rekam sesi benchmark terbuka gratis untuk semua pengguna.
- **HUD Quick Record:** Tombol `REC` cepat langsung di floating HUD.

## [1.0.0] — Stable Release
- **Capability Engine:** Tampilan transparan status kemampuan perangkat Tier 1 (Standard) & Tier 2 (Shizuku).
- **SurfaceFlinger & Thermal Telemetry:** Pemantauan FPS, Frame Time, CPU/GPU, RAM, suhu baterai, daya (Watt), dan jaringan secara real-time.
- **HUD Studio & Performance Engine:** Kustomisasi gaya HUD, *Bezier Sparkline*, *Touch-Through Lock*, Android Game Mode, dan Power HAL Fixed Performance dengan Auto-Thermal Guard.
