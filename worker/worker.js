const MAX_FILE = 24 * 1024 * 1024;
const MAX_ICON = 1024 * 1024;
const MAX_MP4 = 40 * 1024 * 1024;
const FILE_TTL = 3600;       // file sementara hilang otomatis setelah 1 jam
const STATUS_TTL = 86400;    // status disimpan 1 hari
const JOB_TTL = 30 * 86400;  // catatan build untuk panel admin disimpan 30 hari
const UID_RE = /^[A-Za-z0-9_-]{1,128}$/;
// Batas build per IP per hari. Default: TANPA BATAS.
// Untuk mengaktifkan lagi tanpa ubah kode: tambah variabel DAILY_LIMIT (misal 50) di Settings > Variables Worker.
const ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const APPID_RE = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/;
const PERMS_OK = new Set(["camera","microphone","location","notifications","media","overlay","island"]);
// Izin yang hanya boleh dipakai akun admin (tampil di atas aplikasi lain / Dynamic Island native).
const ADMIN_ONLY_PERMS = new Set(["overlay","island"]);
const ORIENT_OK = new Set(["auto","portrait","landscape"]);
const NAME_RE = /^[\p{L}\p{N} ._-]{2,30}$/u;
const ADMIN_EMAIL = "romand94901@gmail.com"; // bisa ditimpa lewat variabel Worker ADMIN_EMAIL
const CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#%^-=+.";
const RESERVED = new Set(["abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","goto","if","implements","import","instanceof","int","interface","long","native","new","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","try","void","volatile","while","true","false","null"]);

function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...extra },
  });
}

function authOk(request, env) {
  const a = request.headers.get("Authorization") || "";
  const b = "Bearer " + (env.BUILD_SECRET || "");
  if (!env.BUILD_SECRET || a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

function checkUrl(s) {
  if (!s || s.length > 500) return null;
  let u;
  try { u = new URL(s); } catch { return null; }
  if (u.protocol !== "https:" || u.username || u.password) return null;
  const h = u.hostname;
  if (!h.includes(".") || h === "localhost" || h.startsWith("[") || /^\d+\.\d+\.\d+\.\d+$/.test(h)) return null;
  return u.href;
}

// Admin = akun dengan email persis itu DAN email sudah terverifikasi (Google otomatis terverifikasi).
function isAdmin(user, env) {
  const a = String(env.ADMIN_EMAIL || ADMIN_EMAIL).toLowerCase();
  return !!user && user.email_verified === true && String(user.email || "").toLowerCase() === a;
}

// Email dianggap terverifikasi jika: Google/Firebase sudah menandai, ATAU lolos OTP buatan kita.
async function isVerified(user, env) {
  if (user.email_verified === true) return true;
  return !!(await env.JOBS.get("otpok:" + user.sub));
}

function randomCode(len) {
  const n = CODE_ALPHABET.length, lim = 256 - (256 % n);
  let out = "";
  while (out.length < len) {
    for (const v of crypto.getRandomValues(new Uint8Array(len * 2))) {
      if (v < lim && out.length < len) out += CODE_ALPHABET[v % n];
    }
  }
  return out;
}

async function codeHash(code) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(code));
  return [...new Uint8Array(d)].map(b => b.toString(16).padStart(2, "0")).join("");
}

// Versi aplikasi: 1 sampai 3 angka (maks 3 digit), contoh 1, 1.2, 1.2.3. Dinormalkan jadi x.y.z; semua nol ditolak.
function normVersion(s) {
  s = String(s || "").trim();
  if (!/^\d{1,3}(\.\d{1,3}){0,2}$/.test(s)) return null;
  const p = s.split(".").map(n => String(Number(n)));
  while (p.length < 3) p.push("0");
  return p.every(n => n === "0") ? null : p.join(".");
}

const semGe = (a, b) => { const x = a.split(".").map(Number), y = b.split(".").map(Number); for (let i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0); } return true; };
const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
async function jobOf(env, id) { try { const jk = await env.JOBS.get("jobref:" + id); return jk ? JSON.parse((await env.JOBS.get(jk)) || "null") : null; } catch { return null; } }
async function getCfg(env) { try { return JSON.parse((await env.JOBS.get("cfg:global")) || "{}") || {}; } catch { return {}; } }

function vtBadge(j) {
  const v = j.vt;
  if (!v || !v.sha) return '<p style="margin:8px 0">🔎 Belum dipindai VirusTotal</p>';
  const link = "https://www.virustotal.com/gui/file/" + esc(v.sha);
  const ok = v.m === 0 && v.s === 0;
  return '<p style="margin:8px 0;padding:8px;border-radius:10px;background:' + (ok ? "#dff6e5" : "#fde2e2") + ';color:#1c2230">' + (ok ? "✅ Bersih" : "⚠️ Ada yang mendeteksi") + " · " + (v.m + v.s) + " dari " + v.t + ' mesin VirusTotal · <a href="' + link + '" rel="noopener" target="_blank">laporan</a></p>';
}

