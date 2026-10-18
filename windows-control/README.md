# Remote Ctl untuk Windows

Program kecil (`RemoteCtlAgent.exe`) yang dipasang di **komputer Windows
yang ingin dikontrol dari HP**. Ini bukan APK (Android tidak bisa
menjalankan program Windows), melainkan program `.exe` biasa, dibangun
dari proyek C# terpisah di folder ini.

## Cara kerja

Komputer Windows bisa dipakai di **dua arah**, sama seperti HP Android:

- **"Jadikan komputer ini yang dikontrol"**: membuat kode sesi 6 digit
  (atau lewat "hubungkan tanpa kode", lihat di bawah), lalu mengirim
  cuplikan layar berkala ke Firebase Realtime Database dan menerima
  perintah klik/geser dari perangkat pengontrol -- HP ataupun komputer
  Windows lain.
- **"Kontrol perangkat lain"**: masukkan kode sesi (atau pilih dari
  "Perangkat tersimpan"), lalu layar perangkat lain itu tampil di
  jendela dan bisa diklik/digeser dengan mouse untuk mengontrolnya dari
  jarak jauh -- bisa dipakai mengontrol HP Android ataupun komputer
  Windows lain.

- **tap** -> diterjemahkan jadi klik kiri mouse di titik itu.
- **swipe** -> diterjemahkan jadi klik-tahan-geser (drag).
- Belum ada dukungan keyboard atau klik kanan di versi pertama ini.

## Cara mendapatkan EXE-nya

Tidak perlu setup Firebase tambahan (program ini memakai proyek Firebase
yang sama, dan tidak butuh file `google-services.json` karena bukan
aplikasi Android).

1. Buka tab **Actions** di GitHub repo.
2. Jalankan workflow **"Build Remote Control EXE (Windows)"**. Isi kolom **version** (contoh `0.2.0`) dan **changelog**.
3. Setelah selesai, unduh **RemoteCtlAgent.exe** dari bagian Artifacts, atau dari halaman **Releases** repo.

> Naikkan nomor **version** tiap kali merilis pembaruan baru supaya program yang sudah jalan di komputer pengguna otomatis mendeteksinya.

## Update otomatis

Setiap kali dijalankan, program mengecek rilis terbaru di GitHub Releases. Kalau ada versi lebih baru, muncul pop-up dengan catatan perubahan. Kalau pengguna pilih "Update Sekarang", file baru diunduh dan program **mengganti dirinya sendiri lalu restart otomatis** -- tidak perlu unduh manual atau install ulang lagi.

## Cara pakai

1. Jalankan `RemoteCtlAgent.exe` di komputer Windows yang mau dikontrol.
   (Windows SmartScreen mungkin memperingatkan "Windows protected your
   PC" karena file belum punya sertifikat penandatanganan resmi --
   ini wajar untuk program buatan sendiri; klik "More info" lalu
   "Run anyway".)
2. Jendela kecil akan muncul menampilkan kode sesi 6 digit.
3. Di HP, buka aplikasi Remote Ctl, pilih "Kontrol perangkat lain",
   masukkan kode itu, lalu Sambung.

## Login Google

Sama seperti versi Android, jendela "Masuk dengan akun" punya tombol
**"🔵 Masuk dengan Google"** -- membuka browser default Windows ke
halaman login Google, lalu otomatis tersambung kembali ke program ini
begitu selesai. Lebih jarang gagal dibanding email/kata sandi manual.

Butuh **satu setup sekali saja** sebelum tombol ini aktif (beda dari
setup Google di Android -- Windows butuh jenis kredensial OAuth yang
berbeda, "Desktop app", bukan "Web application"):

1. Buka [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → pilih proyek **apk-maker-ceb1f** (proyek Firebase yang sama).
2. **Create Credentials → OAuth client ID** → Application type: **Desktop app** → beri nama bebas (mis. "Remote Ctl Windows") → Create.
3. Salin **Client ID** dan **Client secret** yang muncul.
4. Di GitHub repo → Settings → Secrets and variables → Actions → tambah 2 secret:
   - `REMOTECTL_GOOGLE_DESKTOP_CLIENT_ID` = Client ID dari langkah 3
   - `REMOTECTL_GOOGLE_DESKTOP_CLIENT_SECRET` = Client secret dari langkah 3
5. Pastikan provider **Google** sudah aktif di Firebase Console → Authentication → Sign-in method (langkah ini sama dengan setup Google di Android -- kalau sudah pernah diaktifkan untuk Android, tidak perlu diulang).
6. Build ulang EXE lewat Actions seperti biasa -- kredensial otomatis terisi, tidak perlu edit kode.

> Client secret OAuth tipe "Desktop app" memang tidak diperlakukan sebagai rahasia penuh oleh Google sendiri (ini didokumentasikan resmi di developers.google.com -- aplikasi desktop/instal tidak bisa menyembunyikan secret sepenuhnya), jadi aman disimpan sebagai GitHub secret biasa seperti ini.

## Hubungkan tanpa kode (akun + kata sandi)

Sama seperti versi Android: aktifkan sekali, lalu komputer ini muncul
di daftar "Perangkat tersimpan" milik akun yang sama, tanpa perlu
menyalin kode 6 digit tiap kali.

Cara pakai:
1. Dari jendela utama, klik **"Masuk dengan akun"** -- pakai email +
   kata sandi yang sama di semua perangkat (Android maupun Windows)
   yang ingin dihubungkan.
2. Klik **"⚙ Hubungkan tanpa kode"**, aktifkan, pilih mode tanpa kata
   sandi atau pakai kata sandi tambahan (minimal 6 karakter), centang
   "Nyalakan otomatis saat Windows start" (disarankan), lalu Simpan.
3. Dari perangkat lain (HP atau Windows lain, akun yang sama): buka
   "Kontrol perangkat lain" -> "📱 Perangkat tersimpan" -> pilih
   komputer ini -> masukkan kata sandi kalau diminta -> langsung
   tersambung.

Catatan:
- Menutup jendela utama (tombol X) hanya menyembunyikannya ke **system
  tray**, bukan keluar sepenuhnya -- supaya fitur ini tetap jalan di
  latar belakang. Klik kanan ikon di tray untuk membuka lagi atau
  keluar sungguhan.
- Sesi login disimpan terenkripsi di komputer ini (Windows DPAPI,
  terkunci ke akun Windows yang sedang login) supaya setelah komputer
  di-restart, program bisa masuk lagi otomatis tanpa perlu mengetik
  kata sandi akun berulang kali.
- Tidak ada dialog izin tambahan di Windows seperti di Android (tidak
  ada batasan OS yang sama) -- begitu permintaan konek masuk dan kata
  sandinya cocok, pengiriman layar langsung dimulai di latar belakang.

## Keyboard jarak jauh

Tombol **"⌨ Keyboard"** di jendela kontrol membuka kolom ketik kecil di
bawah. Semua yang diketik di situ langsung dikirim sebagai keystroke
asli ke komputer yang dikontrol lewat SendInput -- berfungsi di
aplikasi apa pun yang sedang fokus di sana, sama seperti mengetik di
keyboard fisiknya. Tombol **⏎ Enter** dan **⌫** mengirim tombol itu
secara langsung, dan **A-/A+** mengubah ukuran huruf kolom ketik ini
(cuma tampilan lokal).

## Batasan versi pertama ini

- Komputer dengan banyak monitor hanya memakai monitor utama (primary).
