/* ===== Pembuat karakter chibi =====
   createChibi({ ...opsi }) -> THREE.Group. Sendi: p.userData.joints, animasi idle: p.userData.update(t) */
function createChibi(o = {}) {
  const C = Object.assign({ skin: 0xf2c4a2, hair: 0x5a3825, hairStyle: 'short', shirt: 0x2e8b8b, pants: 0x30394f, shoes: 0xd9483b, iris: 0x7a4a24,
    pack: null, hood: false, zip: null, sleeves: 'long', skirt: false, sock: 0xf5f2ec, girl: false, glasses: null, cap: null, catEars: false,
    ribbon: null, bow: null, scarf: null, ahoge: false, freckles: false, plaster: false,
    belt: null, coat: false, under: null, emblem: null, stripe: null, shorts: false, sailor: null }, o);
  const std = (c, r) => new THREE.MeshStandardMaterial({ color: c, roughness: r, metalness: 0 });
  const shade = (c, f) => new THREE.Color(c).multiplyScalar(f);
  const M = {
    dk: std(shade(C.shirt, 0.7), 0.9), pdk: std(shade(C.pants, 0.7), 0.9),
    skirtM: new THREE.MeshStandardMaterial({ color: C.pants, roughness: 0.88, flatShading: true }),
    skin: std(C.skin, 0.5), shirt: std(C.shirt, 0.92), pants: std(C.pants, 0.88), shoes: std(C.shoes, 0.55), sole: std(0xf5f2ec, 0.6),
    sock: std(C.sock, 0.9), white: std(0xffffff, 0.25), iris: std(C.iris, 0.3), dark: std(0x141012, 0.2), lip: std(0xa8524e, 0.5), gold: std(0xd9b24a, 0.3),
    hair: new THREE.MeshPhysicalMaterial({ color: C.hair, roughness: 0.45, clearcoat: 0.6, clearcoatRoughness: 0.4 }),
    blush: new THREE.MeshBasicMaterial({ color: 0xff8d8d, transparent: true, opacity: 0.42, polygonOffset: true, polygonOffsetFactor: -2, side: THREE.DoubleSide })
  };
  const place = (m, x, y, z, sx, sy, sz) => { m.position.set(x, y, z); m.scale.set(sx || 1, sy || 1, sz || 1); m.castShadow = m.receiveShadow = true; return m; };
  const sphere = (r, m, x = 0, y = 0, z = 0, sx, sy, sz) => place(new THREE.Mesh(new THREE.SphereGeometry(r, 32, 20), m), x, y, z, sx, sy, sz);
  const cone = (r, h, m, x, y, z, rx, rz) => { const c = place(new THREE.Mesh(new THREE.ConeGeometry(r, h, 14).translate(0, h / 2, 0), m), x, y, z); c.rotation.set(rx || 0, 0, rz || 0); return c; };
  const tail = (r, len, m, x, y, z, rx, rz) => { const c = place(new THREE.Mesh(new THREE.ConeGeometry(r, len, 16).rotateX(Math.PI).translate(0, -len / 2, 0), m), x, y, z); c.rotation.set(rx || 0, 0, rz || 0); return c; };
  const joint = (p, x, y, z) => { const g = new THREE.Group(); g.position.set(x, y, z); p.add(g); return g; };
  const limb = (r1, r2, len, m) => {
    const g = new THREE.Group();
    g.add(place(new THREE.Mesh(new THREE.CylinderGeometry(r1, r2, len, 24).translate(0, -len / 2, 0), m), 0, 0, 0));
    g.add(sphere(r1, m)); g.add(sphere(r2, m, 0, -len, 0));
    return g;
  };
  const ring = (r, t, m, y, sz = 0.85) => { const x = place(new THREE.Mesh(new THREE.TorusGeometry(r, t, 10, 40), m), 0, y, 0, 1, 1, sz); x.rotation.x = Math.PI / 2; return x; };
  const bowAt = (p, x, y, z, col, k = 1) => {
    const m = std(col, 0.6);
    p.add(sphere(0.022 * k, m, x, y, z));
    [-1, 1].forEach(s => { const w = sphere(0.04 * k, m, x + s * 0.04 * k, y, z, 1, 0.65, 0.45); w.rotation.z = s * 0.3; p.add(w); });
  };

  const player = new THREE.Group(), J = {};
  const hips = joint(player, 0, 0.385, 0); J.hips = hips;
  hips.add(sphere(0.125, C.skirt ? M.skirtM : M.pants, 0, 0, 0, 1, 0.75, 0.85));
  if (C.skirt) {
    hips.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.22, 0.17, 30).translate(0, -0.085, 0), M.skirtM), 0, 0, 0, 1, 1, 0.95));
    hips.add(ring(0.13, 0.012, M.pdk, 0, 0.95));
    const hem = place(new THREE.Mesh(new THREE.TorusGeometry(0.218, 0.01, 8, 48), M.sole), 0, -0.17, 0, 1, 1, 0.95); hem.rotation.x = Math.PI / 2; hips.add(hem);
  }

  // ---- Badan ----
  const spine = joint(hips, 0, 0.02, 0); J.spine = spine;
  spine.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.135, 0.125, 0.26, 32).translate(0, 0.13, 0), M.shirt), 0, 0, 0, 1, 1, 0.85));
  spine.add(sphere(0.135, M.shirt, 0, 0.26, 0, 1, 0.38, 0.85));
  spine.add(sphere(0.125, M.shirt, 0, 0.02, 0, 1, 0.55, 0.85));
  if (C.zip === 'zip') spine.add(place(new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.22, 0.01), M.sole), 0, 0.14, 0.113));
  if (C.zip === 'buttons') {
    [0.07, 0.14, 0.21].forEach(y => spine.add(sphere(0.014, M.gold, 0, y, 0.117)));
    const col = place(new THREE.Mesh(new THREE.TorusGeometry(0.06, 0.016, 10, 24), M.shirt), 0, 0.3, 0, 1, 1, 0.9); col.rotation.x = Math.PI / 2; spine.add(col);
  }
  if (C.hood) spine.add(sphere(0.095, M.shirt, 0, 0.275, -0.07, 1.15, 0.55, 0.7));
  if (C.pack) { spine.add(sphere(0.11, std(C.pack, 0.8), 0, 0.14, -0.13, 1, 1.15, 0.62)); spine.add(sphere(0.045, std(C.pack, 0.8), 0, 0.07, -0.19, 1.2, 0.7, 0.5)); }
  if (C.bow) bowAt(spine, 0, 0.27, 0.118, C.bow, 1.1);
  // ---- Detail pakaian ----
  spine.add(ring(0.129, 0.014, C.hood ? M.dk : M.shirt, 0.005));
  if (C.coat) {
    spine.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.128, 0.19, 0.11, 40).translate(0, -0.045, 0), M.shirt), 0, 0.02, 0, 1, 1, 0.9));
    spine.add(ring(0.19, 0.012, M.dk, -0.08, 0.9));
    [-1, 1].forEach(s => spine.add(place(new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.03, 0.012), M.dk), s * 0.09, -0.02, 0.118)));
  }
  if (C.hood) {
    spine.add(ring(0.078, 0.034, M.shirt, 0.3, 0.9));
    [-1, 1].forEach(s => { spine.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.004, 0.004, 0.1, 6), M.sole), s * 0.03, 0.23, 0.12)); spine.add(sphere(0.007, M.sole, s * 0.03, 0.18, 0.12)); });
  }
  if (!C.hood && !C.sailor && C.zip !== 'buttons') spine.add(ring(0.07, 0.014, M.dk, 0.3, 0.9));
  if (C.under) { const um = std(C.under, 0.7); spine.add(ring(0.082, 0.013, um, 0.312, 0.9)); spine.add(ring(0.131, 0.012, um, -0.012, 0.85)); }
  if (C.sailor) { spine.add(sphere(0.155, std(C.sailor, 0.85), 0, 0.268, -0.012, 1, 0.13, 0.85)); spine.add(ring(0.145, 0.004, M.white, 0.268, 0.85)); }
  if (C.emblem) { spine.add(sphere(0.045, std(C.emblem, 0.7), 0, 0.17, 0.105, 1, 1, 0.12)); spine.add(sphere(0.026, M.shirt, 0, 0.17, 0.108, 1, 1, 0.16)); }
  if (C.stripe) [0.12, 0.16].forEach(y => spine.add(ring(0.1315, 0.011, std(C.stripe, 0.8), y, 0.85)));
  if (C.belt) { spine.add(ring(0.1285, 0.016, std(C.belt, 0.6), 0.05, 0.85)); bowAt(spine, 0, 0.05, -0.11, C.belt, 1.3); }
  if (C.scarf) {
    const sm = std(C.scarf, 0.95), sc = place(new THREE.Mesh(new THREE.TorusGeometry(0.075, 0.034, 12, 28), sm), 0, 0.31, 0, 1, 1, 0.9);
    sc.rotation.x = Math.PI / 2; spine.add(sc); spine.add(sphere(0.04, sm, 0.06, 0.2, 0.105, 0.9, 2.6, 0.5));
  }

  // ---- Leher & kepala ----
  spine.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.07, 16), M.skin), 0, 0.31, 0.005));
  const head = joint(spine, 0, 0.55, 0); J.head = head;
  head.add(sphere(0.3, M.skin, 0, 0, 0, 1.05, 0.95, 1.0));
  head.add(sphere(0.2, M.skin, 0, -0.1, 0.05, 1.15, 0.8, 0.95));
  head.add(sphere(0.014, std(0xe8a08a, 0.5), 0, -0.065, 0.29));
  const smile = place(new THREE.Mesh(new THREE.TorusGeometry(0.03, 0.006, 8, 20, Math.PI), M.lip), 0, -0.1, 0.275); smile.rotation.z = Math.PI; head.add(smile);

  const eyes = [];
  [-1, 1].forEach(s => {
    const e = joint(head, s * 0.108, -0.025, 0.262); e.rotation.y = s * 0.28; eyes.push(e);
    e.add(sphere(0.066, M.white, 0, 0, 0, 1, 1.22, 0.4));
    e.add(sphere(0.052, M.iris, 0, -0.005, 0.014, 1, 1.18, 0.4));
    e.add(sphere(0.03, M.dark, 0, -0.005, 0.024, 1, 1.15, 0.4));
    e.add(sphere(0.014, M.white, 0.016, 0.026, 0.034));
    e.add(sphere(0.007, M.white, -0.015, -0.025, 0.034));
    const lid = place(new THREE.Mesh(new THREE.TorusGeometry(0.062, C.girl ? 0.01 : 0.007, 8, 24, Math.PI * 0.9), M.dark), 0, 0.012, 0.012, 1, 1.1, 0.5);
    lid.rotation.z = Math.PI * 0.05; e.add(lid);
    if (C.girl) { const l = sphere(0.012, M.dark, s * 0.066, 0.03, 0.014, 1, 2.4, 0.6); l.rotation.z = -s * 0.7; e.add(l); }
    const brow = sphere(0.04, M.hair, s * 0.108, 0.082, 0.265, 1, 0.22, 0.3); brow.rotation.z = -s * 0.12; head.add(brow);
    const bl = new THREE.Mesh(new THREE.CircleGeometry(0.036, 24), M.blush);
    bl.position.set(s * 0.18, -0.1, 0.228); bl.rotation.y = s * 0.7; bl.scale.set(1.3, 0.8, 1); head.add(bl);
    head.add(sphere(0.06, M.skin, s * 0.305, -0.02, 0, 0.35, 0.9, 0.65));
    if (C.freckles) [[0.15, -0.075], [0.185, -0.07], [0.165, -0.115]].forEach(([x, y]) => { const f = sphere(0.007, std(0x9a5a3a, 0.6), s * x, y, 0.238); f.rotation.y = s * 0.7; head.add(f); });
    if (C.glasses) {
      const gm = std(C.glasses, 0.35);
      head.add(place(new THREE.Mesh(new THREE.TorusGeometry(0.068, 0.006, 8, 32), gm), s * 0.108, -0.025, 0.296, 1, 1.12, 1));
      const arm = place(new THREE.Mesh(new THREE.BoxGeometry(0.006, 0.006, 0.2), gm), s * 0.185, -0.02, 0.2); arm.rotation.y = -s * 0.65; head.add(arm);
    }
  });
  if (C.glasses) head.add(place(new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.006, 0.006), std(C.glasses, 0.35)), 0, -0.01, 0.298));
  if (C.plaster) { const pm = std(0xf4efe2, 0.7); [0.6, -0.6].forEach(r => { const b = place(new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.016, 0.006), pm), 0.17, -0.1, 0.235); b.rotation.set(0, 0.7, r); head.add(b); }); }

  // ---- Rambut ----
  const H = M.hair, st = C.hairStyle;
  const dome = place(new THREE.Mesh(new THREE.SphereGeometry(0.318, 36, 22, 0, Math.PI * 2, 0, Math.PI * 0.58), H), 0, 0.01, -0.012, 1.06, 0.98, 1.03);
  dome.rotation.x = -0.32; head.add(dome);
  const back = (r, y, z, sx, sy, sz) => head.add(sphere(r, H, 0, y, z, sx, sy, sz));
  const sides = (y, sx, sy) => [-1, 1].forEach(s => { const t = sphere(0.075, H, s * 0.275, y, 0.07, sx, sy, 0.9); t.rotation.z = -s * 0.12; head.add(t); });
  const bangs = () => [-2, -1, 0, 1, 2].forEach(i => {
    const b = sphere(0.085, H, i * 0.085, 0.2 - Math.abs(i) * 0.03, 0.225 - Math.abs(i) * 0.02, 1, 1.55, 0.5); b.rotation.z = -i * 0.1; b.rotation.x = 0.25; head.add(b);
  });
  if (st === 'bob') { back(0.31, -0.06, -0.1, 1.1, 1.0, 0.95); bangs(); sides(-0.12, 0.8, 2.5); }
  else if (st === 'long') { back(0.3, 0, -0.09, 1.05, 0.96, 0.88); back(0.3, -0.28, -0.13, 1.0, 1.9, 0.55); bangs(); sides(-0.2, 0.75, 3.4); }
  else {
    back(0.3, 0, -0.09, 1.05, 0.96, 0.88); sides(-0.04, 0.6, 1.9);
    if (st === 'spiky') {
      [-3, -2, -1, 0, 1, 2, 3].forEach(i => head.add(cone(0.055, 0.17, H, i * 0.085, 0.27 - Math.abs(i) * 0.03, 0.17, 0.55, -i * 0.22)));
      [-1, 0, 1].forEach(i => head.add(cone(0.06, 0.18, H, i * 0.12, 0.3, -0.03, -0.2, -i * 0.35)));
    } else bangs();
    if (st === 'messy') [-1, 0, 1].forEach(i => head.add(cone(0.04, 0.12, H, i * 0.1, 0.28, 0.05, 0.2, -i * 0.5)));
    if (st === 'twin') [-1, 1].forEach(s => { head.add(sphere(0.085, H, s * 0.3, 0.1, -0.03)); head.add(tail(0.095, 0.4, H, s * 0.31, 0.08, -0.03, 0, s * 0.18)); if (C.ribbon) bowAt(head, s * 0.31, 0.1, 0.03, C.ribbon, 1.0); });
    if (st === 'pony') { head.add(sphere(0.075, H, 0, 0.2, -0.27)); head.add(tail(0.08, 0.42, H, 0, 0.2, -0.27, -0.5, 0)); if (C.ribbon) bowAt(head, 0.0, 0.2, -0.32, C.ribbon, 1.0); }
    if (st === 'bun') { head.add(sphere(0.105, H, 0, 0.34, -0.05)); const bd = place(new THREE.Mesh(new THREE.TorusGeometry(0.1, 0.012, 8, 28), std(0xc0303f, 0.5)), 0, 0.29, -0.04); bd.rotation.x = Math.PI / 2; head.add(bd); }
  }
  if (C.ahoge) { const a = cone(0.025, 0.14, H, 0.02, 0.29, 0.04); a.rotation.z = -0.4; head.add(a); J.ahoge = a; }
  if (C.catEars) [-1, 1].forEach(s => { head.add(cone(0.075, 0.13, H, s * 0.15, 0.25, 0.0, 0, -s * 0.4)); head.add(cone(0.042, 0.08, std(0xff9ab4, 0.6), s * 0.15, 0.255, 0.03, 0, -s * 0.4)); });
  if (C.cap) {
    const cm = std(C.cap.color, 0.7);
    const hat = place(new THREE.Mesh(new THREE.SphereGeometry(0.328, 36, 22, 0, Math.PI * 2, 0, Math.PI * 0.5), cm), 0, 0.045, 0, 1.07, 1.0, 1.05); hat.rotation.x = -0.12; head.add(hat);
    head.add(sphere(0.18, cm, 0, 0.12, C.cap.back ? -0.3 : 0.3, 1, 0.12, 1.0));
    head.add(sphere(0.02, M.white, 0, 0.33, 0));
  }

  // ---- Lengan ----
  const short = C.sleeves === 'short';
  [-1, 1].forEach(s => {
    const k = s < 0 ? 'L' : 'R';
    const sh = joint(spine, s * 0.165, 0.22, 0); sh.rotation.z = s * 0.22; J['arm' + k] = sh;
    sh.add(sphere(0.056, M.shirt));
    if (short) {
      sh.add(limb(0.046, 0.04, 0.15, M.skin));
      sh.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.058, 0.066, 0.085, 24).translate(0, -0.0425, 0), M.shirt), 0, 0, 0));
      const hm = place(new THREE.Mesh(new THREE.TorusGeometry(0.066, 0.008, 8, 24), M.dk), 0, -0.085, 0); hm.rotation.x = Math.PI / 2; sh.add(hm);
    } else sh.add(limb(0.054, 0.047, 0.15, M.shirt));
    const el = joint(sh, 0, -0.15, 0); el.rotation.x = -0.15; J['elbow' + k] = el;
    el.add(limb(short ? 0.04 : 0.047, short ? 0.036 : 0.04, 0.12, short ? M.skin : M.shirt));
    if (!short) { const cuff = place(new THREE.Mesh(new THREE.TorusGeometry(0.041, 0.012, 10, 24), M.dk), 0, -0.12, 0); cuff.rotation.x = Math.PI / 2; el.add(cuff); }
    const wr = joint(el, 0, -0.13, 0); J['wrist' + k] = wr;
    wr.add(sphere(0.047, M.skin, 0, -0.03, 0, 1, 1.05, 0.9));
    wr.add(sphere(0.02, M.skin, s * -0.04, -0.025, 0.025, 1, 1.4, 1));
  });

  // ---- Kaki ----
  [-1, 1].forEach(s => {
    const k = s < 0 ? 'L' : 'R';
    const hp = joint(hips, s * 0.065, -0.02, 0); J['leg' + k] = hp;
    const tor = (r, t, m, y) => { const x = place(new THREE.Mesh(new THREE.TorusGeometry(r, t, 8, 24), m), 0, y, 0); x.rotation.x = Math.PI / 2; return x; };
    hp.add((C.skirt || C.shorts) ? limb(0.058, 0.05, 0.15, M.skin) : limb(0.066, 0.058, 0.15, M.pants));
    if (C.shorts) { hp.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.068, 0.078, 0.1, 24).translate(0, -0.05, 0), M.pants), 0, 0, 0)); hp.add(tor(0.078, 0.009, M.pdk, -0.1)); }
    const kn = joint(hp, 0, -0.15, 0); J['knee' + k] = kn;
    if (C.skirt) kn.add(limb(0.052, 0.045, 0.13, M.sock));
    else if (C.shorts) { kn.add(limb(0.05, 0.043, 0.13, M.skin)); kn.add(place(new THREE.Mesh(new THREE.CylinderGeometry(0.052, 0.047, 0.085, 20).translate(0, -0.0875, 0), M.sock), 0, 0, 0)); kn.add(tor(0.052, 0.008, M.pdk, -0.047)); }
    else { kn.add(limb(0.057, 0.052, 0.13, M.pants)); kn.add(tor(0.056, 0.013, M.pdk, -0.118)); }
    const an = joint(kn, 0, -0.13, 0); J['ankle' + k] = an;
    an.add(sphere(0.062, M.shoes, 0, -0.025, 0.04, 1.05, 0.8, 1.7));
    an.add(sphere(0.05, M.sole, 0, -0.062, 0.042, 1.3, 0.28, 2.15));
    an.add(sphere(0.035, M.sole, 0, -0.012, 0.085, 1.0, 0.7, 0.8));
    [[0.02, 0.022], [0.045, 0.014], [0.07, 0.003]].forEach(([z, y]) => an.add(place(new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.006, 0.008), M.white), 0, y, z)));
    an.add(tor(0.046, 0.011, M.shoes, -0.004));
  });

  // ---- Idle + kedip ----
  let nextBlink = 2 + Math.random() * 2;
  player.userData.joints = J;
  player.userData.update = t => {
    const b = Math.sin(t * 2.2);
    hips.position.y = 0.385 + b * 0.006;
    J.spine.rotation.x = b * 0.01;
    J.head.rotation.y = Math.sin(t * 0.6) * 0.1;
    J.head.rotation.z = Math.sin(t * 0.9) * 0.035;
    J.armL.rotation.x = Math.sin(t * 1.1) * 0.05; J.armR.rotation.x = -Math.sin(t * 1.1) * 0.05;
    if (J.ahoge) J.ahoge.rotation.z = -0.4 + Math.sin(t * 2.6) * 0.12;
    let sy = 1;
    if (t > nextBlink) { const d = (t - nextBlink) / 0.14; sy = d < 1 ? Math.max(0.08, Math.abs(1 - 2 * d)) : 1; if (d >= 1) nextBlink = t + 2 + Math.random() * 3; }
    eyes.forEach(e => e.scale.y = sy);
  };
  return player;
}