async function downloadPage(env, id) {
  const j = await jobOf(env, id);
  const dead = !j || j.status !== "done" || !j.apkUrl || j.purged || j.blocked || (await env.JOBS.get("kill:" + id));
  const h = { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff", "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline' https://cdnjs.cloudflare.com; img-src data:; connect-src 'self'" };
  const head = '<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Unduh aplikasi</title><style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:system-ui,sans-serif;background:#f4f6fb;color:#1c2230;padding:16px;box-sizing:border-box}@media(prefers-color-scheme:dark){body{background:#0f1218;color:#e8ebf2}.c{background:#1a1f2b!important}}.c{background:#fff;border-radius:20px;padding:24px;max-width:420px;width:100%;box-shadow:0 8px 30px #0002;text-align:center}.i{width:88px;height:88px;border-radius:22px;margin:0 auto 12px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:44px;font-weight:800}h1{margin:0;font-size:1.4rem}p,li{color:#667085;font-size:.9rem;line-height:1.5}ol{text-align:left;padding-left:20px}a.b{display:block;margin:16px 0;padding:14px;border-radius:14px;background:linear-gradient(90deg,#4f6df5,#8a5cf6);color:#fff;font-weight:700;text-decoration:none}button{border:0;background:none;color:#667085;text-decoration:underline;cursor:pointer;font-size:.8rem}</style></head><body><div class="c">';
  if (dead) return new Response(head + "<h1>Aplikasi tidak tersedia</h1><p>Aplikasi ini sudah dihapus atau diblokir.</p></div></body></html>", { status: 404, headers: h });
  let hue = 0; for (const ch of j.name) hue = (hue * 31 + ch.codePointAt(0)) % 360;
  const exe = j.target === "exe";
  const steps = exe
    ? "<li>Unduh lalu jalankan installer.</li><li>Jika muncul peringatan Windows (SmartScreen): klik <b>Info lebih lanjut</b>, lalu <b>Tetap jalankan</b>.</li>"
    : "<li>Unduh file, lalu buka.</li><li>Jika diminta, aktifkan <b>Pasang dari sumber tidak dikenal</b> untuk browser/Files kamu.</li><li>Ketuk <b>Pasang</b>.</li>";
  const body = '<div class="i" style="background:hsl(' + hue + ',60%,42%)">' + esc([...j.name][0].toUpperCase()) + "</div><h1>" + esc(j.name) + "</h1>" + vtBadge(j) + "<p>Versi " + esc(j.version) + (exe ? " · Windows (EXE)" : " · Android (APK)") + '</p><a class="b" href="' + esc(j.apkUrl) + '" rel="noopener">⬇ Unduh</a><ol>' + steps + '</ol><p>Aplikasi dibuat oleh pengguna dan tidak kami periksa. Pakai dengan risiko sendiri.</p><div id="qr" style="display:flex;justify-content:center;margin:12px 0"></div><p style="margin:0 0 8px">Pindai dengan HP untuk membuka halaman ini.</p><button id="r">🚩 Laporkan aplikasi</button>'
    + '<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script><script>try{new QRCode(document.getElementById("qr"),{text:location.href,width:160,height:160})}catch(e){document.getElementById("qr").style.display="none"}</script>'
    + '<script>document.getElementById("r").onclick=async function(){var t=prompt("Alasan melaporkan aplikasi ini:");if(t===null)return;try{var r=await fetch("/api/report",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({jobId:"' + id + '",reason:t})});var d=await r.json();alert(r.ok?"Terima kasih, laporan terkirim.":d.error)}catch(e){alert("Gagal mengirim.")}}</script></div></body></html>';
  return new Response(head + body, { headers: h });
}

function checkAppId(s) {
  if (!s || s.length > 60 || !APPID_RE.test(s)) return false;
  return !s.split(".").some(p => RESERVED.has(p));
}

// ====================================================================
// Signaling WebRTC untuk fitur remote-control (RemoteControlModule.kt)
// Durable Object: satu instance = satu "room" (dipasangkan via roomId).
// Worker ini cuma meneruskan pesan offer/answer/candidate antar 2
// device yang roomId-nya sama; video/data WebRTC jalan langsung
// peer-to-peer, tidak lewat sini.
// ====================================================================
export class RoomSignaling {
  constructor(state, env) {
    this.state = state;
    this.sockets = new Map();
    this.nextId = 1;
  }

  async fetch(request) {
    const upgradeHeader = request.headers.get("Upgrade");
    if (!upgradeHeader || upgradeHeader !== "websocket") {
      return new Response("Endpoint ini hanya menerima koneksi WebSocket.", { status: 400 });
    }
    if (this.sockets.size >= 2) {
      return new Response("Room sudah penuh (maksimal 2 device: host + viewer).", { status: 409 });
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.handleSession(server);
    return new Response(null, { status: 101, webSocket: client });
  }

  handleSession(socket) {
    socket.accept();
    const id = this.nextId++;
    this.sockets.set(id, socket);
    socket.addEventListener("message", (event) => {
      for (const [otherId, otherSocket] of this.sockets.entries()) {
        if (otherId !== id) {
          try { otherSocket.send(event.data); } catch {}
        }
      }
    });
    const cleanup = () => { this.sockets.delete(id); };
    socket.addEventListener("close", cleanup);
    socket.addEventListener("error", cleanup);
  }
}

async function handleSignal(request, env) {
  const url = new URL(request.url);
  const roomId = url.searchParams.get("room");
  if (!roomId || roomId.length < 1 || roomId.length > 128) {
    return new Response("Parameter 'room' wajib diisi (1-128 karakter).", { status: 400 });
  }
  const objId = env.ROOMS.idFromName(roomId);
  const roomObj = env.ROOMS.get(objId);
  return roomObj.fetch(request);
}

export default {
  async fetch(request, env) {
    // Jalur WebSocket signaling: ditangani terpisah SEBELUM wrapper
    // CORS/JSON di bawah, karena respons upgrade WebSocket (status 101)
    // tidak boleh dibungkus ulang lewat `new Response(res.body, ...)`.
    if (new URL(request.url).pathname === "/signal") {
      return handleSignal(request, env);
    }

    const cors = {
      "Access-Control-Allow-Origin": env.ALLOWED_ORIGIN,
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Vary": "Origin",
    };
    if (new URL(request.url).pathname.startsWith("/api/app-status/")) cors["Access-Control-Allow-Origin"] = "*";
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    try {
      const res = await route(request, env);
      const h = new Headers(res.headers);
      for (const [k, v] of Object.entries(cors)) h.set(k, v);
      return new Response(res.body, { status: res.status, headers: h });
    } catch (e) {
      console.error("Worker error:", e && e.stack || e);
      return json({ error: "Kesalahan server." }, 500, cors);
    }
  },
};

async function route(request, env) {
  const url = new URL(request.url);
  const p = url.pathname;
  const m = request.method;

  if (m === "POST" && p === "/api/build") return handleBuild(request, env);
  if (m === "GET" && p === "/api/me") {
    const u = await verifyFirebase(request, env);
    if (!u) return json({ error: "Silakan masuk dulu." }, 401, { "Cache-Control": "no-store" });
    return json({ admin: isAdmin(u, env) }, 200, { "Cache-Control": "no-store" });
  }
  if (m === "POST" && p === "/api/admin/codes") return handleAdminCodes(request, env);
  if (m === "GET" && p.startsWith("/api/app-status/")) {
    const id = p.slice("/api/app-status/".length);
    const ns = { "Cache-Control": "no-store" };
    if (!ID_RE.test(id)) return json({ blocked: false }, 200, ns);
    const j = await jobOf(env, id);
    let blocked = !!(await env.JOBS.get("kill:" + id));
    if (!blocked) { // akun pemilik diblokir -> aplikasinya ikut mati
      const owner = await env.JOBS.get("owner:" + id);
      if (owner) blocked = !!(await env.JOBS.get("ban:" + owner));
    }
    if (!blocked && j && (j.blocked || j.status === "blocked")) blocked = true; // data lama
    const out = { blocked };
    if (url.searchParams.get("o") === "1" && !blocked && j && !env.STATS_OFF) { // hitung "dibuka" per hari
      try { const sk = "st:" + new Date().toISOString().slice(0, 10) + ":" + id; await env.JOBS.put(sk, String(Number((await env.JOBS.get(sk)) || 0) + 1), { expirationTtl: 8 * 86400 }); } catch {}
    }
    if (!blocked) {
      const c = await getCfg(env);
      if (c.msg) { out.message = c.msg; out.mid = String(c.at || 1); }
      let u = null; try { u = JSON.parse((await env.JOBS.get("upd:" + id)) || "null"); } catch {}
      if (u && j && u.min && !semGe(j.version || "0.0.0", u.min)) out.force = u.url;
    }
    return json(out, 200, ns);
  }
  if (m === "POST" && p === "/api/report") {
    const b = await request.json().catch(() => ({}));
    const id = String(b.jobId || "");
    if (!ID_RE.test(id)) return json({ error: "Aplikasi tidak valid." }, 400);
    const ip = request.headers.get("CF-Connecting-IP") || "unknown";
    const rk = "repip:" + ip + ":" + new Date().toISOString().slice(0, 13);
    const n = Number((await env.JOBS.get(rk)) || 0);
    if (n >= 5) return json({ error: "Terlalu banyak laporan. Coba lagi nanti." }, 429);
    await env.JOBS.put(rk, String(n + 1), { expirationTtl: 3600 });
    const at = Date.now();
    await env.JOBS.put("rep:" + String(9999999999999 - at).padStart(13, "0") + ":" + id, JSON.stringify({ jobId: id, reason: String(b.reason || "").slice(0, 300), at }), { expirationTtl: JOB_TTL });
    return json({ ok: true });
  }
  if (m === "POST" && p === "/api/cancel") { // batalkan build yang sedang berjalan
    const user = await verifyFirebase(request, env);
    if (!user) return json({ error: "Silakan masuk dulu." }, 401);
    const b = await request.json().catch(() => ({}));
    const id = String(b.jobId || "");
    if (!ID_RE.test(id)) return json({ error: "Job ID tidak valid." }, 400);
    if ((await env.JOBS.get("owner:" + id)) !== user.sub && !isAdmin(user, env)) return json({ error: "Ini bukan pembuatanmu." }, 403);
    const j = await jobOf(env, id);
    if (!j) return json({ error: "Pekerjaan tidak ditemukan." }, 404);
    let cur = {}; try { cur = JSON.parse((await env.JOBS.get("status:" + id)) || "{}"); } catch {}
    if (["done", "failed", "cancelled"].includes(cur.status) || ["done", "failed"].includes(j.status)) return json({ error: "Pembuatan sudah selesai atau sudah dibatalkan." }, 409);
    await env.JOBS.put("cancel:" + id, "1", { expirationTtl: 86400 });
    await env.JOBS.put("status:" + id, JSON.stringify({ status: "cancelled" }), { expirationTtl: STATUS_TTL });
    try { const jk = await env.JOBS.get("jobref:" + id); if (jk) await env.JOBS.put(jk, JSON.stringify({ ...j, status: "cancelled" }), { expirationTtl: JOB_TTL }); } catch {}
    await env.JOBS.delete("file:" + id);
    await env.JOBS.delete("icon:" + id);
    let stopped = false;
    try { stopped = await cancelRun(env, id, j.target); } catch (e) { console.error("Batalkan run gagal:", e && e.message || e); }
    return json({ ok: true, stopped }, 200, { "Cache-Control": "no-store" });
  }
  if (p.startsWith("/api/auth/")) return handleAuth(request, env, p, m);
  if (p.startsWith("/api/admin/")) return handleAdmin(request, env, p, m, url);

  let x;
  if (m === "GET" && (x = p.match(/^\/d\/([0-9a-f-]{36})$/)) && ID_RE.test(x[1])) return downloadPage(env, x[1]);
  if (m === "GET" && (x = p.match(/^\/api\/status\/([0-9a-f-]{36})$/)) && ID_RE.test(x[1])) {
    const s = await env.JOBS.get("status:" + x[1]);
    if (!s) return json({ error: "Pekerjaan tidak ditemukan." }, 404);
    return new Response(s, { headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
  }

  // ---- endpoint internal (hanya untuk GitHub Actions, wajib rahasia) ----
  if (p.startsWith("/internal/")) {
    if (!authOk(request, env)) return json({ error: "Tidak diizinkan." }, 401);

    if (m === "POST" && (x = p.match(/^\/internal\/status\/([0-9a-f-]{36})$/)) && ID_RE.test(x[1])) {
      let b;
      try { b = await request.json(); } catch { return json({ error: "JSON salah." }, 400); }
      if (!b || !["building", "done", "failed"].includes(b.status)) return json({ error: "Status salah." }, 400);
      if (await env.JOBS.get("cancel:" + x[1])) return json({ error: "Dibatalkan." }, 409); // build dibatalkan: hentikan workflow
      let j0 = null;
      try { // jika admin sudah memilih "hapus file permanen" saat build berjalan, hapus rilis begitu selesai
        const jk0 = await env.JOBS.get("jobref:" + x[1]);
        j0 = jk0 ? JSON.parse((await env.JOBS.get(jk0)) || "null") : null;
        if (j0 && j0.purge) {
          if (b.status === "done") await deleteRelease(env, x[1]).catch(() => {});
          return json({ ok: true });
        }
      } catch {}
      const out = { status: b.status };
      const tg = (j0 && j0.target) || "apk"; // perkiraan waktu = rata-rata build sebelumnya
      if (b.status === "building") out.eta = Number(await env.JOBS.get("avg:" + tg)) || 0;
      if (b.status === "done" && j0 && j0.createdAt) {
        const ms = Date.now() - j0.createdAt, old = Number(await env.JOBS.get("avg:" + tg)) || 0;
        if (ms > 30000 && ms < 3600000) await env.JOBS.put("avg:" + tg, String(Math.round(old ? old * 0.7 + ms * 0.3 : ms)));
      }

      // progres (hanya saat building): angka 0-100 dan nama tahap
      if (b.status === "building") {
        const pr = Number(b.progress);
        if (Number.isFinite(pr)) out.progress = Math.max(0, Math.min(100, Math.round(pr)));
        if (typeof b.step === "string") out.step = b.step.slice(0, 60);
      }

      const prefix = `https://github.com/${env.GITHUB_REPO}/releases/download/`;
      if (b.status === "done" && typeof b.apkUrl === "string" && b.apkUrl.startsWith(prefix)) out.apkUrl = b.apkUrl;
      if (b.status === "done" && b.vt && typeof b.vt === "object") {
        const n = v => Math.max(0, Math.min(1000, Math.floor(Number(v)) || 0));
        if (/^[0-9a-f]{64}$/.test(String(b.vt.sha || ""))) out.vt = { m: n(b.vt.m), s: n(b.vt.s), t: n(b.vt.t), sha: b.vt.sha };
      }
      if (b.status === "failed" && typeof b.runUrl === "string" && b.runUrl.startsWith("https://github.com/")) out.runUrl = b.runUrl;
      await env.JOBS.put("status:" + x[1], JSON.stringify(out), { expirationTtl: STATUS_TTL });
      if (b.status === "done" || b.status === "failed") {
        try {
          const jk = await env.JOBS.get("jobref:" + x[1]);
          const j = jk ? JSON.parse((await env.JOBS.get(jk)) || "null") : null;
          if (j) {
            j.status = b.status;
            j.apkUrl = out.apkUrl || "";
            j.runUrl = out.runUrl || "";
            if (out.vt) j.vt = out.vt;
            await env.JOBS.put(jk, JSON.stringify(j), { expirationTtl: JOB_TTL });
          }
        } catch (e) { console.error("Gagal memperbarui catatan build:", e); }
      }
      return json({ ok: true });
    }

    if (m === "GET" && (x = p.match(/^\/internal\/(file|icon|mp4)\/([0-9a-f-]{36})$/)) && ID_RE.test(x[2])) {
      const data = await env.JOBS.get(x[1] + ":" + x[2], "arrayBuffer");
      if (!data) return json({ error: "Tidak ada." }, 404);
      return new Response(data, { headers: { "Content-Type": "application/octet-stream" } });
    }

    if (m === "DELETE" && (x = p.match(/^\/internal\/file\/([0-9a-f-]{36})$/)) && ID_RE.test(x[1])) {
      await env.JOBS.delete("file:" + x[1]);
      await env.JOBS.delete("icon:" + x[1]);
      await env.JOBS.delete("mp4:" + x[1]);
      return json({ ok: true });
    }
  }

  return json({ error: "Tidak ditemukan." }, 404);
}

// Memeriksa ID token Firebase (RS256) dan mengembalikan payload token, atau null jika tidak sah.
async function verifyFirebase(request, env) {
  const m = (request.headers.get("Authorization") || "").match(/^Bearer (.+)$/);
  const pid = env.FIREBASE_PROJECT_ID;
  if (!m || !pid) return null;
  const parts = m[1].split(".");
  if (parts.length !== 3) return null;
  const b64 = s => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64(parts[1])));
  } catch { return null; }
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== "RS256" || !header.kid) return null;
  if (payload.aud !== pid || payload.iss !== "https://securetoken.google.com/" + pid) return null;
  if (!payload.sub || !(payload.exp > now) || payload.iat > now + 300) return null;
  const r = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com", { cf: { cacheTtl: 3600, cacheEverything: true } });
  if (!r.ok) return null;
  const jwk = ((await r.json()).keys || []).find(k => k.kid === header.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64(parts[2]), new TextEncoder().encode(parts[0] + "." + parts[1]));
  return ok ? payload : null;
}

