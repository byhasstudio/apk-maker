# SyhabOS (Windows)

Sumber EXE SyhabOS: Dynamic Island, Dock, Launchpad, Spotlight, Menu bar, dan seterusnya.

Cara build: buka tab **Actions**, pilih **Build SyhabOS (Windows)**, klik **Run workflow**, isi versi (contoh `1.0.71`).
Hasilnya ada di **Releases** (tag `syhabos-vX.Y.Z`) dan di artifact run tersebut.

## Isi folder

- `main.js` : proses utama Electron (jendela, tray, jalan otomatis).
- `index.html` : halaman pengaturan SyhabOS (UI yang tampil di jendela). Kalau file ini sangat besar dan dipecah, workflow juga menerima pecahannya di `page/*.part` dan menyambungnya berurutan.
- Modul `fx/` (island, dock, launchpad, dst.) dan pembantu `.cs` tetap diambil dari `builder/fx-pack-*.txt`, sama seperti alur "Build EXE".

## Memperbarui tampilan

Ganti `syhabos/index.html` (atau `syhabos/page/*.part`) lalu jalankan workflow lagi.
Halaman harus memuat `<meta name="win-island" content="1">`; workflow menolak build kalau penanda ini tidak ada.