/* ===== Daftar 10 karakter ===== */
const CHARS = [
  { g: 'Cowok', name: 'Kai', desc: 'Hoodie teal dengan kantong dan tali, ransel kuning, celana navy bermanset',
    o: { hairStyle: 'short', ahoge: true, hair: 0x5a3825, shirt: 0x2e8b8b, pants: 0x30394f, shoes: 0xd9483b, iris: 0x7a4a24, pack: 0xe3a93b, hood: true, zip: 'zip' } },
  { g: 'Cowok', name: 'Riku', desc: 'Kaos merah berlogo, celana pendek denim, kaos kaki putih, plester di pipi',
    o: { skin: 0xd9a07a, hairStyle: 'spiky', hair: 0x15151c, shirt: 0xc8363c, sleeves: 'short', pants: 0x3d4f73, shorts: true, shoes: 0xf2f2f2, iris: 0x2d2d2d, plaster: true, emblem: 0xf5f2ec } },
  { g: 'Cowok', name: 'Leo', desc: 'Sweter hijau dengan kerah kemeja putih, celana khaki, kacamata',
    o: { skin: 0xf7d3b8, hairStyle: 'messy', hair: 0xe0b45a, shirt: 0x6b8f5a, pants: 0xb89f7a, shoes: 0x6b4a32, iris: 0x3a6f9a, glasses: 0x1c1c22, under: 0xf5f2ec } },
  { g: 'Cowok', name: 'Dani', desc: 'Kaos kuning bergaris putih, jins bermanset, topi oranye terbalik',
    o: { skin: 0x8d5a3c, hairStyle: 'short', hair: 0x1a120c, shirt: 0xf0c23a, sleeves: 'short', pants: 0x3d6a9e, shoes: 0xffffff, iris: 0x3b2412, cap: { color: 0xe36a2c, back: true }, stripe: 0xffffff } },
  { g: 'Cowok', name: 'Akira', desc: 'Jaket seragam hitam berkancing emas, syal merah, rambut perak',
    o: { skin: 0xf2d3c0, hairStyle: 'messy', hair: 0xcfd5e0, shirt: 0x1d1f27, pants: 0x25272f, shoes: 0x111111, iris: 0x7a4fd0, zip: 'buttons', scarf: 0xb8323c, coat: true } },
  { g: 'Cewek', name: 'Mika', desc: 'Gaun pink berlipit dengan sabuk dan pita putih, kuncir dua',
    o: { girl: true, skin: 0xf7cdb4, hairStyle: 'twin', hair: 0xff8fb8, shirt: 0xf7a8c4, pants: 0xf7a8c4, skirt: true, sleeves: 'short', shoes: 0xc23b5c, iris: 0xd05a8a, ribbon: 0xe84a6f, belt: 0xffffff } },
  { g: 'Cewek', name: 'Sora', desc: 'Seragam pelaut: kerah navy, pita merah, rok lipit, rambut panjang',
    o: { girl: true, hairStyle: 'long', hair: 0x14131a, shirt: 0xf4f6fb, pants: 0x23325c, skirt: true, shoes: 0x3a2a22, iris: 0x2f5d8a, bow: 0xc0303f, sailor: 0x23325c } },
  { g: 'Cewek', name: 'Luna', desc: 'Hoodie lavender dengan kantong dan tali, telinga kucing',
    o: { girl: true, skin: 0xe9b996, hairStyle: 'bob', hair: 0x7a4fd0, catEars: true, shirt: 0xb9a2e8, pants: 0x4a4a68, shoes: 0xf2f2f2, iris: 0xd9a020, hood: true, zip: 'zip' } },
  { g: 'Cewek', name: 'Hana', desc: 'Kaos oranye bersabuk cokelat, rok biru berlipit, kuncir kuda',
    o: { girl: true, skin: 0xc68a63, hairStyle: 'pony', hair: 0x6b3a22, shirt: 0xf2a33a, sleeves: 'short', pants: 0x3b6ea5, skirt: true, shoes: 0x2e8b8b, iris: 0x4a2c16, freckles: true, ribbon: 0xe84a6f, belt: 0x6b4a32 } },
  { g: 'Cewek', name: 'Yuki', desc: 'Mantel hijau berkancing, rok plum, syal putih, kaos kaki hitam',
    o: { girl: true, skin: 0xfbe0d0, hairStyle: 'bun', hair: 0xf4f4fa, shirt: 0x3f7d5f, pants: 0x6b2f3a, skirt: true, sock: 0x2d2d2d, shoes: 0x3a2a22, iris: 0x5ab0c8, zip: 'buttons', glasses: 0xd06080, scarf: 0xf2f2f2, coat: true } }
];