async function handleAdminCodes(request, env) {
  const user = await verifyFirebase(request, env);
  if (!user) return json({ error: "Silakan masuk dulu." }, 401);
  if (!isAdmin(user, env)) return json({ error: "Tidak diizinkan." }, 403);
  let b;
  try { b = await request.json(); } catch { return json({ error: "JSON salah." }, 400); }
  const int = (v, d, lo, hi) => { const n = Math.floor(Number(v)); return Number.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : d; };
  const count = int(b.count, 1, 1, 50);
  const uses = int(b.uses, 1, 1, 1000);
  const days = int(b.days, 0, 0, 365);
  const len = int(b.length, 100, 32, 200);
  const now = Math.floor(Date.now() / 1000);
  const exp = days ? now + days * 86400 : 0;
  const codes = [];
  for (let i = 0; i < count; i++) {
    const c = randomCode(len);
    await env.JOBS.put("code:" + await codeHash(c), JSON.stringify({ uses, exp }), exp ? { expirationTtl: exp - now } : {});
    codes.push(c);
  }
  return json({ codes, uses, days }, 200, { "Cache-Control": "no-store" });
}

async function handleBuild(request, env) {
  const user = await verifyFirebase(request, env);
  if (!user) return json({ error: "Silakan masuk dulu." }, 401);
  const admin = isAdmin(user, env);
  if (!admin && (await env.JOBS.get("ban:" + user.sub))) return json({ error: "Akun kamu diblokir oleh admin." }, 403);
  if (!admin && !(await isVerified(user, env))) return json({ error: "Email belum diverifikasi. Masukkan kode OTP dari email.", code: "unverified" }, 403);
  const len = Number(request.headers.get("Content-Length") || 0);
  if (len > MAX_FILE + MAX_ICON + MAX_MP4 + 200000) return json({ error: "Ukuran total terlalu besar." }, 413);
  if (!(request.headers.get("Content-Type") || "").includes("multipart/form-data"))
    return json({ error: "Format permintaan salah." }, 400);

  const form = await request.formData();
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";

  // 1. Validasi teks (murah, dilakukan sebelum Turnstile)
  const mode = String(form.get("mode") || "");
  const appName = String(form.get("appName") || "").trim();
  const appId = String(form.get("appId") || "").trim();
  const version = normVersion(form.get("version"));
  if (!version) return json({ error: "Versi aplikasi wajib diisi, contoh: 1.0.0 (angka dipisah titik, minimal bukan 0)." }, 400);
  const target = String(form.get("target") || "apk");
  if (target !== "apk" && target !== "exe") return json({ error: "Target salah." }, 400);
  if (mode !== "url" && mode !== "upload" && mode !== "mp4") return json({ error: "Mode salah." }, 400);
  // Mode "mp4" (popup video saat aplikasi dibuka): sementara khusus admin dan hanya untuk target EXE.
  if (mode === "mp4" && (!admin || target !== "exe")) return json({ error: "Mode video ini khusus admin dan hanya untuk target EXE." }, 403);
  if (!NAME_RE.test(appName)) return json({ error: "Nama aplikasi 2–30 karakter (huruf, angka, spasi, titik, garis)." }, 400);
  if (target === "apk" && !checkAppId(appId)) return json({ error: "Package ID tidak valid. Contoh: com.budi.toko" }, 400);
  const permAll = [...new Set(String(form.get("perms") || "").split(",").map(x => x.trim()).filter(Boolean))];
  if (permAll.some(x => !PERMS_OK.has(x))) return json({ error: "Izin tidak dikenal." }, 400);
  // APK: semua izin yang dipilih. EXE: hanya "island" (modul Windows: Dynamic Island + animasi jendela), khusus admin.
  const permList = target === "apk" ? permAll : permAll.filter(x => x === "island");
  if (!admin && permList.some(x => ADMIN_ONLY_PERMS.has(x))) return json({ error: "Izin ini hanya tersedia untuk admin." }, 403);
  const perms = permList.join(",");
  const secure = target === "apk" && String(form.get("secure") || "") === "true";
  const lock = target === "apk" && String(form.get("lock") || "") === "true";
  const tray = target === "exe" && String(form.get("tray") || "") === "true";
  const orient = target === "apk" ? String(form.get("orient") || "auto") : "auto";
  if (!ORIENT_OK.has(orient)) return json({ error: "Orientasi layar tidak dikenal." }, 400);

  const code = String(form.get("code") || "").trim();
  if (!admin && (!code || code.length > 200)) return json({ error: "Masukkan kode verifikasi." }, 400);

  let siteUrl = "";
  let fileBuf = null;
  if (mode === "url") {
    siteUrl = checkUrl(String(form.get("url") || ""));
    if (!siteUrl) return json({ error: "URL harus https dan berupa nama domain yang valid." }, 400);
  } else if (mode === "mp4") {
    const file = form.get("file");
    if (!(file instanceof File)) return json({ error: "File video belum dipilih." }, 400);
    if (file.size === 0 || file.size > MAX_FILE) return json({ error: "Ukuran video maksimal 24 MB." }, 400);
    fileBuf = await file.arrayBuffer();
    // Kotak "ftyp" dari format MP4/MOV (ISO base media) selalu ada di offset byte ke-4.
    const box = new Uint8Array(fileBuf.slice(4, 8));
    const isMp4 = box.length === 4 && box[0] === 0x66 && box[1] === 0x74 && box[2] === 0x79 && box[3] === 0x70;
    if (!isMp4) return json({ error: "File bukan video MP4 yang valid." }, 400);
  } else {
    const file = form.get("file");
    if (!(file instanceof File)) return json({ error: "File belum dipilih." }, 400);
    if (file.size === 0 || file.size > MAX_FILE) return json({ error: "Ukuran file maksimal 24 MB." }, 400);
    fileBuf = await file.arrayBuffer();
    const head = new Uint8Array(fileBuf.slice(0, 4));
    const isZip = head[0] === 0x50 && head[1] === 0x4b && head[2] === 3 && head[3] === 4;
    const n = (file.name || "").toLowerCase();
    if (n.endsWith(".zip")) {
      if (!isZip) return json({ error: "File bukan ZIP yang valid." }, 400);
    } else if (n.endsWith(".html") || n.endsWith(".htm")) {
      if (isZip) return json({ error: "Isi file adalah ZIP, ganti ekstensi menjadi .zip." }, 400);
      if (new Uint8Array(fileBuf.slice(0, 4096)).includes(0)) return json({ error: "File bukan HTML teks." }, 400);
    } else {
      return json({ error: "Hanya .html, .htm, atau .zip." }, 400);
    }
    // Halaman pengaturan Windows (dynamic-island-windows.html) menandai dirinya; hanya admin yang boleh mengaktifkan modul Windows.
    if (target === "exe" && admin && !isZip && !permList.includes("island")
        && new TextDecoder().decode(new Uint8Array(fileBuf.slice(0, 300000))).includes('name="win-island"')) permList.push("island");
  }

  let iconBuf = null;
  const icon = form.get("icon");
  if (icon instanceof File && icon.size > 0) {
    if (icon.size > MAX_ICON) return json({ error: "Ikon maksimal 1 MB." }, 400);
    iconBuf = await icon.arrayBuffer();
    const h = new Uint8Array(iconBuf.slice(0, 4));
    const png = h[0] === 0x89 && h[1] === 0x50 && h[2] === 0x4e && h[3] === 0x47;
    const jpg = h[0] === 0xff && h[1] === 0xd8 && h[2] === 0xff;
    if (!png && !jpg) return json({ error: "Ikon harus PNG atau JPG." }, 400);
  }

  // Video popup saat EXE dibuka (khusus admin): file mp4 + pengaturan posisi/kecepatan/volume.
  // Semua angka divalidasi ulang di sini juga, jangan percaya begitu saja apa yang dikirim client.
  let mp4Buf = null, popupCfgStr = "";
  if (target === "exe" && admin) {
    const mp4 = form.get("mp4");
    if (mp4 instanceof File && mp4.size > 0) {
      if (mp4.size > MAX_MP4) return json({ error: "Video popup maksimal 40 MB." }, 400);
      mp4Buf = await mp4.arrayBuffer();
      const box = new Uint8Array(mp4Buf.slice(4, 8));
      const isMp4 = box.length === 4 && box[0] === 0x66 && box[1] === 0x74 && box[2] === 0x79 && box[3] === 0x70;
      if (!isMp4) return json({ error: "File video popup bukan MP4 yang valid." }, 400);
      let pc = {};
      try { pc = JSON.parse(String(form.get("popupCfg") || "{}")) || {}; } catch { pc = {}; }
      const count = Math.max(1, Math.min(50, Math.floor(Number(pc.count)) || 1));
      const gapMs = Math.max(0, Math.min(3600000, Math.floor(Number(pc.gapMs)) || 0));
      const move = pc.move === true;
      // piksel per detik, sama seperti label di UI ("Kecepatan gerak (piksel/detik)")
      const speed = Math.max(10, Math.min(2000, Math.floor(Number(pc.speed)) || 150));
      // pecahan 0-1 (bukan 0-100), sama seperti yang dikirim UI
      let volume = Number(pc.volume);
      if (!Number.isFinite(volume)) volume = 1;
      volume = Math.max(0, Math.min(1, volume));
      // berapa kali video DIPUTAR ULANG dalam satu kemunculan popup (1-20), sama seperti yang dikirim UI "popupLoop"
      const loop = Math.max(1, Math.min(20, Math.floor(Number(pc.loop)) || 1));
      // teks besar kedap-kedip di lapisan paling depan (di atas video), opsional - kosongkan = tidak ada teks
      const text = String(pc.text || "").slice(0, 60);
      // kata sandi kustom untuk menutup paksa semua jendela popup + keluar aplikasi (diketik di mana saja di aplikasi)
      const closePwd = String(pc.closePwd || "").slice(0, 64);
      popupCfgStr = JSON.stringify({ count, gapMs, move, speed, volume, loop, text, closePwd });
    }
  }

  // 2. Turnstile
  const token = form.get("cf-turnstile-response");
  if (typeof token !== "string" || !token) return json({ error: "Verifikasi belum selesai." }, 400);
  const vr = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
    method: "POST",
    body: new URLSearchParams({ secret: env.TURNSTILE_SECRET, response: token, remoteip: ip }),
  });
  const vj = await vr.json();
  if (!vj.success) return json({ error: "Verifikasi gagal. Coba lagi." }, 403);

  // 3. Batas per IP per hari (opsional; aktif hanya jika env.DAILY_LIMIT > 0)
  const limit = Number(env.DAILY_LIMIT || 0);
  let rlKey = null, used = 0;
  if (limit > 0) {
    rlKey = `rl:${ip}:${new Date().toISOString().slice(0, 10)}`;
    used = Number((await env.JOBS.get(rlKey)) || 0);
    if (used >= limit) return json({ error: `Batas ${limit} build per hari tercapai. Coba lagi besok.` }, 429);
  }

  // 3b. Kode verifikasi (sekali/beberapa kali pakai, hanya hash yang disimpan)
  let codeKey = "", rec = null;
  const putRec = async r => {
    if (!codeKey || !r) return;
    if (r.uses <= 0) await env.JOBS.delete(codeKey);
    else await env.JOBS.put(codeKey, JSON.stringify(r), r.exp ? { expirationTtl: Math.max(60, r.exp - Math.floor(Date.now() / 1000)) } : {});
  };
  if (!admin) {
    codeKey = "code:" + await codeHash(code);
    try { rec = JSON.parse((await env.JOBS.get(codeKey)) || "null"); } catch { rec = null; }
    if (!rec || !(rec.uses > 0)) return json({ error: "Kode verifikasi salah atau sudah habis dipakai." }, 403);
    await putRec({ uses: rec.uses - 1, exp: rec.exp });
  }

  // 4. Simpan sementara
  const jobId = crypto.randomUUID();
  const createdAt = Date.now();
  const jobKey = "job:" + String(9999999999999 - createdAt).padStart(13, "0") + ":" + jobId; // urut terbaru dulu
  const jobRec = {
    jobId, uid: user.sub, email: user.email || "", name: appName, version, target, mode,
    url: siteUrl, appId: target === "apk" ? appId : "", perms: permList, secure, lock, tray, orient,
    hasIcon: !!iconBuf, hasMp4: !!mp4Buf, createdAt, status: "queued", apkUrl: "", runUrl: "",
  };
  await env.JOBS.put(jobKey, JSON.stringify(jobRec), { expirationTtl: JOB_TTL });
  await env.JOBS.put("jobref:" + jobId, jobKey, { expirationTtl: JOB_TTL });
  await env.JOBS.put("owner:" + jobId, user.sub);
  if (fileBuf) await env.JOBS.put("file:" + jobId, fileBuf, { expirationTtl: FILE_TTL });
  if (iconBuf) await env.JOBS.put("icon:" + jobId, iconBuf, { expirationTtl: FILE_TTL });
  if (mp4Buf) await env.JOBS.put("mp4:" + jobId, mp4Buf, { expirationTtl: FILE_TTL });
  await env.JOBS.put("status:" + jobId, JSON.stringify({ status: "queued" }), { expirationTtl: STATUS_TTL });

  // 5. Picu GitHub Actions
  const fail = async (msg) => {
    await putRec(rec).catch(() => {});
    await env.JOBS.put("status:" + jobId, JSON.stringify({ status: "failed" }), { expirationTtl: STATUS_TTL });
    await env.JOBS.put(jobKey, JSON.stringify({ ...jobRec, status: "failed" }), { expirationTtl: JOB_TTL }).catch(() => {});
    await env.JOBS.delete("file:" + jobId);
    await env.JOBS.delete("icon:" + jobId);
    await env.JOBS.delete("mp4:" + jobId);
    return json({ error: msg }, 502);
  };

  const inputs = { job_id: jobId, mode, url: siteUrl, app_name: appName, version, has_icon: iconBuf ? "true" : "false" };
  if (target === "apk") { inputs.app_id = appId; inputs.perms = perms; inputs.secure = secure ? "true" : "false"; inputs.lock = lock ? "true" : "false"; inputs.orient = orient; }
  if (target === "exe") {
    inputs.tray = tray ? "true" : "false";
    inputs.fx = permList.includes("island") ? "true" : "false";
    inputs.has_mp4 = mp4Buf ? "true" : "false";
    if (mp4Buf) inputs.popup_cfg = popupCfgStr;
  }
  const wf = target === "exe" ? "build-exe.yml" : "build-apk.yml";

  let gh;
  try {
    gh = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/actions/workflows/${wf}/dispatches`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${env.GITHUB_TOKEN}`,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "apk-maker-worker",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        ref: env.GITHUB_BRANCH || "main",
        inputs,
      }),
    });
  } catch (e) {
    console.error("GitHub dispatch error:", e);
    return fail("Gagal menghubungi GitHub. Coba lagi.");
  }
  if (gh.status !== 204) {
    const detail = await gh.text().catch(() => "");
    console.error("GitHub dispatch status", gh.status, detail);
    let hint = "";
    if (gh.status === 401) hint = " Token salah/kedaluwarsa.";
    else if (gh.status === 403) hint = " Token tidak punya izin Actions: Read and write (atau Actions dinonaktifkan).";
    else if (gh.status === 404) hint = " Repo/nama workflow/branch tidak ditemukan atau token tidak punya akses ke repo.";
    else if (gh.status === 422) hint = " Workflow tidak punya trigger workflow_dispatch atau input tidak cocok.";
    return fail("Gagal memulai build (kode GitHub " + gh.status + ")." + hint);
  }

  // hitung hanya jika build benar-benar dimulai
  if (rlKey) await env.JOBS.put(rlKey, String(used + 1), { expirationTtl: 86400 });

  return json({ jobId });
}

