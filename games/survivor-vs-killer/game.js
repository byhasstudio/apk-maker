/* =====================================================================
   SURVIVOR — prototipe gerak (Three.js r128, tanpa library fisika)
   - isSlippery = false : WASD normal, kecepatan standar
   - isSlippery = true  : meluncur seperti es (momentum, arah "liar")
   - tabrak dinding saat licin -> ragdoll 3 detik, lalu berdiri lagi
   Karakter: chibi.js (createChibi + CHARS)
   ===================================================================== */

// ───────── Konstanta (ubah di sini untuk menyetel rasa gerak) ─────────
const HIP_H = 0.385;            // tinggi pinggul karakter dari lantai
const R_PLAYER = 0.28;          // radius tabrakan karakter
const WALK_SPEED = 3.2;         // kecepatan standar (m/s)
const WALK_ACCEL = 40;          // percepatan gerak normal (responsif)
const SLIP = {
  accel: 12,                    // gaya geser saat menekan WASD
  maxSpeed: 7.5,                // kecepatan maksimum di es
  drag: 0.35,                   // hambatan sangat kecil -> momentum bertahan
  wobbleDeg: 35,                // sudut "liar" maksimum arah dorongan
  wobbleFreq: 1.7               // seberapa cepat arah berubah-ubah
};
const WALL_HIT_MIN_SPEED = 3.2; // tabrak dinding lebih pelan dari ini = tidak jatuh
const FALL_TIME = 3;            // lama jatuh/ragdoll (detik)
const STAND_TIME = 0.6;         // lama animasi berdiri
const ARENA = 16;               // setengah ukuran arena
const ICE = { minX: -8, maxX: 8, minZ: -8, maxZ: 8 }; // area lantai licin
const UP = new THREE.Vector3(0, 1, 0);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

// ───────── Renderer & scene ─────────
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.outputEncoding = THREE.sRGBEncoding;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x1e2531);
scene.fog = new THREE.Fog(0x1e2531, 25, 60);
const camera = new THREE.PerspectiveCamera(55, 1, 0.1, 100);

scene.add(new THREE.HemisphereLight(0xdfeaff, 0x4a4036, 0.9));
const sun = new THREE.DirectionalLight(0xfff0dc, 1.2);
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.bias = -0.0005;
Object.assign(sun.shadow.camera, { left: -8, right: 8, top: 8, bottom: -8, near: 0.5, far: 30 });
scene.add(sun, sun.target);

// ───────── Arena: lantai, area es, dinding ─────────
const floor = new THREE.Mesh(new THREE.PlaneGeometry(ARENA * 2, ARENA * 2),
  new THREE.MeshStandardMaterial({ color: 0x46556e, roughness: 0.95 }));
floor.rotation.x = -Math.PI / 2; floor.receiveShadow = true; scene.add(floor);
const grid = new THREE.GridHelper(ARENA * 2, ARENA * 2, 0x5a6a86, 0x3a475c);
grid.position.y = 0.01; scene.add(grid);

// Area es (zona Lantai Licin): kotak biru mengilap + bingkai
const iceW = ICE.maxX - ICE.minX, iceD = ICE.maxZ - ICE.minZ;
const iceFrame = new THREE.Mesh(new THREE.PlaneGeometry(iceW + 0.5, iceD + 0.5), new THREE.MeshBasicMaterial({ color: 0x2a6f9a }));
iceFrame.rotation.x = -Math.PI / 2; iceFrame.position.y = 0.015; scene.add(iceFrame);
const ice = new THREE.Mesh(new THREE.PlaneGeometry(iceW, iceD),
  new THREE.MeshStandardMaterial({ color: 0xa8dcff, roughness: 0.08, metalness: 0.3 }));
ice.rotation.x = -Math.PI / 2; ice.position.y = 0.02; ice.receiveShadow = true; scene.add(ice);

