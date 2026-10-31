# Dynamic Island untuk Windows

Aplikasi Windows kecil: pulau hitam di tepi layar yang melebar saat ada notifikasi.
Halaman pengaturannya diambil dari `index.html` di repo `dynamicisland_windows`
(disalin otomatis oleh workflow saat build, jadi cukup edit prototipe di repo itu).

## Build

Actions > **Build Dynamic Island EXE (Windows)**:

- Push ke `main` yang menyentuh `windows-dynamic-island/**` membangun otomatis (hasil di Artifacts).
- **Run workflow** dengan nomor versi `x.y.z` membuat GitHub Release `dynamicisland-win-vx.y.z`.

## Yang sudah jalan (v0.0.x)

- Pulau di atas/bawah/kiri/kanan layar, warna, ukuran, posisi, buram, selalu tampil atau hanya saat ada notifikasi
- Notifikasi tes, peringatan baterai & charger, pemberitahuan teks yang disalin
- Jangan ganggu, jam tenang, riwayat notifikasi, suara, lama tampil
- Ikon tray, jalan otomatis saat Windows menyala (klik kanan ikon tray)

## Belum ada

Membaca notifikasi aplikasi lain, animasi buka/tutup jendela, tombol pintas, live activity,
kontrol media, FPS game, kursor kustom, crosshair, kalender & cuaca.
Kartu-kartu itu disembunyikan dari halaman pengaturan sampai fiturnya dibuat.

Pengaturan tersimpan di `%APPDATA%\DynamicIsland\config.json`; catatan error di `crash.log` folder yang sama.