// ---------- Panel admin: pantau build, blokir/buka blokir akun ----------
async function handleAdmin(request, env, p, m, url) {
  const user = await verifyFirebase(request, env);
  if (!user) return json({ error: "Silakan masuk dulu." }, 401);
  if (!isAdmin(user, env)) return json({ error: "Tidak diizinkan." }, 403);
  const noStore = { "Cache-Control": "no-store" };

  // Daftar build semua pengguna (terbaru dulu), 20 per halaman
  if (m === "GET" && p === "/api/admin/jobs") {
    const cursor = url.searchParams.get("cursor") || undefined;
    const l = await env.JOBS.list({ prefix: "job:", limit: 20, cursor });
    const jobs = [];
    for (const k of l.keys) {
      try {
        const j = JSON.parse((await env.JOBS.get(k.name)) || "null");
        if (j) jobs.push(j);
      } catch {}
    }
    const banned = {};
    for (const uid of new Set(jobs.map(j => j.uid))) banned[uid] = !!(await env.JOBS.get("ban:" + uid));
    jobs.forEach(j => { j.banned = !!banned[j.uid]; });
    return json({ jobs, cursor: l.list_complete ? "" : l.cursor }, 200, noStore);
  }

  // Daftar akun yang sedang diblokir
  if (m === "GET" && p === "/api/admin/bans") {
    const l = await env.JOBS.list({ prefix: "ban:", limit: 40 });
    const bans = [];
    for (const k of l.keys) {
      try {
        const r = JSON.parse((await env.JOBS.get(k.name)) || "null");
        if (r) bans.push({ uid: k.name.slice(4), email: r.email || "", reason: r.reason || "", at: r.at || 0 });
      } catch {}
    }
    return json({ bans }, 200, noStore);
  }

  if (m === "POST" && (p === "/api/admin/ban" || p === "/api/admin/unban")) {
    let b;
    try { b = await request.json(); } catch { return json({ error: "JSON salah." }, 400); }
    const uid = String((b && b.uid) || "");
    if (!UID_RE.test(uid)) return json({ error: "UID tidak valid." }, 400);
    if (p === "/api/admin/unban") {
      await env.JOBS.delete("ban:" + uid);
      return json({ ok: true }, 200, noStore);
    }
    const email = String(b.email || "").slice(0, 200);
    const adminMail = String(env.ADMIN_EMAIL || ADMIN_EMAIL).toLowerCase();
    if (uid === user.sub || email.toLowerCase() === adminMail) return json({ error: "Akun admin tidak bisa diblokir." }, 400);
    await env.JOBS.put("ban:" + uid, JSON.stringify({ email, reason: String(b.reason || "").slice(0, 200), at: Date.now() }));
    return json({ ok: true }, 200, noStore);
  }

  // Blokir aplikasi (kill switch, bisa dibuka lagi). Opsi purge = hapus juga file di GitHub (permanen).
  if (m === "POST" && (p === "/api/admin/takedown" || p === "/api/admin/untakedown")) {
    let b;
    try { b = await request.json(); } catch { return json({ error: "JSON salah." }, 400); }
    const jobId = String((b && b.jobId) || "");
    if (!ID_RE.test(jobId)) return json({ error: "Job ID tidak valid." }, 400);
    const jk = await env.JOBS.get("jobref:" + jobId);
    let j = null;
    try { j = jk ? JSON.parse((await env.JOBS.get(jk)) || "null") : null; } catch {}
    const save = async () => { if (j && jk) await env.JOBS.put(jk, JSON.stringify(j), { expirationTtl: JOB_TTL }); };

    if (p === "/api/admin/untakedown") {
      await env.JOBS.delete("kill:" + jobId);
      if (j) { j.blocked = false; if (j.status === "blocked") j.status = "done"; await save(); }
      return json({ ok: true }, 200, noStore);
    }

    await env.JOBS.put("kill:" + jobId, "1"); // permanen sampai admin membuka blokir
    if (j) { j.blocked = true; if (b.purge) j.purge = true; await save(); }
    if (b.purge) {
      let r;
      try { r = await deleteRelease(env, jobId); }
      catch (e) { return json({ error: "Aplikasi sudah diblokir, tetapi gagal menghapus file di GitHub: " + (e && e.message || e) }, 502); }
      if (j) { j.apkUrl = ""; j.purged = true; await save(); }
      await env.JOBS.delete("file:" + jobId);
      await env.JOBS.delete("icon:" + jobId);
      return json({ ok: true, release: r }, 200, noStore);
    }
    return json({ ok: true }, 200, noStore);
  }

  if (p === "/api/admin/config") { // pengumuman untuk semua aplikasi
    if (m === "GET") return json(await getCfg(env), 200, noStore);
    const b = await request.json().catch(() => ({}));
    const msg = String(b.message || "").trim().slice(0, 300);
    await env.JOBS.put("cfg:global", JSON.stringify({ msg, at: msg ? Date.now() : 0 }));
    return json({ ok: true }, 200, noStore);
  }
  if (m === "GET" && p === "/api/admin/stats") {
    const days = [];
    for (let i = 0; i < 7; i++) days.push(new Date(Date.now() - i * 86400000).toISOString().slice(0, 10));
    const agg = {};
    for (const d of days) {
      const l = await env.JOBS.list({ prefix: "st:" + d + ":", limit: 200 });
      for (const k of l.keys) {
        const id = k.name.slice(14), n = Number((await env.JOBS.get(k.name)) || 0);
        const a = agg[id] || (agg[id] = { today: 0, week: 0 });
        a.week += n; if (d === days[0]) a.today += n;
      }
    }
    const rows = [];
    for (const id of Object.keys(agg)) { const j = await jobOf(env, id); rows.push({ jobId: id, name: j ? j.name : "-", target: j ? j.target : "", today: agg[id].today, week: agg[id].week }); }
    rows.sort((a, b) => b.week - a.week);
    return json({ rows: rows.slice(0, 30), days: 7 }, 200, noStore);
  }
  if (m === "POST" && p === "/api/admin/forceupdate") { // paksa update per aplikasi
    const b = await request.json().catch(() => ({}));
    const jobId = String(b.jobId || "");
    if (!ID_RE.test(jobId)) return json({ error: "Job ID tidak valid." }, 400);
    if (!String(b.minVersion || "").trim()) { await env.JOBS.delete("upd:" + jobId); return json({ ok: true }, 200, noStore); }
    const nv = normVersion(b.minVersion), u = checkUrl(String(b.url || ""));
    if (!nv || !u) return json({ error: "Versi atau link (https) tidak valid." }, 400);
    await env.JOBS.put("upd:" + jobId, JSON.stringify({ min: nv, url: u }));
    return json({ ok: true }, 200, noStore);
  }
  if (m === "GET" && p === "/api/admin/reports") {
    const l = await env.JOBS.list({ prefix: "rep:", limit: 30 });
    const reports = [];
    for (const k of l.keys) { try { const r = JSON.parse((await env.JOBS.get(k.name)) || "null"); if (r) reports.push({ key: k.name, ...r }); } catch {} }
    return json({ reports }, 200, noStore);
  }
  if (m === "POST" && p === "/api/admin/delreport") {
    const b = await request.json().catch(() => ({}));
    const key = String(b.key || "");
    if (!/^rep:\d{13}:[0-9a-f-]{36}$/.test(key)) return json({ error: "Kunci tidak valid." }, 400);
    await env.JOBS.delete(key);
    return json({ ok: true }, 200, noStore);
  }

  return json({ error: "Tidak ditemukan." }, 404);
}