// Daftar kotak tabrakan (AABB) sekaligus membuat mesh dindingnya
const boxes = [];
function addBox(cx, cz, w, d, h, color) {
  boxes.push({ minX: cx - w / 2, maxX: cx + w / 2, minZ: cz - d / 2, maxZ: cz + d / 2 });
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshStandardMaterial({ color, roughness: 0.8 }));
  m.position.set(cx, h / 2, cz); m.castShadow = m.receiveShadow = true; scene.add(m);
}
const E = ARENA + 0.5;                       // dinding pembatas arena
addBox(0, -E, ARENA * 2 + 2, 1, 2.5, 0x59678a);
addBox(0, E, ARENA * 2 + 2, 1, 2.5, 0x59678a);
addBox(-E, 0, 1, ARENA * 2 + 2, 2.5, 0x59678a);
addBox(E, 0, 1, ARENA * 2 + 2, 2.5, 0x59678a);
addBox(6, -3, 1, 6, 1.6, 0x8a5a5a);          // dinding dalam area es
addBox(-5, 4, 6, 1, 1.6, 0x8a5a5a);
addBox(0, -6, 2, 2, 1.6, 0x8a5a5a);
addBox(-9, 11, 2, 2, 1.6, 0x5a8a6a);         // pilar di lantai normal (tes: tidak jatuh)

/** Dorong lingkaran (pos,r) keluar dari semua kotak. Mengembalikan normal tabrakan terdalam (atau null). */
function collide(pos, r) {
  let best = null, bestPen = 0;
  for (const b of boxes) {
    const cx = clamp(pos.x, b.minX, b.maxX), cz = clamp(pos.z, b.minZ, b.maxZ);
    const dx = pos.x - cx, dz = pos.z - cz, d2 = dx * dx + dz * dz;
    if (d2 >= r * r) continue;
    let nx, nz, pen;
    if (d2 > 1e-9) { const d = Math.sqrt(d2); nx = dx / d; nz = dz / d; pen = r - d; }
    else { // pusat berada di dalam kotak: keluar lewat sisi terdekat
      const l = pos.x - b.minX, rt = b.maxX - pos.x, t = pos.z - b.minZ, bt = b.maxZ - pos.z, m = Math.min(l, rt, t, bt);
      if (m === l) { nx = -1; nz = 0; } else if (m === rt) { nx = 1; nz = 0; } else if (m === t) { nx = 0; nz = -1; } else { nx = 0; nz = 1; }
      pen = m + r;
    }
    pos.x += nx * pen; pos.z += nz * pen;
    if (pen > bestPen) { bestPen = pen; best = new THREE.Vector3(nx, 0, nz); }
  }
  return best;
}
const inIce = p => p.x > ICE.minX && p.x < ICE.maxX && p.z > ICE.minZ && p.z < ICE.maxZ;

// ───────── Survivor: model + status ─────────
// pivot berada di PINGGUL, supaya ragdoll berputar di sekitar pinggul.
const pivot = new THREE.Group(); scene.add(pivot);
let model = null, J = null;
const FLOP = ['armL', 'armR', 'elbowL', 'elbowR', 'legL', 'legR', 'kneeL', 'kneeR', 'spine', 'head'];
const JR = {}, JS = {};         // JR = pose awal tiap sendi, JS = status pegas ragdoll

function buildModel(i) {
  if (model) pivot.remove(model);
  model = createChibi(CHARS[i].o);
  model.position.y = -HIP_H;
  model.traverse(o => { if (o.isMesh) o.castShadow = true; });
  pivot.add(model);
  J = model.userData.joints;
  FLOP.forEach(k => { const r = J[k].rotation; JR[k] = { x: r.x, y: r.y, z: r.z }; JS[k] = { ox: 0, oz: 0, vx: 0, vz: 0 }; });
}

const P = {
  isSlippery: false,            // <── VARIABEL UTAMA: lantai licin atau tidak
  inIce: false,                 // dipakai agar zona hanya mengubah isSlippery saat masuk/keluar
  state: 'normal',              // 'normal' | 'ragdoll' | 'standing'
  pos: new THREE.Vector3(0, 0, 10), vel: new THREE.Vector3(),
  facing: Math.PI, phase: 0, seed: Math.random() * 100,
  t: 0, lastN: new THREE.Vector3(0, 0, 1)
};
const RAG = { pos: new THREE.Vector3(), vel: new THREE.Vector3(), q: new THREE.Quaternion(), w: new THREE.Vector3() };
let stand = null;

// ───────── Input: keyboard + joystick sentuh ─────────
const keys = new Set(), joy = { x: 0, y: 0 };
addEventListener('keydown', e => {
  const k = e.key.toLowerCase(); keys.add(k);
  if (k === 'f') toggleSlip(); if (k === 'r') respawn();
  if (e.key.startsWith('Arrow') || e.key === ' ') e.preventDefault();
});
addEventListener('keyup', e => keys.delete(e.key.toLowerCase()));
addEventListener('blur', () => keys.clear());
function getInput() {
  const k = n => keys.has(n) ? 1 : 0;
  let x = k('d') + k('arrowright') - k('a') - k('arrowleft') + joy.x;
  let y = k('w') + k('arrowup') - k('s') - k('arrowdown') + joy.y;
  const l = Math.hypot(x, y); if (l > 1) { x /= l; y /= l; }
  return { x, y };
}

