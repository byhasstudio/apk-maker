# Game Asimetris 3D (1 Killer vs 4 Survivor) - Unity

Skrip ada di `Assets/Scripts/Survivor/`:

- `SurvivorMovement.cs` - gerak Rigidbody WASD, mekanik Lantai Licin (`isSlippery`), jatuh 3 detik saat menabrak dinding.
- `ThirdPersonCamera.cs` - kamera third-person (orbit mouse, anti tembus dinding).
- `SlipperyZone.cs` - trigger area licin (set `isSlippery` otomatis).

## Setup cepat
1. Buat GameObject Survivor: tambahkan `Rigidbody` (Use Gravity on) + `CapsuleCollider` + `SurvivorMovement`.
2. Buat 2 Physic Material: `Normal` (friction 0.6) dan `Slippery` (dynamic/static friction 0, Friction Combine = Minimum). Isi di `SurvivorMovement`.
3. Pasang `ThirdPersonCamera` di Main Camera, isi `target` dengan Survivor.
4. Buat lantai licin: objek dengan Collider (Is Trigger) + `SlipperyZone`. Atau cukup centang `isSlippery` manual di Inspector untuk tes.
5. Dinding harus punya Collider.

## Karakter
Karakter chibi (Kai, Riku, Leo, Dani, Akira, Mika, Sora, Luna, Hana, Yuki) dibuat prosedural dengan Three.js di `chibi_characters.html`, jadi tidak bisa langsung dipakai Unity. Sementara pakai kapsul sebagai placeholder; model bisa diekspor ke glTF lalu diimpor ke Unity.

## Belum ada
Sinkronisasi multiplayer (Netcode/Mirror/Photon), peran Killer, ragdoll tulang penuh (opsional lewat `ragdollBodies`).