async function cancelRun(env, jobId, target) {
  const wf = target === "exe" ? "build-exe.yml" : "build-apk.yml";
  const h = {
    "Authorization": `Bearer ${env.GITHUB_TOKEN}`, "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "apk-maker-worker",
  };
  const base = `https://api.github.com/repos/${env.GITHUB_REPO}/actions`;
  const r = await fetch(`${base}/workflows/${wf}/runs?event=workflow_dispatch&per_page=30`, { headers: h });
  if (!r.ok) throw new Error("GitHub " + r.status);
  const live = ["queued", "in_progress", "waiting", "requested", "pending"];
  const run = ((await r.json()).workflow_runs || []).find(x => String(x.display_title || x.name || "").includes(jobId) && live.includes(x.status));
  if (!run) return false;
  const c = await fetch(`${base}/runs/${run.id}/cancel`, { method: "POST", headers: h });
  return c.status === 202;
}

async function deleteRelease(env, jobId) {
  const tag = "job-" + jobId;
  const h = {
    "Authorization": `Bearer ${env.GITHUB_TOKEN}`, "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "apk-maker-worker",
  };
  const base = `https://api.github.com/repos/${env.GITHUB_REPO}`;
  const r = await fetch(`${base}/releases/tags/${tag}`, { headers: h });
  if (r.status === 404) return "tidak ada";
  if (!r.ok) throw new Error("GitHub " + r.status + (r.status === 403 || r.status === 401 ? " (token butuh izin Contents: Read and write)" : ""));
  const rel = await r.json();
  const d = await fetch(`${base}/releases/${rel.id}`, { method: "DELETE", headers: h });
  if (d.status !== 204 && d.status !== 404) throw new Error("GitHub hapus rilis " + d.status + (d.status === 403 ? " (token butuh izin Contents: Read and write)" : ""));
  await fetch(`${base}/git/refs/tags/${tag}`, { method: "DELETE", headers: h }).catch(() => {});
  return "dihapus";
}