// Joystick layar sentuh
const stick = document.getElementById('stick'), knob = document.getElementById('knob'); let stickPtr = null;
function setStick(e) {
  const r = stick.getBoundingClientRect();
  let dx = (e.clientX - r.left - r.width / 2) / (r.width / 2), dy = (e.clientY - r.top - r.height / 2) / (r.height / 2);
  const l = Math.hypot(dx, dy); if (l > 1) { dx /= l; dy /= l; }
  joy.x = dx; joy.y = -dy; knob.style.transform = `translate(${dx * 38}px,${dy * 38}px)`;
}
stick.addEventListener('pointerdown', e => { stickPtr = e.pointerId; stick.setPointerCapture(e.pointerId); setStick(e); });
stick.addEventListener('pointermove', e => { if (e.pointerId === stickPtr) setStick(e); });
const endStick = e => { if (e.pointerId !== stickPtr) return; stickPtr = null; joy.x = joy.y = 0; knob.style.transform = ''; };
stick.addEventListener('pointerup', endStick); stick.addEventListener('pointercancel', endStick);

// ───────── Kamera third-person (seret untuk memutar, roda untuk zoom) ─────────
let camYaw = 0, camPitch = 0.35, camDist = 4.2;
const camFocus = new THREE.Vector3(0, 0.85, 10);
const el = renderer.domElement; let camPtr = null, lx = 0, ly = 0;
el.addEventListener('pointerdown', e => { camPtr = e.pointerId; lx = e.clientX; ly = e.clientY; el.setPointerCapture(e.pointerId); });
el.addEventListener('pointermove', e => {
  if (e.pointerId !== camPtr) return;
  camYaw -= (e.clientX - lx) * 0.006; camPitch = clamp(camPitch + (e.clientY - ly) * 0.005, -0.1, 1.1);
  lx = e.clientX; ly = e.clientY;
});
el.addEventListener('pointerup', () => camPtr = null); el.addEventListener('pointercancel', () => camPtr = null);
el.addEventListener('wheel', e => { e.preventDefault(); camDist = clamp(camDist + e.deltaY * 0.003, 2, 8); }, { passive: false });

function updateCamera(dt) {
  camFocus.lerp(new THREE.Vector3(pivot.position.x, 0.85, pivot.position.z), 1 - Math.exp(-12 * dt));
  const cp = Math.cos(camPitch);
  const x = clamp(camFocus.x + Math.sin(camYaw) * cp * camDist, -(ARENA - 0.3), ARENA - 0.3);
  const z = clamp(camFocus.z + Math.cos(camYaw) * cp * camDist, -(ARENA - 0.3), ARENA - 0.3);
  camera.position.set(x, Math.max(0.4, camFocus.y + Math.sin(camPitch) * camDist), z);
  camera.lookAt(camFocus);
}

// ───────── Gerak: NORMAL vs LICIN ─────────
const wobble = t => Math.sin(t * SLIP.wobbleFreq + P.seed) * 0.6 + Math.sin(t * SLIP.wobbleFreq * 2.3 + P.seed * 1.7) * 0.4;
const turnTo = (cur, target, k) => { let d = Math.atan2(Math.sin(target - cur), Math.cos(target - cur)); return cur + d * Math.min(1, k); };

