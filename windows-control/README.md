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

## Login (username bebas + kata sandi)

Jendela "Masuk dengan akun" cukup minta **username bebas** (tidak perlu
format email, tidak pakai akun Google/Gmail) dan **kata sandi** (minimal
6 karakter, batasan dari Firebase). Di balik layar username itu diubah
otomatis jadi "email" internal (`username@remotectl.local`) supaya tetap
diterima provider Email/Password Firebase -- skemanya sama persis dengan
Android, jadi akun yang sama bisa dipakai login di kedua platform.

Siapa pun boleh Daftar/Masuk pakai username apa saja, dan tetap bisa
pakai fitur inti remote control lewat **kode 6 digit** seperti biasa.
Yang khusus hanya untuk username **`haerka949`** (akun admin) adalah
fitur "hubungkan tanpa kode" di bawah -- lihat `remote-control/README.md`
bagian "Akun & hubungkan tanpa kode" untuk detail lengkapnya.

## Hubungkan tanpa kode (akun admin)

Begitu login pakai akun admin (`haerka949`) di komputer ini, komputer
ini **otomatis muncul & bisa langsung dihubungkan tanpa kode** dari
perangkat lain yang login akun yang sama -- tidak perlu lagi
mengaktifkan toggle "hubungkan tanpa kode" secara manual atau memasang
kata sandi tambahan (walau opsi itu tetap ada kalau mau dipakai untuk
alasan lain).

Cara pakai:
1. Dari jendela utama, klik **"Masuk dengan akun"** -- login/daftar
   pakai username `haerka949` + kata sandi yang sama di semua perangkat
   (Android maupun Windows) yang ingin dihubungkan.
2. (Opsional) Klik **"⚙ Hubungkan tanpa kode"** kalau mau mengatur kata
   sandi tambahan khusus komputer ini, lalu centang "Nyalakan otomatis
   saat Windows start" (disarankan) supaya komputer ini tetap bisa
   diakses walau programnya tidak dibuka manual lagi setelah Windows
   restart -- lalu Simpan.
3. Dari perangkat lain (HP atau Windows lain, login akun admin yang
   sama): buka "Kontrol perangkat lain" -> "📱 Perangkat tersimpan" ->
   pilih komputer ini -> langsung tersambung.

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

## Perintah jarak jauh (⚡, khusus akun admin)

Di jendela "📱 Perangkat tersimpan", **akun admin (`haerka949`)** melihat
tombol **⚡** di tiap baris perangkat -- diklik, muncul menu perintah
yang langsung dikirim ke perangkat itu **tanpa perlu tersambung dulu**
(tidak butuh sesi kontrol layar aktif, cukup perangkat tujuan masih
login & programnya masih jalan di latar belakang):

- **⏻ Matikan** -- `shutdown /s`
- **🔄 Restart** -- `shutdown /r`
- **🚪 Keluar akun Windows** -- log off sesi yang sedang aktif
- **💤 Tidurkan** -- sleep lewat `powrprof.dll`
- **💬 Kirim teks...** -- buka jendela Command Prompt di komputer tujuan menampilkan teks yang kamu ketik

Perintah matikan/restart/keluar akun diminta konfirmasi dulu (dialog Ya/Tidak) sebelum dikirim, karena efeknya langsung dan tidak bisa dibatalkan.

## Batasan versi pertama ini

- Komputer dengan banyak monitor hanya memakai monitor utama (primary).