// ---------- Verifikasi email (OTP lewat Gmail API) ----------
const OTP_TTL = 600;        // kode berlaku 10 menit
const OTP_TRIES = 5;        // maksimal salah input
const OTP_IP_HOUR = 5;      // maksimal kirim kode per IP per jam
const SIGNUP_IP_DAY = 3;    // maksimal akun baru terverifikasi per IP per hari
const EMAIL_RE = /^[a-z0-9._%+-]{1,64}@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$/;

const normEmail = s => String(s || "").trim().toLowerCase();
function canonEmail(e) { // gmail: titik & +alias dianggap satu email
  let [l, d] = e.split("@");
  if (d === "googlemail.com") d = "gmail.com";
  if (d === "gmail.com") l = l.replace(/\./g, "").split("+")[0];
  return l + "@" + d;
}
function otpCode() {
  return String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
}
function utf8b64(s) {
  let bin = "";
  for (const b of new TextEncoder().encode(s)) bin += String.fromCharCode(b);
  return btoa(bin);
}

let gTok = { v: "", exp: 0 };
async function gmailToken(env) {
  if (gTok.v && gTok.exp > Date.now() + 60000) return gTok.v;
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    body: new URLSearchParams({
      client_id: env.GMAIL_CLIENT_ID || "", client_secret: env.GMAIL_CLIENT_SECRET || "",
      refresh_token: env.GMAIL_REFRESH_TOKEN || "", grant_type: "refresh_token",
    }),
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.access_token) throw new Error("Token Gmail gagal: " + r.status + " " + (j.error || ""));
  gTok = { v: j.access_token, exp: Date.now() + (j.expires_in || 3000) * 1000 };
  return gTok.v;
}