function updateNormal(dt, t) {
  const inp = getInput();
  // arah WASD relatif kamera
  const fwd = new THREE.Vector3(-Math.sin(camYaw), 0, -Math.cos(camYaw)), rgt = new THREE.Vector3(-fwd.z, 0, fwd.x);
  const dir = fwd.multiplyScalar(inp.y).add(rgt.multiplyScalar(inp.x));
  const v = P.vel;

  if (!P.isSlippery) {
    // NORMAL: kecepatan mendekati target dengan cepat -> responsif, berhenti seketika
    const tx = dir.x * WALK_SPEED, tz = dir.z * WALK_SPEED, dx = tx - v.x, dz = tz - v.z, dl = Math.hypot(dx, dz), step = WALK_ACCEL * dt;
    if (dl <= step) { v.x = tx; v.z = tz; } else { v.x += dx / dl * step; v.z += dz / dl * step; }
    if (dir.lengthSq() > 0.01) P.facing = turnTo(P.facing, Math.atan2(dir.x, dir.z), 14 * dt);
  } else {
    // LICIN: input hanya MENAMBAH gaya geser (momentum), arahnya dibelokkan acak -> "liar"
    if (dir.lengthSq() > 0.01) {
      const wild = dir.clone().applyAxisAngle(UP, wobble(t) * SLIP.wobbleDeg * Math.PI / 180);
      v.addScaledVector(wild, SLIP.accel * dt);
    }
    v.multiplyScalar(Math.exp(-SLIP.drag * dt));                 // friksi hampir nol
    const sp = Math.hypot(v.x, v.z); if (sp > SLIP.maxSpeed) { v.x *= SLIP.maxSpeed / sp; v.z *= SLIP.maxSpeed / sp; }
    if (sp > 0.6) P.facing = turnTo(P.facing, Math.atan2(v.x, v.z), 6 * dt);
  }

  // gerak + tabrakan
  const pre = v.clone();
  P.pos.x += v.x * dt; P.pos.z += v.z * dt;
  const n = collide(P.pos, R_PLAYER);
  if (n) {
    const into = -v.dot(n);
    if (into > 0) {
      if (P.isSlippery && into >= WALL_HIT_MIN_SPEED) { startRagdoll(n, pre); return; } // tabrak keras di es -> jatuh
      v.addScaledVector(n, into);                                // tabrak pelan: meluncur menyusuri dinding
    }
  }
  // zona es: hanya mengubah isSlippery saat masuk/keluar area
  const now = inIce(P.pos); if (now !== P.inIce) { P.inIce = now; P.isSlippery = now; }

  pivot.position.set(P.pos.x, HIP_H, P.pos.z);
  pivot.quaternion.setFromAxisAngle(UP, P.facing);
  posePlay(dt, t);
}

// Animasi jalan (normal) / pose seluncur (licin)
function posePlay(dt, t) {
  model.userData.update(t);                                      // napas + kedip dari chibi.js
  const sp = Math.hypot(P.vel.x, P.vel.z), sgn = j => Math.sign(J[j].position.x) || 1;
  if (!P.isSlippery) {
    const k = Math.min(sp / WALK_SPEED, 1); P.phase += sp * dt * 2.4;
    const s = Math.sin(P.phase) * 0.9 * k;
    J.legL.rotation.x = s; J.legR.rotation.x = -s;
    J.kneeL.rotation.x = Math.max(0, s) * 1.1; J.kneeR.rotation.x = Math.max(0, -s) * 1.1;
    J.armL.rotation.x = -s * 0.8; J.armR.rotation.x = s * 0.8;
    J.elbowL.rotation.x = J.elbowR.rotation.x = -0.15 - k * 0.5;
    J.hips.position.y += Math.abs(Math.sin(P.phase)) * 0.025 * k;
  } else {
    const k = Math.min(sp / SLIP.maxSpeed, 1);
    J.spine.rotation.x = 0.35 * k; J.spine.rotation.z = Math.sin(t * 5) * 0.12 * k; J.head.rotation.x = -0.18 * k;
    J.legL.rotation.x = 0.4; J.legR.rotation.x = -0.3; J.kneeL.rotation.x = J.kneeR.rotation.x = 0.3;
    J.legL.rotation.z = sgn('legL') * (0.28 + 0.12 * Math.sin(t * 4));
    J.legR.rotation.z = sgn('legR') * (0.28 + 0.12 * Math.sin(t * 4 + 2));
    J.armL.rotation.z = sgn('armL') * (1.1 + 0.25 * Math.sin(t * 6));       // tangan terentang menjaga keseimbangan
    J.armR.rotation.z = sgn('armR') * (1.1 + 0.25 * Math.sin(t * 6 + 2));
    J.armL.rotation.x = 0.2; J.armR.rotation.x = -0.2; J.elbowL.rotation.x = J.elbowR.rotation.x = -0.3;
  }
}

