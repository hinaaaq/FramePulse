# Security & Verification Policy

FramePulse dirancang sebagai utilitas telemetri sistem Android yang transparan dan aman digunakan tanpa akses root.

## Verifikasi File APK
1. Unduh APK hanya dari halaman resmi [GitHub Releases](https://github.com/hinaaaq/FramePulse/releases).
2. Cocokkan hash **SHA-256** APK dengan file `SHA256SUMS.txt` yang dilampirkan di setiap rilis.
3. Setiap rilis mencantumkan tautan pengecekan **VirusTotal** berdasarkan SHA-256 hasil build resmi GitHub Actions.

## Prinsip Keamanan Aplikasi
- **Tanpa Root:** Tidak meminta akses `su` atau memodifikasi partisi sistem.
- **Aman untuk Sistem:** Tidak menjalankan `am kill-all` atau skrip pembersih RAM agresif; seluruh perintah Shizuku menggunakan sanitasi ketat dan *timeout watchdog*.
- **Trafik Terenkripsi:** `usesCleartextTraffic="false"` aktif secara eksplisit; seluruh komunikasi API dan pembaruan rilis tervalidasi lewat HTTPS.
- **Lisensi Kriptografis:** Aktivasi Supporter Pass diverifikasi menggunakan tanda tangan digital **ECDSA P-256** (kunci privat disimpan aman di Cloudflare Worker Secrets, tidak pernah ada di repositori).
- **Anti-Cheat Leaderboard:** Skor benchmark divalidasi di sisi klien dan server dari hasil rekaman asli (*Session Benchmark Recorder* minimal 15 detik).

## Melaporkan Celah Keamanan
Mohon **jangan** membuka Issue publik untuk celah keamanan sensitif. Gunakan fitur [GitHub Private Vulnerability Reporting](https://github.com/hinaaaq/FramePulse/security/advisories/new) agar temuan dapat ditangani secara bertanggung jawab sebelum dipublikasikan.