async function sendOtpMail(env, to, code) {
  const sender = env.GMAIL_SENDER;
  if (!sender || !env.GMAIL_REFRESH_TOKEN) throw new Error("Gmail belum dikonfigurasi");
  const body = `Kode verifikasi kamu: ${code}\r\n\r\nKode berlaku 10 menit. Jangan bagikan kode ini ke siapa pun.\r\nAbaikan email ini jika kamu tidak merasa mendaftar.`;
  const raw = [
    `From: Pembuat APK <${sender}>`,
    `To: ${to}`,
    `Subject: =?UTF-8?B?${utf8b64("Kode verifikasi: " + code)}?=`,
    "MIME-Version: 1.0",
    "Content-Type: text/plain; charset=UTF-8",
    "Content-Transfer-Encoding: base64",
    "",
    utf8b64(body).match(/.{1,76}/g).join("\r\n"),
  ].join("\r\n");
  const rawB64 = utf8b64(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const r = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", {
    method: "POST",
    headers: { "Authorization": "Bearer " + await gmailToken(env), "Content-Type": "application/json" },
    body: JSON.stringify({ raw: rawB64 }),
  });
  if (!r.ok) {
    if (r.status === 401) gTok = { v: "", exp: 0 };
    throw new Error("Gmail send gagal: " + r.status + " " + (await r.text().catch(() => "")));
  }
}

