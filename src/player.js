// Tocador da luta: desenha os EVENTOS da simulação em pixel art.
//
// Não decide nada: só lê eventos (cast, shot, hit, kill, spawn, levelup) e
// os dados visuais de data/game.json. Toda animação é função de (agora - e.t),
// então a mesma luta fica igual em qualquer tela. O wallpaper em Kotlin deve
// ser um porte deste arquivo.

(function (root) {
  "use strict";

  const W = 160, H = 100, FLOOR = 84;
  const HERO_X = 22;
  const MON_X = 120;

  // Fonte 3x5 em pixels (só o que o HUD usa)
  const FONT = {
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
    "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001010010010",
    "8": "111101111101111", "9": "111101111001111", "+": "000010111010000", "!": "010010010000010",
    "N": "101111111111101", "I": "111010010010111", "V": "101101101101010", "E": "111100110100111",
    "L": "100100100100111", "U": "101101101101111", "P": "111101111100100", "X": "101101010101101",
    "C": "111100100100111", "R": "110101110101101", "T": "111010010010010", "O": "111101101101111",
    "K": "101101110101101", " ": "000000000000000",
  };

  // Aleatório só para enfeite (partículas), com semente no tempo do evento
  function prng(seed) {
    let s = (seed * 2654435761) >>> 0;
    return () => { s = (s + 0x6d2b79f5) >>> 0; let t = Math.imul(s ^ (s >>> 15), s | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  const clamp01 = x => x < 0 ? 0 : x > 1 ? 1 : x;
  const easeOut = p => 1 - (1 - p) * (1 - p);

  // opts.transparente: modo flutuante (sobre outros apps). Sem fundo; no lugar dele,
  // uma ilha de chão e camadas em paralaxe atrás e na frente da luta.
  function Player(canvas, data, opts) {
    this.opts = opts || {};
    this.data = data;
    this.pal = data.palette;
    this.canvas = canvas;
    // No modo flutuante corta o céu vazio de cima: a luta fica maior na mesma janela
    this.cropY = this.opts.transparente ? 32 : 0;
    canvas.width = W; canvas.height = H - this.cropY;
    this.g = canvas.getContext("2d");
    this.g.imageSmoothingEnabled = false;
    this.spells = {};
    for (const s of data.spells) this.spells[s.id] = s;
    this.hero = this.sprite(data.hero.sprite);
    this.mons = {};
    for (const [id, m] of Object.entries(data.monsters))
      this.mons[id] = { def: m, img: this.sprite(m.sprite), flash: this.sprite(m.sprite, "white") };
    this.bgs = {};
    this.fx = [];
    this.deathT = -1e12; this.spawnT = -1e12; this.lastHitT = -1e12; this.levelT = -1e12;
    this.level = 1;
  }

  // ---------- Construção de imagens ----------
  Player.prototype.sprite = function (spr, only) {
    const h = spr.rows.length, w = Math.max(...spr.rows.map(r => r.length));
    const c = document.createElement("canvas"); c.width = w; c.height = h;
    const g = c.getContext("2d");
    spr.rows.forEach((row, y) => [...row].forEach((ch, x) => {
      if (ch === ".") return;
      g.fillStyle = this.pal[only || spr.colors[ch]];
      g.fillRect(x, y, 1, 1);
    }));
    return c;
  };

  Player.prototype.background = function (stage) {
    if (this.bgs[stage.id]) return this.bgs[stage.id];
    const b = stage.bg, P = this.pal;
    const c = document.createElement("canvas"); c.width = W; c.height = H;
    const g = c.getContext("2d");
    const px = (col, x, y, w = 1, h = 1) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
    // céu em faixas com pontilhado entre as cores
    const bands = 6, bh = Math.ceil(FLOOR / bands);
    for (let i = 0; i < bands; i++) {
      const y0 = i * bh;
      px(i < bands / 2 ? P[b.top] : P[b.bottom], 0, y0, W, bh);
      if (i === Math.floor(bands / 2) - 1)
        for (let y = y0; y < y0 + bh; y++) for (let x = (y % 2); x < W; x += 2) px(P[b.bottom], x, y);
    }
    const r = prng(stage.id.length * 97 + stage.id.charCodeAt(0));
    if (b.deco === "trees") {
      for (let i = 0; i < 9; i++) {
        const x = Math.floor(r() * W), hgt = 22 + Math.floor(r() * 26);
        for (let y = 0; y < hgt; y++) { const half = Math.floor((y / hgt) * 9) + 1; px(P[b.decoColor], x - half, FLOOR - hgt + y, half * 2 + 1, 1); }
      }
    } else if (b.deco === "stalactites") {
      for (let i = 0; i < 14; i++) {
        const x = Math.floor(r() * W), hgt = 6 + Math.floor(r() * 22);
        for (let y = 0; y < hgt; y++) { const half = Math.floor((1 - y / hgt) * 4); px(P[b.decoColor], x - half, y, half * 2 + 1, 1); }
      }
    } else if (b.deco === "graves") {
      for (let i = 0; i < 7; i++) {
        const x = 4 + Math.floor(r() * (W - 12)), hgt = 7 + Math.floor(r() * 6);
        px(P[b.decoColor], x, FLOOR - hgt, 7, hgt); px(P[b.decoColor], x + 1, FLOOR - hgt - 1, 5, 1);
        px(P.ink, x + 3, FLOOR - hgt + 2, 1, 4); px(P.ink, x + 2, FLOOR - hgt + 3, 3, 1);
      }
    }
    px(P[b.floor], 0, FLOOR, W, H - FLOOR);
    px(P[b.floorLine], 0, FLOOR, W, 1);
    for (let x = 3; x < W; x += 9) px(P[b.floorLine], x, FLOOR + 3 + (x % 4), 2, 1);
    this.bgs[stage.id] = c;
    return c;
  };

  // ---------- Desenho básico ----------
  Player.prototype.px = function (col, x, y, w = 1, h = 1) {
    this.g.fillStyle = this.pal[col] || col;
    this.g.fillRect(Math.round(x), Math.round(y), w, h);
  };
  Player.prototype.line = function (col, x0, y0, x1, y1) {
    x0 = Math.round(x0); y0 = Math.round(y0); x1 = Math.round(x1); y1 = Math.round(y1);
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      this.px(col, x0, y0);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  };
  Player.prototype.text = function (str, x, y, col, scale = 1) {
    let cx = Math.round(x);
    for (const ch of String(str).toUpperCase()) {
      const f = FONT[ch] || FONT[" "];
      for (let i = 0; i < 15; i++) if (f[i] === "1") {
        const gx = cx + (i % 3) * scale, gy = Math.round(y) + Math.floor(i / 3) * scale;
        this.px("ink", gx + scale, gy + scale, scale, scale);
        this.px(col, gx, gy, scale, scale);
      }
      cx += 4 * scale;
    }
  };
  const textW = (str, scale = 1) => String(str).length * 4 * scale - scale;

  // ---------- Geometria da cena ----------
  Player.prototype.heroPos = function (now) {
    return { x: HERO_X, y: FLOOR - this.hero.height + (Math.floor(now / 450) % 2) };
  };
  Player.prototype.tip = function (now) {
    const h = this.heroPos(now), t = this.data.hero.sprite.staffTip;
    return { x: h.x + t[0], y: h.y + t[1] };
  };
  Player.prototype.monPos = function (stage) {
    const m = this.mons[stage.monster];
    return { m, x: MON_X - Math.floor(m.img.width / 2), y: FLOOR - m.img.height - (m.def.hover || 0) };
  };
  Player.prototype.target = function (stage) {
    const p = this.monPos(stage);
    return { x: MON_X, y: p.y + Math.floor(p.m.img.height / 2) };
  };

  // ---------- Eventos -> efeitos ----------
  // `fired` são os eventos que acabaram de acontecer; os muito antigos
  // (ex.: 8 horas offline) são ignorados para não animar tudo de uma vez.
  Player.prototype.onEvents = function (fired, now) {
    for (const e of fired) {
      if (e.type === "kill") this.deathT = e.t;
      if (e.type === "spawn") this.spawnT = e.t;
      if (e.type === "levelup") { this.levelT = e.t; this.level = e.level; }
      if (e.type === "hit") this.lastHitT = e.t;
      if (now - e.t > 2500) continue;
      if (e.type === "cast" || e.type === "shot" || e.type === "hit" || e.type === "kill" || e.type === "levelup") this.fx.push(e);
    }
  };

  // Eventos de disparo chegam no instante do disparo; o projétil voa depois.
  Player.prototype.draw = function (now, view) {
    const g = this.g, data = this.data;
    const stage = data.stages.find(s => s.id === view.stageId) || data.stages[0];
    this.level = view.level;

    // tremida de tela: soma das pancadas recentes
    let shake = 0;
    for (const e of this.fx) if (e.type === "hit") {
      const v = this.spells[e.spell].visual, p = (now - e.t) / 220;
      if (p >= 0 && p < 1) shake = Math.max(shake, (v.impact.shake || 0) * (e.crit ? 1.5 : 1) * (1 - p));
    }
    const sx = shake ? Math.round((Math.floor(now / 30) % 2 ? 1 : -1) * shake) : 0;
    const sy = shake ? Math.round((Math.floor(now / 45) % 2 ? 1 : -1) * shake * 0.5) : 0;

    g.save();
    g.clearRect(0, 0, W, H);
    g.translate(sx, sy - this.cropY);
    if (this.opts.transparente) this.drawBackLayer(now, stage);
    else g.drawImage(this.background(stage), 0, 0);

    // herói
    const hp = this.heroPos(now);
    g.drawImage(this.hero, hp.x, hp.y);

    // monstro
    this.drawMonster(now, stage, view);

    // efeitos (atrás -> frente)
    const keep = [];
    for (const e of this.fx) if (this.drawFx(e, now, stage)) keep.push(e);
    this.fx = keep;

    if (this.opts.transparente) this.drawFrontLayer(now, stage);
    g.restore();
    this.drawHud(now, view, stage);
  };

  Player.prototype.drawMonster = function (now, stage, view) {
    const g = this.g;
    const { m, x, y } = this.monPos(stage);
    const sinceDeath = now - this.deathT, sinceSpawn = now - this.spawnT;
    const dead = this.deathT > this.spawnT;
    let dx = 0, dy = 0, img = m.img, sw = m.img.width, sh = m.img.height;

    const idle = m.def.idle;
    if (idle === "squish") { const k = Math.floor(now / 300) % 2; sh -= k; dy += k; sw += k; dx -= k / 2; }
    if (idle === "flap") dy += Math.round(Math.sin(now / 160) * 2);
    if (idle === "sway") dx += Math.floor(now / 500) % 2;

    if (now - this.lastHitT < 90) img = m.flash;
    if (now - this.lastHitT < 140) dx += 2;

    if (dead) {
      // some em pontilhado e afunda
      const p = clamp01(sinceDeath / 450);
      if (p >= 1) return;
      const tmp = this.tmp || (this.tmp = document.createElement("canvas"));
      tmp.width = m.img.width; tmp.height = m.img.height;
      const t = tmp.getContext("2d");
      t.drawImage(m.img, 0, 0);
      t.globalCompositeOperation = "destination-out";
      const r = prng(this.deathT);
      for (let yy = 0; yy < tmp.height; yy++) for (let xx = 0; xx < tmp.width; xx++) if (r() < p) t.fillRect(xx, yy, 1, 1);
      g.drawImage(tmp, x + dx, y + dy + Math.round(p * 4));
      return;
    }
    if (sinceSpawn >= 0 && sinceSpawn < 260) dy -= Math.round((1 - easeOut(sinceSpawn / 260)) * 30);
    g.drawImage(img, Math.round(x + dx), Math.round(y + dy), Math.round(sw), Math.round(sh));

    // barra de vida
    const bw = 26, bx = MON_X - bw / 2, by = y - 6 + (m.def.hover ? -2 : 0);
    this.px("ink", bx - 1, by - 1, bw + 2, 4);
    this.px("dusk", bx, by, bw, 2);
    this.px("red", bx, by, Math.max(0, Math.round(bw * view.hp / view.max)), 2);
  };

  // Devolve false quando o efeito terminou.
  Player.prototype.drawFx = function (e, now, stage) {
    const age = now - e.t;
    if (age < 0) return true;
    const tip = this.tip(now), tgt = this.target(stage);

    if (e.type === "cast") {
      const v = this.spells[e.spell].visual, p = age / e.ms;
      if (p >= 1) return false;
      // energia se juntando na ponta do cajado
      const r = prng(e.t), n = 6 + (v.size || 1) * 2;
      for (let i = 0; i < n; i++) {
        const a = r() * Math.PI * 2, d = (1 - p) * (8 + r() * 6);
        this.px(i % 2 ? v.color : v.color2, tip.x + Math.cos(a) * d, tip.y + Math.sin(a) * d);
      }
      const s = 1 + Math.round(p * (v.size || 1));
      this.px(v.color2, tip.x - Math.floor(s / 2), tip.y - Math.floor(s / 2), s, s);
      if (v.kind === "nova") {
        const rr = Math.round(p * 6);
        this.ring(tgt.x, tgt.y, 16 - rr, v.color, 0.5);
      }
      return true;
    }

    if (e.type === "shot") {
      const v = this.spells[e.spell].visual;
      if (v.kind === "nova") return false;
      if (v.kind === "beam") {
        const dur = Math.max(e.ms, 200);
        if (age > dur) return false;
        const r = prng(e.t + Math.floor(age / 50));
        const segs = 8;
        let px0 = tip.x, py0 = tip.y;
        for (let i = 1; i <= segs; i++) {
          const k = i / segs;
          const px1 = i === segs ? tgt.x : tip.x + (tgt.x - tip.x) * k;
          const py1 = i === segs ? tgt.y : tip.y + (tgt.y - tip.y) * k + (r() - 0.5) * 12;
          this.line(v.color, px0, py0 - 1, px1, py1 - 1);
          this.line(v.color2, px0, py0, px1, py1);
          px0 = px1; py0 = py1;
        }
        return true;
      }
      // projétil em arco com rastro
      const p = age / Math.max(1, e.ms);
      if (p >= 1) return false;
      const pos = k => ({
        x: tip.x + (tgt.x - tip.x) * k,
        y: tip.y + (tgt.y - tip.y) * k - Math.sin(Math.PI * k) * (v.arc || 0) * (e.i % 2 ? -0.6 : 1),
      });
      const size = v.size || 1;
      for (let j = v.trail || 0; j >= 1; j--) {
        const k = p - j * 0.035;
        if (k < 0) continue;
        const q = pos(k), s = Math.max(1, Math.round(size * (1 - j / ((v.trail || 1) + 1))));
        this.px(j % 2 ? v.color : v.color2, q.x - s / 2, q.y - s / 2, s, s);
      }
      const h = pos(p);
      this.px(v.color, h.x - size / 2 - 1, h.y - size / 2 - 1, size + 2, size + 2);
      this.px(v.color2, h.x - size / 2, h.y - size / 2, size, size);
      return true;
    }

    if (e.type === "hit") {
      const v = this.spells[e.spell].visual, im = v.impact;
      const p = age / im.ms;
      // número de dano (dura mais que a explosão)
      const pn = age / 800;
      if (pn < 1) {
        const scale = e.crit ? 2 : 1, label = e.crit ? e.dmg + "!" : String(e.dmg);
        const nx = tgt.x - textW(label, scale) / 2 + (e.i || 0) * 6 - 4;
        const ny = tgt.y - 12 - easeOut(pn) * 14 - (e.i || 0) * 4;
        if (!(pn > 0.7 && Math.floor(now / 60) % 2)) this.text(label, nx, ny, e.crit ? "amber" : "white", scale);
      }
      if (p < 1) {
        if (im.flash && age < 70) { this.g.globalAlpha = 0.25; this.px(v.color2, 0, 0, W, H); this.g.globalAlpha = 1; }
        const r = prng(e.t + (e.i || 0));
        for (let i = 0; i < im.particles; i++) {
          const a = r() * Math.PI * 2, sp = 0.4 + r() * 0.6;
          const d = easeOut(p) * im.radius * sp * (e.crit ? 1.4 : 1);
          const s = p < 0.5 ? 2 : 1;
          this.px(i % 3 ? v.color : v.color2, tgt.x + Math.cos(a) * d, tgt.y + Math.sin(a) * d * 0.8 + p * p * 6, s, s);
        }
        if (v.kind === "nova") this.ring(tgt.x, tgt.y, easeOut(p) * im.radius * 1.4, v.color2, 1);
      }
      return pn < 1;
    }

    if (e.type === "kill") {
      const p = age / 900;
      if (p >= 1) return false;
      // moedas voando para o contador de ouro
      const r = prng(e.t * 3);
      for (let i = 0; i < 5; i++) {
        const k = clamp01((age - i * 70) / 600);
        if (k <= 0 || k >= 1) continue;
        const sx0 = tgt.x + (r() - 0.5) * 12, sy0 = tgt.y;
        const x = sx0 + (6 - sx0) * easeOut(k), y = sy0 + (5 - sy0) * k - Math.sin(Math.PI * k) * 18;
        this.px("orange", x - 1, y - 1, 3, 3);
        this.px("amber", x - 1, y - 1, 2, 2);
      }
      const label = "+" + e.gold;
      this.text(label, tgt.x - textW(label) / 2, tgt.y - 4 - p * 10, "amber");
      return true;
    }

    if (e.type === "levelup") {
      const p = age / 1800;
      if (p >= 1) return false;
      const h = this.heroPos(now);
      this.ring(h.x + 6, h.y + 8, 4 + easeOut(clamp01(p * 3)) * 14, "amber", 1);
      const label = "NIVEL " + e.level;
      const bounce = Math.round(Math.abs(Math.sin(p * Math.PI * 3)) * 3 * (1 - p));
      this.text(label, W / 2 - textW(label) / 2, 18 + this.cropY - bounce, "amber");
      return true;
    }
    return false;
  };

  // ---------- Modo flutuante: camadas em paralaxe ----------
  // Faixa de enfeites (árvores, estalactites, lápides) que se repete na horizontal.
  Player.prototype.decoStrip = function (stage, front) {
    const key = stage.id + (front ? ":f" : ":b");
    this.strips = this.strips || {};
    if (this.strips[key]) return this.strips[key];
    const b = stage.bg, P = this.pal, SWD = W * 2;
    const c = document.createElement("canvas"); c.width = SWD; c.height = H;
    const g = c.getContext("2d");
    const px = (col, x, y, w = 1, h = 1) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
    const r = prng(stage.id.length * 131 + (front ? 7 : 3));
    if (!front) {
      // atrás: silhuetas menores, só na ilha
      const col = P[b.decoColor] || P.ink;
      for (let i = 0; i < 10; i++) {
        const x = Math.floor(r() * SWD);
        if (b.deco === "trees") {
          const hgt = 14 + Math.floor(r() * 16);
          for (let y = 0; y < hgt; y++) { const half = Math.floor((y / hgt) * 6) + 1; px(col, x - half, FLOOR - hgt + y, half * 2 + 1, 1); }
        } else if (b.deco === "stalactites") {
          const hgt = 6 + Math.floor(r() * 10);
          for (let y = 0; y < hgt; y++) { const half = Math.floor((y / hgt) * 3); px(col, x - half, FLOOR - hgt + y, half * 2 + 1, 1); }
        } else {
          const hgt = 6 + Math.floor(r() * 5);
          px(col, x, FLOOR - hgt, 6, hgt); px(col, x + 1, FLOOR - hgt - 1, 4, 1);
        }
      }
    } else {
      // na frente: tufos de grama/pedrinhas que passam rente ao chão
      for (let i = 0; i < 16; i++) {
        const x = Math.floor(r() * SWD), hgt = 2 + Math.floor(r() * 4);
        for (let k = 0; k < 3; k++) px(k === 1 ? P[b.floorLine] : P[b.floor], x + k * 2, FLOOR + 3 - hgt + (k === 1 ? -1 : 0), 1, hgt + (k === 1 ? 1 : 0));
      }
    }
    this.strips[key] = c;
    return c;
  };

  Player.prototype.drawStrip = function (img, offset) {
    const SWD = img.width;
    const x = -(((offset % SWD) + SWD) % SWD);
    this.g.drawImage(img, x, 0);
    this.g.drawImage(img, x + SWD, 0);
  };

  // Ilha de chão (as pontas arredondadas deixam ver o que está atrás)
  Player.prototype.drawIsland = function (stage) {
    const b = stage.bg, x0 = 6, x1 = W - 6;
    for (let y = FLOOR; y < FLOOR + 10; y++) {
      const shrink = Math.floor(((y - FLOOR) / 10) ** 2 * 26);
      this.px(y === FLOOR ? b.floorLine : b.floor, x0 + shrink, y, x1 - x0 - shrink * 2, 1);
    }
  };

  Player.prototype.drawBackLayer = function (now, stage) {
    const g = this.g;
    g.save();
    // corta as silhuetas para caberem só em cima da ilha
    g.beginPath(); g.rect(10, 0, W - 20, FLOOR); g.clip();
    g.globalAlpha = 0.85;
    this.drawStrip(this.decoStrip(stage, false), now * 0.004);
    g.restore();
    this.drawIsland(stage);
  };

  Player.prototype.drawFrontLayer = function (now, stage) {
    const g = this.g;
    g.save();
    g.beginPath(); g.rect(8, 0, W - 16, H); g.clip();
    this.drawStrip(this.decoStrip(stage, true), now * 0.012);
    g.restore();
    // partículas flutuando na frente (folhas, poeira, brasas) pela cor da fase
    const col = stage.bg.deco === "trees" ? "green" : stage.bg.deco === "graves" ? "lilac" : "gray";
    for (let i = 0; i < 6; i++) {
      const sp = 0.006 + i * 0.002;
      const x = W - ((now * sp + i * 41) % (W + 20)) + 10;
      const y = 20 + ((i * 23) % 50) + Math.sin(now / 600 + i) * 4;
      this.px(col, x, y, i % 2 ? 1 : 2, 1);
    }
  };

  Player.prototype.ring = function (cx, cy, r, col, step) {
    if (r <= 0) return;
    const n = Math.max(8, Math.round(r * 5));
    for (let i = 0; i < n; i += step >= 1 ? 1 : 2) {
      const a = (i / n) * Math.PI * 2;
      this.px(col, cx + Math.cos(a) * r, cy + Math.sin(a) * r * 0.7);
    }
  };

  // HUD dentro do canvas: precisa ser legível na janelinha (PiP)
  Player.prototype.drawHud = function (now, view, stage) {
    this.px("amber", 2, 2, 4, 4); this.px("orange", 3, 3, 3, 3); this.px("amber", 3, 3, 2, 2);
    this.text(view.gold, 8, 2, "white");
    this.text("NV" + view.level, 2, 9, "lilac");
    // barra de XP no chão, embaixo do herói
    const need = Sim.xpNeed(this.data, view.level);
    const bw = 24;
    this.px("ink", HERO_X - 6, FLOOR + 6, bw + 2, 3);
    this.px("teal", HERO_X - 5, FLOOR + 7, Math.round(bw * Math.min(1, view.xp / need)), 1);
  };

  Player.W = W; Player.H = H; Player.H_FLUTUANTE = H - 32;
  root.Player = Player;
})(window);
