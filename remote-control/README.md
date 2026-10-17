# Remote Ctl — kontrol perangkat jarak jauh (versi 1, Android ke Android)

Proyek Android native terpisah (bukan lewat alur "Pembuat APK" biasa),
karena fitur lihat + kontrol layar jarak jauh butuh dua izin sistem
yang hanya bisa diakses lewat kode native: `MediaProjection` (rekam
layar) dan `AccessibilityService` (kirim ketukan/geser).

## Cara kerja singkat

- **Perangkat yang dikontrol** (buka app, pilih "Jadikan perangkat ini
  yang dikontrol"): mendapat kode sesi 6 digit, mengizinkan rekam
  layar, lalu wajib mengaktifkan Accessibility Service lewat
  Pengaturan (tombol disediakan di app).
- **Perangkat pengontrol** (pilih "Kontrol perangkat lain"): masukkan
  kode 6 digit, lalu layar perangkat lain tampil dan bisa disentuh
  untuk mengirim tap/swipe jarak jauh.
- Semua komunikasi lewat **Firebase Realtime Database** (bukan
  WebRTC), jadi tidak butuh server TURN/STUN. Layar dikirim sebagai
  cuplikan JPEG kecil (~480px lebar, kualitas rendah) beberapa kali
  per detik — bukan video real-time halus, tapi cukup untuk kontrol
  dasar di versi pertama ini.

## Yang WAJIB kamu siapkan sebelum build jalan

### 1. Daftarkan aplikasi baru di Firebase (proyek yang sudah ada: `apk-maker-ceb1f`)

Aplikasi ini punya package name baru (`com.byhasstudio.remotectl`),
beda dari aplikasi Pembuat APK, jadi perlu didaftarkan sebagai
"Android app" baru di proyek Firebase yang sama:

1. Buka [Firebase Console](https://console.firebase.google.com/) → proyek `apk-maker-ceb1f`.
2. Project settings → tab General → "Add app" → pilih Android.
3. Package name: `com.byhasstudio.remotectl`
4. Unduh file `google-services.json` yang diberikan.
5. **Aktifkan Realtime Database** di proyek ini (menu Build → Realtime Database → Create database), kalau belum aktif.
6. Di tab Rules Realtime Database, tempel isi file `database.rules.json` dari folder ini, lalu Publish.
7. Di Authentication → Sign-in method, aktifkan **Anonymous**.

### 2. Masukkan google-services.json ke GitHub sebagai secret

File itu tidak boleh langsung dikomit ke repo publik/private biasa
begitu saja kalau kamu ingin lebih aman — jadi dikirim lewat GitHub
Secret, di-decode saat build:

1. Di terminal/HP, ubah file jadi base64 (atau pakai situs base64 encoder — isi file, bukan path).
2. Buka GitHub repo → Settings → Secrets and variables → Actions → New repository secret.
3. Nama: `REMOTECTL_GOOGLE_SERVICES_JSON`
4. Value: hasil base64 dari file `google-services.json`.

### 3. Jalankan build

Buka tab **Actions** di GitHub repo → workflow **"Build Remote Control APK"** → Run workflow. Setelah selesai, unduh APK dari bagian **Artifacts** di halaman run tersebut (`remote-ctl-debug-apk`).

## Batasan versi pertama ini (sengaja disederhanakan)

- Hanya Android ↔ Android (belum ke komputer Windows/Mac/Linux — itu
  butuh program desktop terpisah, bukan APK).
- Layar dikirim sebagai cuplikan foto berkala, bukan video real-time
  mulus seperti AnyDesk asli.
- Kode sesi 6 digit = kunci bersama sederhana. Siapa pun yang tahu
  kodenya bisa tersambung selama sesi itu aktif — jangan bagikan kode
  ke sembarang orang. Pengamanan yang lebih kuat (sesi kedaluwarsa,
  pembatasan per akun, dsb.) bisa ditambahkan di versi berikutnya.
- Belum ada transfer file, clipboard, atau suara.

## Ide pengembangan berikutnya

- Kedaluwarsakan/hapus sesi otomatis setelah tidak dipakai.
- Dukungan keyboard (ketik teks dari jarak jauh).
- Kompres frame lebih baik / naikkan fps kalau jaringan bagus.
- Versi untuk komputer (program desktop kecil, bukan APK).
