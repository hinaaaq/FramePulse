# Changelog

## [1.2.0] - 2026-11-24

### Added
- **Shizuku Pro Tweaks (Non-Root):** Smart Standby Bucket (`restricted` untuk app latar belakang berat), Refresh Rate Lock (`Auto / 60Hz / 90Hz`), Gaming Visual Mode (Blur OFF & Animasi `0.5x`), Deep ART Compile (`speed`) + Trim Cache khusus game, serta tombol **1-Tap Reset Tweaks** & **Smart Shizuku Redirect**.
- **HUD Thermal Warning:** Peringatan otomatis di layar HUD saat suhu baterai menyentuh `>= 42.0°C`.
- **Quick Collapse HUD:** Ketuk dua kali (*Double-Tap*) pada *floating HUD* untuk beralih instan ke mode Mini dengan *haptic feedback*.
- **Auto-Show HUD Khusus Game:** Otomatis sembunyi di *Home screen* / *Launcher* dan muncul kembali saat masuk ke game.
- **Benchmark Pro & Export:** Metrik Frametime (`ms`), Stutter Counter, Stabilitas (`%`), **Export CSV** ke `Download/FramePulse/`, dan **Simpan Card PNG** (1080×640) ke Galeri.
- **First-Launch Language Picker & What's New Popup:** Pilihan bahasa (English-first) saat pertama kali install, dan popup *"What's New"* otomatis khusus saat update dari versi lama (bisa dibuka ulang dari footer versi di tab Setup).
- **Pro Update Checker:** Popup update dengan *Markdown cleaner*, ukuran APK, tanggal rilis, opsi **Lewati Versi Ini**, dan tombol cek manual.

### Fixed & Improved
- Lokalisasi 100% penuh (EN / ID) termasuk seluruh tombol baru, dialog update, *toast*, dan overlay.
- Audit memori & stabilitas: menghapus duplikat `Handler` di `OverlayService.kt` dan mengamankan potensi NPE pada `QrisPaymentDialog.kt`.
- Pembaruan `README.md` agar mencerminkan fitur inti 100% gratis & dukungan Android 16.


## v1.0.1 — Maintenance & Experience Update
- **In-App Update Checker:** Aplikasi otomatis mengecek rilis stabil terbaru di GitHub Releases dan menampilkan banner update 1-tap (install langsung tanpa uninstall).
- **Benchmark Recorder untuk Semua User:** Fitur rekam sesi benchmark (Avg FPS, 1% Low, Peak °C) kini terbuka gratis agar semua pengguna bisa ikut Verified Global Leaderboard dan membuat Kartu Pamer (Flex Card).
- **HUD Quick Record:** Tombol `REC` di floating HUD kini aktif untuk semua tier.

## v1.0.0 — Stable Release
- **Capability Engine:** Tampilan transparan status kemampuan perangkat Tier 1 (Standard) & Tier 2 (Shizuku).
- **SurfaceFlinger & Thermal Telemetry:** Pemantauan FPS, Frame Time, CPU/GPU, RAM, suhu baterai, daya (Watt), dan jaringan secara real-time.
- **HUD Studio:** Kustomisasi gaya HUD, transparansi, ukuran font, posisi preset, *Bezier Sparkline*, dan *Touch-Through Lock*.
- **Performance Engine (Shizuku):** Android Game Mode, Preset Game per-paket, Power HAL Fixed Performance dengan Auto-Thermal Guard, dan ART `speed-profile` compile (aman untuk One UI tanpa `am kill-all`).
- **Verified Leaderboard & Flex Card:** Anti-cheat benchmark-locked (minimal rekaman 15 detik) + ekspor Kartu Pamer PNG.
- **Signed Release Pipeline:** Rilis resmi bertanda tangan permanen untuk dukungan *in-place upgrade* tanpa uninstall.
