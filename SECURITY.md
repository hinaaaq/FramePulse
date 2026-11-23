# Security & Verification Policy

FramePulse dirancang sebagai utilitas telemetri sistem yang transparan dan aman digunakan tanpa akses root.

## Verifikasi File APK
1. Unduh APK hanya dari halaman resmi [GitHub Releases](https://github.com/hinaaaq/FramePulse/releases).
2. Cocokkan hash **SHA-256** APK dengan file `SHA256SUMS.txt` yang dilampirkan di setiap rilis.
3. Setiap rilis mencantumkan tautan pengecekan **VirusTotal** berdasarkan SHA-256 build resmi GitHub Actions.

## Prinsip Keamanan Aplikasi
- **Tanpa Root:** Tidak meminta akses `su` atau memodifikasi partisi sistem.
- **Aman untuk Sistem:** Tidak menjalankan `am kill-all` atau skrip pembersih RAM agresif.
- **Lisensi Kriptografis:** Aktivasi Pro diverifikasi menggunakan tanda tangan digital **ECDSA P-256** (kunci privat disimpan aman di Cloudflare Worker Secrets, tidak pernah ada di repositori).
- **Anti-Cheat Leaderboard:** Skor benchmark dikirim bersama validasi integritas dari hasil rekaman asli (*Benchmark Recorder*), bukan input manual.

## Melaporkan Celah Keamanan
Jika menemukan bug keamanan atau celah pada aplikasi/backend, silakan buka Issue atau hubungi developer melalui GitHub [@hinaaaq](https://github.com/hinaaaq).
