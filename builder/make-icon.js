// Ikon 100% dari gambar pengguna (sudah di-crop/diedit di halaman web, dikirim 512x512).
// Tanpa ikon (atau ikon rusak) dibuat ikon huruf pertama nama aplikasi.
const sharp = require("sharp");
const fs = require("fs");

const name = (process.env.APP_NAME || "A").trim() || "A";
const hasIcon = process.env.HAS_ICON === "true";
const CLEAR = { r: 0, g: 0, b: 0, alpha: 0 };

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
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
    <rect width="1024" height="1024" fill="${hslHex(hue, 0.6, 0.42)}"/>
    <text x="512" y="720" font-size="560" font-family="DejaVu Sans, Arial, sans-serif" font-weight="bold"
          fill="#ffffff" text-anchor="middle">${letter}</text></svg>`;
  return sharp(Buffer.from(svg)).png().toBuffer();
}

async function userIcon() {
  return sharp("icon-src.bin", { limitInputPixels: 50_000_000 })
    .rotate()
    .resize(1024, 1024, { fit: "cover" })
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

  // Ikon adaptif: seluruh gambar ditaruh persis di area yang terlihat (72/108 dari kanvas),
  // jadi tampil utuh tanpa warna tambahan. Latar dibiarkan transparan.
  const fg = await sharp(base)
    .resize(683, 683)
    .extend({ top: 170, bottom: 171, left: 170, right: 171, background: CLEAR })
    .png().toBuffer();
  fs.writeFileSync("assets/icon-foreground.png", fg);

  const bg = await sharp({ create: { width: 1024, height: 1024, channels: 4, background: CLEAR } }).png().toBuffer();
  fs.writeFileSync("assets/icon-background.png", bg);
  console.log("Ikon siap");
}

main().catch(err => { console.error(err); process.exit(1); });
