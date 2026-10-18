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

> **Kalau kamu sudah pernah Publish rules sebelumnya, WAJIB tempel ulang dan Publish lagi** setelah update ini -- struktur penyimpanan "perangkat tersimpan" berubah (dari per-akun jadi satu daftar bersama, lihat bagian "Hubungkan tanpa kode" di bawah), kalau rules lama masih dipakai maka fitur ini akan terus gagal.
7. Di Authentication → Sign-in method, aktifkan **Anonymous**.
8. **Di halaman yang sama, aktifkan juga provider Email/Password.** Ini
   LANGKAH YANG SERING KELEWATAN -- kalau tidak diaktifkan, tombol
   Masuk, Daftar akun baru, DAN Masuk cepat semuanya akan gagal dengan
   error (biasanya "CONFIGURATION_NOT_FOUND" atau pesan error umum),
   karena provider ini beda dari Anonymous dan beda juga dari Google
   (lihat bagian "Login Google" di bawah -- itu provider ketiga yang
   terpisah lagi).

> **Kalau sebelumnya semua cara login selalu gagal** (Masuk, Daftar,
> Masuk cepat -- bukan cuma salah satu), ini hampir pasti karena
> langkah 8 di atas belum dilakukan. Buka Firebase Console sekarang,
> cek provider Email/Password statusnya "Enabled" atau belum -- tidak
> perlu build ulang APK setelah mengaktifkannya, cukup buka lagi
> aplikasi yang sudah terpasang.

### 2. Masukkan google-services.json ke GitHub sebagai secret

File itu tidak boleh langsung dikomit ke repo publik/private biasa
begitu saja kalau kamu ingin lebih aman — jadi dikirim lewat GitHub
Secret, di-decode saat build:

1. Di terminal/HP, ubah file jadi base64 (atau pakai situs base64 encoder — isi file, bukan path).
2. Buka GitHub repo → Settings → Secrets and variables → Actions → New repository secret.
3. Nama: `REMOTECTL_GOOGLE_SERVICES_JSON`
4. Value: hasil base64 dari file `google-services.json`.

### 3. Jalankan build

Buka tab **Actions** di GitHub repo → workflow **"Build Remote Control APK"** → Run workflow. Isi kolom **version** (contoh `0.2.0`) dan **changelog** (catatan singkat perubahan, akan tampil di pop-up update pengguna). Setelah selesai, unduh APK dari bagian **Artifacts**, atau dari halaman **Releases** repo.

> Setiap kali build ulang untuk merilis pembaruan, **naikkan nomor version** (misal 0.2.0 → 0.2.1) dibanding rilis sebelumnya. Aplikasi yang sudah terpasang di HP pengguna akan otomatis mendeteksi versi baru ini dan menampilkan pop-up update -- lihat bagian "Update otomatis" di bawah.

## Update otomatis

Setiap kali aplikasi dibuka, ia mengecek rilis terbaru di GitHub Releases (repo publik, tidak perlu token). Kalau ada versi lebih baru, muncul pop-up dengan catatan perubahan dan tombol "Update Sekarang" -- APK baru diunduh otomatis, lalu Android membuka dialog pemasangan (cukup sekali ketuk "Install", ini batasan keamanan Android dan tidak bisa dilewati tanpa root).

> Pengecekan update ini baru jalan SETELAH berhasil login (ada di halaman utama, bukan halaman login). Kalau login masih gagal karena masalah di atas, update otomatis juga belum akan kelihatan -- begitu login beres, update otomatis ikut jalan lagi.

## Login Google (biar gak sering "error padahal udah benar")

Login email/kata sandi manual rawan salah ketik tanpa disadari (spasi
tersembunyi, Caps Lock, dll), jadi sekarang ada juga tombol **"🔵 Masuk
dengan Google"** di halaman login -- lebih jarang gagal karena tidak
ada yang diketik ulang.

Supaya tombol ini berfungsi (sekali setup saja, aktif untuk semua
build selanjutnya):

