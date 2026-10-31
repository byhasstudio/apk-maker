const { app, BrowserWindow, shell, Menu, dialog, Tray } = require("electron");
const fs = require("fs");
const path = require("path");
const cfg = require("./app-config.json");
const fx = cfg.fx ? require("./fx/fx.js") : null;
const TT = k => { try { return fx && fx.T ? fx.T(k) : k; } catch (_) { return k; } };
if (cfg.fx) { app.commandLine.appendSwitch("disable-features", "CalculateNativeWinOcclusion"); app.commandLine.appendSwitch("disable-renderer-backgrounding"); app.commandLine.appendSwitch("disable-background-timer-throttling"); }
const safe = u => /^https?:/i.test(u);
const BLOCK_HTML = '<!doctype html><meta charset="utf-8"><title>Diblokir</title><body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;font-family:Segoe UI,sans-serif;background:#111827;color:#f9fafb;text-align:center"><div><div style="font-size:64px">&#128683;</div><h2>Aplikasi ini sedang dinonaktifkan</h2><p style="color:#9ca3af">Hubungi pembuat aplikasi untuk informasi lebih lanjut.</p></div></body>';
let win = null, blocked = false, forced = "", tray = null, quitting = false;
const hidden = process.argv.indexOf("--hidden") >= 0;   // dijalankan otomatis oleh Windows: jangan buka jendela utama
const flagFile = () => path.join(app.getPath("userData"), "blocked.flag");
// Tanya Worker apakah aplikasi ini diblokir. Offline = pakai status terakhir yang diketahui.
async function checkBlocked(o) {
  if (!cfg.api || !cfg.jobId) return {};
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 6000);
    const r = await fetch(cfg.api + "/api/app-status/" + cfg.jobId + (o ? "?o=1" : ""), { signal: ctl.signal, cache: "no-store" });
    clearTimeout(t);
    if (!r.ok) throw new Error("status " + r.status);
    const d = await r.json();
    try { if (d.blocked) fs.writeFileSync(flagFile(), "1"); else fs.unlinkSync(flagFile()); } catch (_) {}
    return d;
  } catch (_) {
    try { return { blocked: fs.existsSync(flagFile()) }; } catch (__) { return {}; }
  }
}
const esc = x => String(x).split("&").join("&amp;").split("<").join("&lt;").split(">").join("&gt;").split("'").join("&#39;");
function offlineHtml() {
  const u = JSON.stringify(String(cfg.url || "")).split("<").join("%3C");
  return '<!doctype html><meta charset="utf-8"><title>Tidak ada koneksi</title><style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:Segoe UI,sans-serif;background:#f3f4f6;color:#111827;text-align:center;padding:24px;box-sizing:border-box}@media(prefers-color-scheme:dark){body{background:#111827;color:#f9fafb}p{color:#9ca3af}}.i{font-size:72px}h2{margin:12px 0 6px}p{color:#6b7280;margin:0 0 22px;line-height:1.5}button{border:0;border-radius:12px;padding:12px 30px;font-size:1rem;font-weight:700;background:#4f46e5;color:#fff;cursor:pointer}small{display:block;margin-top:24px;opacity:.6}</style><body><div><div class="i">&#128225;</div><h2>Tidak ada koneksi internet</h2><p>Periksa koneksi kamu, lalu coba lagi.<br>Halaman dimuat otomatis begitu tersambung.</p><button id="b">Coba lagi</button><small>' + esc(cfg.name) + ' &middot; v' + esc(cfg.version || "") + '</small></div><script>var U=' + u + ';function go(){location.replace(U)}function t(){return fetch(U,{mode:"no-cors",cache:"no-store"}).then(go)}document.getElementById("b").onclick=function(){var x=this;x.textContent="Memeriksa...";t().catch(function(){x.textContent="Coba lagi"})};window.addEventListener("online",go);setInterval(function(){t().catch(function(){})},8000);</script>';
}
function loadContent() { if (cfg.url) win.loadURL(cfg.url); else win.loadFile("www/index.html"); }
function showBlocked() { win.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(BLOCK_HTML)); }
const FORCE_HTML = '<!doctype html><meta charset="utf-8"><title>Perbarui</title><body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;font-family:Segoe UI,sans-serif;background:#111827;color:#f9fafb;text-align:center"><div><div style="font-size:64px">&#11015;</div><h2>Versi ini sudah usang</h2><p style="color:#9ca3af">Unduh versi terbaru untuk melanjutkan.</p><a target="_blank" href="__U__" style="display:inline-block;margin-top:12px;padding:12px 28px;border-radius:12px;background:#4f46e5;color:#fff;text-decoration:none;font-weight:700">Unduh versi terbaru</a></div></body>';
function showForce(u) { win.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(FORCE_HTML.split("__U__").join(encodeURI(u).split("'").join("%27")))); }
function notify(st) {
  if (!st || !st.message || !st.mid) return;
  const f = path.join(app.getPath("userData"), "seen.txt");
  let seen = ""; try { seen = fs.readFileSync(f, "utf8"); } catch (_) {}
  if (seen === st.mid) return;
  try { fs.writeFileSync(f, st.mid); } catch (_) {}
  dialog.showMessageBox(win, { type: "info", title: cfg.name, message: String(st.message) });
}
async function applyState(st) {
  st = st || {};
  if (st.blocked) { if (!blocked) { blocked = true; showBlocked(); } return; }
  if (typeof st.force === "string" && /^https:/.test(st.force)) { if (forced !== st.force) { forced = st.force; blocked = false; showForce(st.force); } return; }
  if (blocked || forced) { blocked = false; forced = ""; loadContent(); }
  notify(st);
}
async function create() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({ width: 1100, height: 760, title: cfg.name, autoHideMenuBar: true, show: !hidden,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, preload: fx ? path.join(__dirname, "fx", "preload.js") : undefined,
      additionalArguments: (fx && cfg.url) ? ["--fx-origin=" + (() => { try { const o = new URL(cfg.url); return o.protocol === "https:" ? o.origin : ""; } catch (_) { return ""; } })()] : [] } });
  // F11 / Alt+Enter = layar penuh, Esc = keluar dari layar penuh (menu dimatikan, jadi harus ditangani manual)
  win.webContents.on("before-input-event", (ev, input) => {
    if (input.type !== "keyDown") return;
    if (input.key === "F11" || (input.alt && input.key === "Enter")) { win.setFullScreen(!win.isFullScreen()); ev.preventDefault(); }
    else if (input.key === "Escape" && win.isFullScreen()) { win.setFullScreen(false); ev.preventDefault(); }
  });
  if (cfg.url) {
    win.webContents.setWindowOpenHandler(({ url }) => { if (safe(url)) shell.openExternal(url); return { action: "deny" }; });
    win.webContents.on("will-navigate", (ev, u) => {
      try { if (new URL(u).hostname !== new URL(cfg.url).hostname) { ev.preventDefault(); if (safe(u)) shell.openExternal(u); } } catch (_) { ev.preventDefault(); }
    });
  }
  win.webContents.setWindowOpenHandler(({ url }) => { if (safe(url)) shell.openExternal(url); return { action: "deny" }; });
  if (fx) { try { fx.start({ app, win, cfg }); } catch (e) { console.error(e); } }
  if (cfg.tray) {
    try {
      tray = new Tray(path.join(__dirname, "icon.png"));
      tray.setToolTip(cfg.name);
      const mkMenu = () => Menu.buildFromTemplate([
        { label: TT("Buka") + " " + cfg.name, click: () => { win.show(); win.focus(); } },
        { label: TT("Jalan otomatis saat Windows menyala"), type: "checkbox", checked: fx ? fx.autoOn() : app.getLoginItemSettings({ args: ["--hidden"] }).openAtLogin, click: it => { if (fx) fx.setAuto(it.checked); else app.setLoginItemSettings({ openAtLogin: it.checked, args: ["--hidden"] }); } },
        ...(fx ? fx.trayItems() : []),
        { type: "separator" },
        { label: TT("Keluar"), click: () => { quitting = true; app.quit(); } }
      ]);
      tray.setContextMenu(mkMenu());
      if (fx && fx.onLang) fx.onLang(() => { try { tray.setContextMenu(mkMenu()); } catch (_) {} });
      tray.on("click", () => { win.show(); win.focus(); });
      win.on("close", ev => { if (!quitting) { ev.preventDefault(); win.hide(); } });
    } catch (_) {}
  }
  // gagal memuat halaman utama (offline / situs tak terjangkau) -> tampilkan halaman "tidak ada koneksi"
  win.webContents.on("did-fail-load", (ev, code, desc, failedUrl, isMain) => {
    if (!isMain || code === -3 || blocked || !cfg.url) return;
    win.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(offlineHtml()));
  });
  await applyState(await checkBlocked(true));
  if (!blocked && !forced) loadContent();
  // cek ulang tiap 10 menit: kalau diblokir/dibuka saat aplikasi berjalan, langsung berlaku
  setInterval(async () => { applyState(await checkBlocked()); }, 600000);
}
if (!app.requestSingleInstanceLock()) { app.quit(); } else {
  app.on("second-instance", () => { if (win) { if (!win.isVisible()) win.show(); if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(create);
}
app.on("before-quit", () => { quitting = true; });
app.on("window-all-closed", () => app.quit());