// ───────── Ragdoll (fisika sederhana: terpental, berguling, sendi lemas) ─────────
const LIMX = { armL: [-2.6, 2.6], armR: [-2.6, 2.6], elbowL: [-2.2, -0.05], elbowR: [-2.2, -0.05], legL: [-1.4, 1.4], legR: [-1.4, 1.4], kneeL: [0.05, 2.2], kneeR: [0.05, 2.2], spine: [-0.8, 0.8], head: [-0.9, 0.9] };
function kickJoints(p) { FLOP.forEach(k => { JS[k].vx += (Math.random() - 0.5) * p * 3; JS[k].vz += (Math.random() - 0.5) * p * 3; }); }

function startRagdoll(n, pre) {
  P.state = 'ragdoll'; P.t = 0; P.lastN.copy(n);
  const speed = pre.length();
  RAG.pos.copy(pivot.position);
  RAG.vel.copy(pre).multiplyScalar(0.2).addScaledVector(n, speed * 0.5); RAG.vel.y = 3 + speed * 0.25; // terpental balik
  RAG.q.copy(pivot.quaternion);
  RAG.w.copy(UP).cross(n).multiplyScalar(speed * 1.2).add(new THREE.Vector3(Math.random() - .5, Math.random() - .5, Math.random() - .5).multiplyScalar(2));
  P.vel.set(0, 0, 0);
  FLOP.forEach(k => { const s = JS[k]; s.ox = (Math.random() - .5) * 1.2; s.oz = (Math.random() - .5) * 1.0; s.vx = s.vz = 0; });
  JS.kneeL.ox = 0.4 + Math.random() * 0.7; JS.kneeR.ox = 0.4 + Math.random() * 0.7;
  JS.elbowL.ox = -Math.random() * 0.8; JS.elbowR.ox = -Math.random() * 0.8;
  kickJoints(speed);
}

function updateRagdoll(dt) {
  P.t += dt;
  const R = RAG;
  R.vel.y -= 20 * dt;                                            // gravitasi
  R.pos.addScaledVector(R.vel, dt);

  // tinggi lantai bergantung orientasi badan (kepala & kaki tidak menembus lantai)
  const up = UP.clone().applyQuaternion(R.q);
  const lowest = up.y >= 0 ? -HIP_H * up.y : 0.78 * up.y;
  const floorY = Math.max(0.17, -lowest + 0.02);
  let onFloor = false;
  if (R.pos.y < floorY) {
    R.pos.y = floorY; onFloor = true;
    if (R.vel.y < -1.5) { kickJoints(-R.vel.y); R.vel.y *= -0.3; } else R.vel.y = 0;
  }
  if (onFloor) { const f = Math.exp(-3.5 * dt); R.vel.x *= f; R.vel.z *= f; R.w.multiplyScalar(Math.exp(-2.5 * dt)); }
  else R.w.multiplyScalar(Math.exp(-0.3 * dt));

  // tabrakan badan dengan dinding (memantul pelan)
  const n = collide(R.pos, R_PLAYER + 0.05);
  if (n) { const into = -R.vel.dot(n); if (into > 0) R.vel.addScaledVector(n, into * 1.3); }

  // rotasi badan
  const ang = R.w.length();
  if (ang > 1e-4) R.q.premultiply(new THREE.Quaternion().setFromAxisAngle(R.w.clone().normalize(), ang * dt));
  // sudah pelan di lantai -> rebah mendatar
  if (onFloor && R.vel.lengthSq() < 1) {
    const h = new THREE.Vector3(up.x, 0, up.z);
    if (h.lengthSq() < 1e-4) h.set(Math.sin(P.facing), 0, Math.cos(P.facing));
    h.normalize();
    const target = new THREE.Quaternion().setFromUnitVectors(up.clone().normalize(), h).multiply(R.q);
    R.q.slerp(target, 1 - Math.exp(-5 * dt));
  }

  // sendi lemas: pegas teredam menuju pose lemas acak + hentakan dari gerak badan
  FLOP.forEach(k => {
    const s = JS[k], r = J[k].rotation, rest = JR[k], lx = LIMX[k];
    s.vx += (Math.random() - .5) * ang * dt * 40;
    s.vx += (-26 * (r.x - (rest.x + s.ox)) - 3 * s.vx) * dt;
    s.vz += (-26 * (r.z - (rest.z + s.oz)) - 3 * s.vz) * dt;
    r.x = clamp(r.x + s.vx * dt, lx[0], lx[1]); r.z = clamp(r.z + s.vz * dt, rest.z - 1.3, rest.z + 1.3);
  });

  pivot.position.copy(R.pos); pivot.quaternion.copy(R.q);

  if (P.t >= FALL_TIME) beginStand();                            // 3 detik selesai -> berdiri
}

