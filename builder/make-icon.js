// Menyiapkan ikon 1024x1024. Tanpa ikon dari pengguna (atau ikon rusak), dibuat ikon huruf pertama nama aplikasi.
const sharp = require("sharp");
const fs = require("fs");

const color = process.env.COLOR || "#1a73e8";
const name = (process.env.APP_NAME || "A").trim() || "A";
const hasIcon = process.env.HAS_ICON === "true";

const esc = s => s.replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&apos;"}[c]));

async function letterIcon() {
  const letter = esc([...name][0].toUpperCase());
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
    <rect width="1024" height="1024" fill="${color}"/>
    <text x="512" y="720" font-size="560" font-family="DejaVu Sans, Arial, sans-serif" font-weight="bold"
          fill="#ffffff" text-anchor="middle">${letter}</text></svg>`;
  return sharp(Buffer.from(svg)).png().toBuffer();
}

async function userIcon() {
  return sharp("icon-src.bin", { limitInputPixels: 50_000_000 })
    .rotate() // hormati orientasi EXIF dari foto
    .resize(1024, 1024, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
}

async function main() {
  fs.mkdirSync("assets", { recursive: true });
  let base;
  if (hasIcon) {
    try {
      base = await userIcon();
    } catch (err) {
      console.warn("Ikon pengguna tidak bisa dibaca, pakai ikon huruf:", err.message);
    }
  }
  if (!base) base = await letterIcon();
  fs.writeFileSync("assets/icon-only.png", base);

  // Ikon adaptif Android: gambar dikecilkan dan diberi ruang kosong di tepi
  const fg = await sharp(base)
    .resize(640, 640, { fit: "inside" })
    .extend({ top: 192, bottom: 192, left: 192, right: 192, background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  fs.writeFileSync("assets/icon-foreground.png", fg);
  console.log("Ikon siap");
}

main().catch(err => { console.error(err); process.exit(1); });
