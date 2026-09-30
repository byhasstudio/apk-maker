// Membuat capacitor.config.json dari input. Semua nilai divalidasi ulang di sini.
const fs = require("fs");
const e = process.env;

const name = e.APP_NAME || "";
const id = e.APP_ID || "";
const color = e.COLOR || "";
const mode = e.MODE || "";

if (!/^[\p{L}\p{N} ._-]{2,30}$/u.test(name)) throw new Error("Nama aplikasi tidak valid");
if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(id) || id.length > 60) throw new Error("appId tidak valid");
if (!/^#[0-9a-fA-F]{6}$/.test(color)) throw new Error("Warna tidak valid");

const cfg = {
  appId: id,
  appName: name,
  webDir: "www",
  backgroundColor: color,
  android: { backgroundColor: color, allowMixedContent: false },
};

if (mode === "url") {
  const u = new URL(e.SITE_URL || "");
  if (u.protocol !== "https:") throw new Error("URL harus https");
  cfg.server = { url: u.href, cleartext: false, allowNavigation: [u.hostname] };
}

fs.writeFileSync("capacitor.config.json", JSON.stringify(cfg, null, 2));
console.log("capacitor.config.json dibuat untuk", id);
