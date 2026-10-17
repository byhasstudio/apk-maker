# Remote Ctl untuk Windows

Program kecil (`RemoteCtlAgent.exe`) yang dipasang di **komputer Windows
yang ingin dikontrol dari HP**. Ini bukan APK (Android tidak bisa
menjalankan program Windows), melainkan program `.exe` biasa, dibangun
dari proyek C# terpisah di folder ini.

## Cara kerja

Sama persis dengan versi Android: membuat kode sesi 6 digit, lalu
mengirim cuplikan layar berkala ke Firebase Realtime Database dan
menerima perintah klik/geser dari HP pengontrol. HP yang sudah dipasangi
aplikasi Remote Ctl (bagian "Kontrol perangkat lain") bisa langsung
dipakai mengontrol komputer ini tanpa perlu diubah sama sekali --
sisi pengontrol memang dibuat generik.

- **tap** dari HP -> diterjemahkan jadi klik kiri mouse di titik itu.
- **swipe** dari HP -> diterjemahkan jadi klik-tahan-geser (drag).
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

## Batasan versi pertama ini

- Harus dijalankan manual tiap kali mau dikontrol (belum otomatis
  jalan saat Windows nyala / tidak ada system tray).
- Mouse saja, belum ada kontrol keyboard.
- Komputer dengan banyak monitor hanya memakai monitor utama (primary).
- Berjalan sebagai aplikasi biasa (window), bukan layanan latar
  belakang -- kalau ditutup, kontrol berhenti.