function beginStand() {
  P.state = 'standing';
  const j0 = {}; FLOP.forEach(k => { j0[k] = { x: J[k].rotation.x, z: J[k].rotation.z }; });
  const face = Math.atan2(-P.lastN.x, -P.lastN.z);              // berdiri menghadap dinding
  stand = { t: 0, pos0: RAG.pos.clone(), q0: RAG.q.clone(), j0, qT: new THREE.Quaternion().setFromAxisAngle(UP, face), face };
}

function updateStanding(dt) {
  stand.t += dt;
  const u = clamp(stand.t / STAND_TIME, 0, 1), e = u * u * (3 - 2 * u);
  pivot.position.set(stand.pos0.x, stand.pos0.y + (HIP_H - stand.pos0.y) * e, stand.pos0.z);
  pivot.quaternion.copy(stand.q0).slerp(stand.qT, e);
  FLOP.forEach(k => {
    J[k].rotation.x = stand.j0[k].x + (JR[k].x - stand.j0[k].x) * e;
    J[k].rotation.z = stand.j0[k].z + (JR[k].z - stand.j0[k].z) * e;
  });
  if (u >= 1) {                                                  // selesai: kontrol kembali
    P.state = 'normal'; P.facing = stand.face;
    P.pos.set(pivot.position.x, 0, pivot.position.z); collide(P.pos, R_PLAYER);
    P.vel.set(0, 0, 0); P.inIce = inIce(P.pos);
  }
}

// ───────── Kontrol UI & respawn ─────────
const slipBtn = document.getElementById('slipBtn');
function toggleSlip() { P.isSlippery = !P.isSlippery; }
function respawn() {
  P.state = 'normal'; P.pos.set(0, 0, 10); P.vel.set(0, 0, 0); P.facing = Math.PI; P.isSlippery = false; P.inIce = false;
  FLOP.forEach(k => { J[k].rotation.x = JR[k].x; J[k].rotation.z = JR[k].z; });
  pivot.position.set(0, HIP_H, 10); pivot.quaternion.setFromAxisAngle(UP, P.facing);
  camYaw = 0; camFocus.set(0, 0.85, 10);
}
slipBtn.onclick = toggleSlip; document.getElementById('respawnBtn').onclick = respawn;

const sel = document.getElementById('char');
CHARS.forEach((c, i) => { const o = document.createElement('option'); o.value = i; o.textContent = c.name + ' (' + c.g + ')'; sel.appendChild(o); });
sel.onchange = () => { buildModel(+sel.value); sel.blur(); };
buildModel(0);
pivot.position.set(P.pos.x, HIP_H, P.pos.z); pivot.quaternion.setFromAxisAngle(UP, P.facing);

function resize() { renderer.setSize(innerWidth, innerHeight); camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); }
addEventListener('resize', resize); resize();

// ───────── HUD ─────────
const fl = document.getElementById('fl'), spEl = document.getElementById('sp'), warn = document.getElementById('warn');
function updateHud() {
  fl.textContent = P.isSlippery ? 'LICIN' : 'Normal'; fl.classList.toggle('ice', P.isSlippery);
  slipBtn.textContent = 'Lantai Licin: ' + (P.isSlippery ? 'ON' : 'OFF'); slipBtn.classList.toggle('on', P.isSlippery);
  spEl.textContent = Math.hypot(P.vel.x, P.vel.z).toFixed(1);
  warn.textContent = P.state === 'ragdoll' ? 'Terjatuh! Berdiri dalam ' + Math.max(0, FALL_TIME - P.t).toFixed(1) + ' dtk'
    : P.state === 'standing' ? 'Berdiri...' : '';
}

// ───────── Loop utama ─────────
const clock = new THREE.Clock(); let T = 0;
(function loop() {
  const dt = Math.min(clock.getDelta(), 0.05); T += dt;
  if (P.state === 'normal') updateNormal(dt, T);
  else if (P.state === 'ragdoll') updateRagdoll(dt);
  else updateStanding(dt);
  updateCamera(dt);
  sun.position.set(pivot.position.x + 4, 8, pivot.position.z + 3); sun.target.position.set(pivot.position.x, 0, pivot.position.z);
  updateHud();
  renderer.render(scene, camera);
  requestAnimationFrame(loop);
})();
