# Crosshair Overlay (Android)

Aplikasi kecil yang menampilkan **crosshair di atas aplikasi/game lain, permanen** sampai kamu
matikan sendiri. Posisi, bentuk, ukuran, warna, dan transparansinya bisa diatur.

Jendela crosshair memakai teknik overlay yang sama dengan Dynamic Island native di builder APK
(`TYPE_APPLICATION_OVERLAY` + foreground service), tapi jendelanya **tidak bisa disentuh**, jadi
sentuhan tembus ke aplikasi di bawahnya dan tidak mengganggu permainan.

## Fitur

- **Permanen**: jalan sebagai foreground service (`START_STICKY`) dan otomatis menyala lagi setelah
  HP restart atau setelah aplikasi diperbarui, selama belum dimatikan.
- **Matikan kapan saja**: saklar "Crosshair aktif" di aplikasi, atau tombol **Matikan** di notifikasi.
- **Posisi bisa diatur**
  - **Geser langsung**: tombol "Geser langsung di layar" (atau **Atur posisi** di notifikasi, bisa
    dipencet saat sedang di dalam game). Seret crosshair ke tempat yang diinginkan; terkunci lagi
    otomatis beberapa detik setelah dilepas.
  - **Slider + tombar panah** untuk geser halus, plus tombol **Tengah**.
  - Posisi disimpan sebagai pecahan layar, jadi tetap pas saat layar diputar.
- **Tampilan**: 7 bentuk (plus, titik, lingkaran, plus + titik, T, silang, lingkaran + titik), ukuran,
  ketebalan, celah tengah, transparansi, garis tepi hitam, 8 warna preset + kode hex bebas.
- **Update otomatis**: lihat bagian di bawah.

## Cara mendapatkan APK

1. Buka tab **Actions** di GitHub repo.
2. Jalankan workflow **"Build Crosshair Overlay APK"**. Isi **version** (contoh `1.0.0`) dan **changelog**.
3. Setelah selesai, unduh `Crosshair-<versi>.apk` dari halaman **Releases** (tag `crosshair-v<versi>`).
4. Pasang di HP, buka, lalu izinkan **tampil di atas aplikasi lain**.

Setiap perubahan kode di folder ini yang masuk ke `main` juga menjalankan build otomatis sebagai
cek kompilasi (hanya artifact, tanpa rilis).

## Update otomatis

- Tiap workflow dijalankan lewat tombol **Run workflow**, selain rilis `crosshair-v<versi>`, workflow
  juga memperbarui rilis penunjuk **`crosshair-latest`** yang berisi APK terbaru.
- Aplikasi mengecek rilis itu **saat dibuka** dan **tiap 6 jam di latar belakang** (selagi crosshair
  aktif). Kalau ada versi lebih baru: muncul notifikasi / pop-up berisi catatan perubahan, lalu
  **Update sekarang** mengunduh APK dan membuka pemasang Android.
- **Naikkan nomor versi tiap rilis**, kalau tidak aplikasi di HP tidak menganggapnya sebagai update.
- Android tidak mengizinkan aplikasi hasil sideload memasang dirinya sendiri tanpa persetujuan, jadi
  langkah terakhir (ketuk **Update/Install**) tetap perlu satu sentuhan. Pertama kali, Android juga
  akan meminta izin "pasang aplikasi dari sumber ini".
- Update hanya bisa menimpa versi lama kalau **tanda tangannya sama**. Karena itu workflow selalu
  menyalin `remote-control/app/debug.keystore` ke proyek ini sebelum build. **Jangan hapus file itu.**
- Update otomatis diambil dari repo ini (`byhasstudio/apk-maker`) dan harus publik, sama seperti
  update Remote Ctl.

## Izin yang dipakai

| Izin | Untuk apa |
|------|-----------|
| `SYSTEM_ALERT_WINDOW` | menampilkan crosshair di atas aplikasi lain |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`) | menjaga crosshair tetap hidup |
| `POST_NOTIFICATIONS` | notifikasi kontrol dan info update |
| `RECEIVE_BOOT_COMPLETED` | menyalakan lagi crosshair setelah HP restart |
| `INTERNET` | cek dan unduh update dari GitHub |
| `REQUEST_INSTALL_PACKAGES` | membuka pemasang untuk update |

## Catatan

- Minimal Android 8.0 (API 26).
- Beberapa HP (Xiaomi, Oppo, Vivo, dll.) punya penghemat baterai agresif. Kalau crosshair hilang
  sendiri, matikan optimasi baterai untuk aplikasi ini dan aktifkan **Autostart**.
- Beberapa game mendeteksi overlay dan melarangnya di aturan permainan online. Pakai dengan bijak.
