// Ikon 100% dari logo pengguna: logo TIDAK dipotong, TIDAK diberi latar/blur, TIDAK diperkecil.
// Gambar dimuat utuh (hanya diskalakan ke kotak ikon; kalau tidak persegi, sisi kosong dibiarkan transparan).
// Tanpa ikon (atau ikon rusak) dibuat ikon huruf pertama nama aplikasi.
//   node make-icon.js            -> buat assets/*.png (dipakai capacitor-assets & EXE)
//   node make-icon.js android    -> timpa ikon peluncur Android dengan logo penuh (tanpa ikon adaptif)
const sharp = require("sharp");
const fs = require("fs");

const name = (process.env.APP_NAME || "A").trim() || "A";
const hasIcon = process.env.HAS_ICON === "true";
const CLEAR = { r: 0, g: 0, b: 0, alpha: 0 };
const SIZE = 1024;

const esc = s => s.replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&apos;"}[c]));
function hslHex(h, s, l) {
  const a = s * Math.min(l, 1 - l);
  const f = n => { const k = (n + h / 30) % 12; return Math.round(255 * (l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1)))); };
  return "#" + [f(0), f(8), f(4)].map(v => v.toString(16).padStart(2, "0")).join("");
}

async function letterIcon() {
  let hue = 0;
  for (const ch of name) hue = (hue * 31 + ch.codePointAt(0)) % 360;
  const letter = esc([...name][0].toUpperCase());
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}">
    <rect width="${SIZE}" height="${SIZE}" fill="${hslHex(hue, 0.6, 0.42)}"/>
    <text x="512" y="720" font-size="560" font-family="DejaVu Sans, Arial, sans-serif" font-weight="bold"
          fill="#ffffff" text-anchor="middle">${letter}</text></svg>`;
  return sharp(Buffer.from(svg)).png().toBuffer();
}

// Logo utuh, memenuhi kotak ikon sepenuhnya.
async function userIcon() {
  return sharp("icon-src.bin", { limitInputPixels: 50_000_000 })
    .rotate()
    .resize(SIZE, SIZE, { fit: "contain", background: CLEAR })
    .png()
    .toBuffer();
}

async function main() {
  fs.mkdirSync("assets", { recursive: true });
  let base;
  if (hasIcon) {
    try { base = await userIcon(); }
    catch (err) { console.warn("Ikon pengguna tidak bisa dibaca, pakai ikon huruf:", err.message); }
  }
  if (!base) base = await letterIcon();
  fs.writeFileSync("assets/icon-only.png", base);
  // file ini hanya agar capacitor-assets berjalan; ikon adaptifnya dibuang di tahap "android"
  fs.writeFileSync("assets/icon-foreground.png", base);
  fs.writeFileSync("assets/icon-background.png",
    await sharp({ create: { width: SIZE, height: SIZE, channels: 4, background: CLEAR } }).png().toBuffer());
  console.log("Ikon siap");
}

// Android: pakai ikon biasa (persegi) berisi logo penuh, bukan ikon adaptif yang selalu dipotong topeng launcher.
async function androidIcons() {
  const res = "android/app/src/main/res";
  const base = fs.readFileSync("assets/icon-only.png");
  fs.rmSync(`${res}/mipmap-anydpi-v26`, { recursive: true, force: true });
  const dens = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
  for (const [d, px] of Object.entries(dens)) {
    const dir = `${res}/mipmap-${d}`;
    fs.mkdirSync(dir, { recursive: true });
    const png = await sharp(base).resize(px, px, { fit: "contain", background: CLEAR }).png().toBuffer();
    fs.writeFileSync(`${dir}/ic_launcher.png`, png);
    fs.writeFileSync(`${dir}/ic_launcher_round.png`, png);
    for (const f of ["ic_launcher_foreground.png", "ic_launcher_background.png"]) fs.rmSync(`${dir}/${f}`, { force: true });
  }
  console.log("Ikon Android (logo penuh) siap");
}

(process.argv[2] === "android" ? androidIcons() : main()).catch(err => { console.error(err); process.exit(1); });
