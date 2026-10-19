# Game Asimetris 3D (1 Killer vs 4 Survivor) - Web / Three.js

Game web satu file (`index.html`) yang dibuat jadi APK (Android) dan EXE (Windows) lewat alur build GitHub apk-maker.

## Yang sudah ada
- Survivor dengan karakter chibi (10 karakter dari `chibi_characters.html`, ganti dengan tombol atau tombol C).
- Kamera third-person (seret mouse / geser layar kanan, roda mouse untuk zoom).
- Gerak WASD (PC) atau joystick virtual layar kiri (Android).
- Mekanik **Lantai Licin** lewat variabel `S.isSlippery`:
  - `false`: jalan normal, kecepatan standar.
  - `true`: WASD jadi gaya dorong (momentum), friksi hampir nol, meluncur seperti es.
  - Menabrak dinding saat licin: karakter jatuh, tidak bisa bergerak 3 detik, lalu bangun.
- Area es = separuh timur arena. Tombol I / tombol "Paksa Licin" memaksa licin di mana saja untuk tes.
- Semua angka (kecepatan, friksi, lama jatuh) ada di objek `CFG` di bagian game.

## Belum ada
Peran Killer dan multiplayer (tinggal pilih Firebase atau WebSocket).