1. Buka [Firebase Console](https://console.firebase.google.com/) → proyek `apk-maker-ceb1f` → **Authentication → Sign-in method**.
2. Aktifkan provider **Google** (pilih email dukungan proyek lalu Simpan).
3. Masih di halaman yang sama, klik provider Google tadi → salin nilai **"Web client ID"** (bentuknya seperti `xxxxx.apps.googleusercontent.com`).
4. Buka **Project settings** (ikon gerigi) → scroll ke bagian "Your apps" → pilih app Android `com.byhasstudio.remotectl` → **Add fingerprint** → tempel SHA-1 ini:
   ```
   AE:B3:D4:51:AC:2A:09:37:56:24:73:76:63:C8:36:DE:99:DF:C1:4B
   ```
   (Ini SHA-1 keystore debug yang sudah di-pin di repo ini -- `app/debug.keystore` -- jadi nilainya tidak akan berubah-ubah tiap build CI. Tidak perlu generate ulang.)
5. Di GitHub repo → Settings → Secrets and variables → Actions → **New repository secret**: nama `REMOTECTL_GOOGLE_WEB_CLIENT_ID`, isi value dengan Web client ID dari langkah 3.
6. Build ulang APK seperti biasa lewat Actions -- workflow otomatis mengisi Web Client ID itu ke dalam aplikasi.

Login email/kata sandi tetap ada sebagai cadangan buat yang tidak
punya/tidak mau pakai akun Google.

## Hubungkan tanpa kode (akun + kata sandi)

Sekarang aplikasi mewajibkan **login (email + kata sandi, Google, atau "Masuk cepat")** -- bukan supaya dikunci per akun, tapi supaya setiap permintaan konek bisa dicatat dengan jelas siapa yang terhubung.

**Perangkat yang mengaktifkan fitur ini akan tetap muncul di daftar "Perangkat tersimpan", DI PERANGKAT MANA PUN dan LOGIN PAKAI AKUN APA PUN** -- tidak perlu akun yang sama persis di semua perangkat. Ini sengaja diubah supaya kamu tidak kejebak kalau salah satu perangkat login pakai "Masuk cepat" dan perangkat lain login manual/Google -- semuanya tetap satu daftar yang sama. Yang menjaga keamanannya adalah kata sandi per perangkat (kalau diaktifkan) dan kode sesi 6 digit sekali pakai, bukan lagi pemisahan akun.

Cara pakai:
1. Login (akun apa saja, tidak harus sama) di semua perangkat yang mau dihubungkan.
2. Di perangkat yang ingin bisa dikontrol: buka menu "⚙ Pengaturan hubungkan tanpa kode" dari halaman utama, aktifkan, isi kata sandi (minimal 6 karakter), Simpan.
3. Di perangkat pengontrol: buka "Kontrol perangkat lain" → "📱 Perangkat tersimpan" → pilih perangkat → masukkan kata sandinya → otomatis tersambung, tanpa perlu menyalin kode 6 digit.

Catatan penting:
- Begitu diaktifkan, perangkat itu menjalankan layanan latar belakang kecil (notifikasi permanen "Remote Ctl aktif") supaya tetap bisa menerima permintaan konek walau aplikasi tidak sedang dibuka. Layanan ini otomatis menyala lagi kalau HP direstart, dan mencoba menyala ulang sendiri kalau sistem mematikannya.
- **Tetap tekan tombol "🔋 Matikan optimasi baterai untuk aplikasi ini"** di halaman Pengaturan setelah mengaktifkan fitur ini. **Beberapa merk HP (Xiaomi/MIUI, Oppo/ColorOS, Vivo, dll.) tetap bisa agresif mematikan aplikasi latar belakang** meski sudah dikecualikan dari optimasi baterai Android standar -- untuk HP-HP ini, biasanya masih perlu tambahan cari pengaturan khusus merk seperti "Autostart" / "Izinkan berjalan di latar belakang" di aplikasi Pengaturan bawaan HP tersebut.
- Saat ada permintaan konek masuk, Android **tetap akan menampilkan satu dialog sistem singkat** ("Mulai merekam atau mentransmisikan?") di perangkat yang dikontrol -- ini pagar keamanan Android sendiri dan tidak bisa dilewati tanpa root, jadi bukan 100% tanpa sentuhan sama sekali, tapi jauh lebih singkat dibanding buka aplikasi dan salin kode manual.
- Fitur ini sekarang juga ada di versi Windows (`windows-control/`) -- daftar perangkatnya sama (bersama), jadi HP dan laptop langsung saling lihat begitu sama-sama mengaktifkan fitur ini, tanpa perlu akun yang sama.

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

## Keyboard jarak jauh

Di layar kontrol, tombol **"⌨ Keyboard"** membuka kolom ketik kecil di
bawah. Setiap huruf yang diketik langsung dikirim ke perangkat yang
dikontrol dan ditimpakan ke kolom teks yang sedang fokus di sana lewat
Accessibility Service -- jadi terasa seperti mengetik langsung di
perangkat itu. Tombol **⏎** dan **⌫** mengirim Enter/Backspace secara
eksplisit, dan tombol **A-/A+** memperbesar/mengecilkan ukuran huruf di
kolom ketik ini sendiri (murni tampilan lokal, tidak memengaruhi
perangkat yang dikontrol).

> Catatan: hanya bisa mengisi kolom teks yang memang sedang aktif
> difokus di perangkat yang dikontrol (misalnya kolom chat atau kolom
> pencarian yang sudah di-tap duluan) -- fitur ini belum bisa membuka
> kolom teks itu sendiri dari jarak jauh.

> **Kalau update ini baru kamu pasang dan keyboard masih belum jalan**:
> izin Accessibility Service di perangkat yang DIKONTROL perlu
> dinyalakan ULANG sekali -- buka Pengaturan > Aksesibilitas di HP itu,
> matikan dulu "Remote Ctl" kalau sudah aktif, lalu nyalakan lagi. Ini
> karena APK versi baru minta izin tambahan (baca isi kolom teks yang
> sedang fokus) supaya tombol ketik/Enter/Backspace bisa benar-benar
> jalan -- sebelumnya izin ini belum diminta sama sekali, itu sebabnya
> keyboard selalu diam tanpa pesan error apa pun.

## Ide pengembangan berikutnya

- Kedaluwarsakan/hapus sesi otomatis setelah tidak dipakai.
- Kompres frame lebih baik / naikkan fps kalau jaringan bagus.
- Tombol navigasi (Back/Home/Recents) langsung dari layar kontrol.
