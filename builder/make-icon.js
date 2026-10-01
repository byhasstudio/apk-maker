// Ikon dari gambar pengguna. Gambar tidak dipotong: dimuat utuh (contain) di atas
// latar blur dari gambar yang sama. Ikon adaptif memakai zona aman agar tidak terpotong topeng launcher.
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

// Gambar utuh tanpa dipotong; sisa ruang (jika tidak persegi) diisi versi blur gambar itu sendiri.
async function userIcon() {
  const src = await sharp("icon-src.bin", { limitInputPixels: 50_000_000 }).rotate().png().toBuffer();
  const bg = await sharp(src).resize(SIZE, SIZE, { fit: "cover" }).blur(40).png().toBuffer();
  const fg = await sharp(src).resize(SIZE, SIZE, { fit: "inside" }).png().toBuffer();
  return sharp(bg).composite([{ input: fg, gravity: "center" }]).png().toBuffer();
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

  // Ikon adaptif: gambar diperkecil ke zona aman (~49%) supaya tidak terpotong topeng bulat/squircle.
  const INNER = 500, PAD = (SIZE - INNER) / 2;
  const fg = await sharp(base)
    .resize(INNER, INNER)
    .extend({ top: PAD, bottom: PAD, left: PAD, right: PAD, background: CLEAR })
    .png().toBuffer();
  fs.writeFileSync("assets/icon-foreground.png", fg);

  // Latar adaptif: gambar yang sama di-blur, jadi menyatu dengan ikon.
  const bg = await sharp(base).blur(40).png().toBuffer();
  fs.writeFileSync("assets/icon-background.png", bg);
  console.log("Ikon siap");
}

main().catch(err => { console.error(err); process.exit(1); });