async function handleAuth(request, env, p, m) {
  const noStore = { "Cache-Control": "no-store" };
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";

  // status verifikasi akun yang sedang masuk
  if (m === "GET" && p === "/api/auth/status") {
    const user = await verifyFirebase(request, env);
    if (!user) return json({ error: "Silakan masuk dulu." }, 401);
    return json({ verified: await isVerified(user, env) }, 200, noStore);
  }
  if (m !== "POST") return json({ error: "Tidak ditemukan." }, 404);

  // selesai verifikasi: tandai akun (dipanggil setelah akun dibuat/masuk)
  if (p === "/api/auth/claim") {
    const user = await verifyFirebase(request, env);
    if (!user) return json({ error: "Silakan masuk dulu." }, 401);
    const email = normEmail(user.email);
    if (!email) return json({ error: "Akun tidak punya email." }, 400);
    const canon = canonEmail(email);
    if (!(await env.JOBS.get("emailok:" + canon))) return json({ error: "Verifikasi email dulu dengan kode OTP." }, 403);
    const owner = await env.JOBS.get("canon:" + canon);
    if (owner && owner !== user.sub) return json({ error: "Email ini sudah dipakai akun lain." }, 409);
    await env.JOBS.put("canon:" + canon, user.sub);
    await env.JOBS.put("otpok:" + user.sub, "1");
    await env.JOBS.delete("emailok:" + canon);
    return json({ ok: true }, 200, noStore);
  }

  let b;
  try { b = await request.json(); } catch { return json({ error: "JSON salah." }, 400); }
  const email = normEmail(b && b.email);
  if (email.length > 254 || !EMAIL_RE.test(email)) return json({ error: "Format email tidak valid." }, 400);
  const [local, domain] = email.split("@");
  if ((domain === "gmail.com" || domain === "googlemail.com") && local.includes("+"))
    return json({ error: "Email Gmail dengan tanda + tidak diperbolehkan." }, 400);
  const canon = canonEmail(email);

  // kirim kode OTP
  if (p === "/api/auth/send-otp") {
    const hour = new Date().toISOString().slice(0, 13);
    const ipKey = `otpip:${ip}:${hour}`;
    const n = Number((await env.JOBS.get(ipKey)) || 0);
    if (n >= OTP_IP_HOUR) return json({ error: "Terlalu banyak permintaan kode dari perangkat ini. Coba lagi nanti." }, 429);
    if (await env.JOBS.get("otpcd:" + canon)) return json({ error: "Kode baru saja dikirim. Tunggu 1 menit lalu kirim ulang." }, 429);
    const code = otpCode();
    await env.JOBS.put("otp:" + canon, JSON.stringify({ h: await codeHash(canon + ":" + code), tries: 0 }), { expirationTtl: OTP_TTL });
    await env.JOBS.put("otpcd:" + canon, "1", { expirationTtl: 60 });
    await env.JOBS.put(ipKey, String(n + 1), { expirationTtl: 3600 });
    try { await sendOtpMail(env, email, code); }
    catch (e) {
      console.error("Kirim OTP gagal:", e && e.message || e);
      await env.JOBS.delete("otp:" + canon);
      await env.JOBS.delete("otpcd:" + canon);
      return json({ error: "Gagal mengirim email. Coba lagi sebentar lagi." }, 502);
    }
    return json({ ok: true }, 200, noStore);
  }

  // periksa kode OTP
  if (p === "/api/auth/verify-otp") {
    const code = String((b && b.code) || "").trim();
    if (!/^\d{6}$/.test(code)) return json({ error: "Kode harus 6 digit angka." }, 400);
    const day = new Date().toISOString().slice(0, 10);
    const suKey = `su:${ip}:${day}`;
    const su = Number((await env.JOBS.get(suKey)) || 0);
    if (su >= SIGNUP_IP_DAY) return json({ error: "Batas pendaftaran akun per perangkat hari ini tercapai. Coba lagi besok." }, 429);
    let rec = null;
    try { rec = JSON.parse((await env.JOBS.get("otp:" + canon)) || "null"); } catch {}
    if (!rec) return json({ error: "Kode kedaluwarsa atau belum diminta. Kirim kode baru." }, 400);
    if (rec.tries >= OTP_TRIES) {
      await env.JOBS.delete("otp:" + canon);
      return json({ error: "Terlalu banyak salah. Kirim kode baru." }, 429);
    }
    if (rec.h !== await codeHash(canon + ":" + code)) {
      rec.tries++;
      await env.JOBS.put("otp:" + canon, JSON.stringify(rec), { expirationTtl: OTP_TTL });
      return json({ error: `Kode salah. Sisa percobaan: ${Math.max(0, OTP_TRIES - rec.tries)}.` }, 400);
    }
    await env.JOBS.delete("otp:" + canon);
    await env.JOBS.put("emailok:" + canon, "1", { expirationTtl: 900 });
    await env.JOBS.put(suKey, String(su + 1), { expirationTtl: 86400 });
    return json({ ok: true }, 200, noStore);
  }

  return json({ error: "Tidak ditemukan." }, 404);
}
