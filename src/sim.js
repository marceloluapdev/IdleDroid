// Simulação determinística da luta idle.
//
// O jogo nunca "roda" em segundo plano: guarda só um TRECHO (estado inicial,
// horário de início e semente) e recalcula a luta a partir dele. Mesmo trecho +
// mesmo instante = mesma luta, sempre. Isso vale no app, no PiP e (no futuro)
// no wallpaper em Kotlin, que deve portar este arquivo linha a linha.
//
// Regras para manter o porte fácil e o resultado idêntico:
//  - toda aleatoriedade sai de rand(c) (mulberry32), sempre na mesma ordem;
//  - números arredondados com Math.round/Math.floor logo após cada conta;
//  - nada de Date.now() aqui dentro: o tempo sempre vem de fora.

(function (root) {
  "use strict";

  // ---------- Aleatório com semente (mulberry32) ----------
  function rand(c) {
    c.seed = (c.seed + 0x6d2b79f5) >>> 0;
    let t = c.seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  // Mistura dois números numa semente nova (para trechos novos)
  function mixSeed(a, b) {
    let h = (a ^ 0x9e3779b9) >>> 0;
    h = Math.imul(h ^ (b >>> 0), 0x85ebca6b) >>> 0;
    h = Math.imul(h ^ Math.floor(b / 4294967296), 0xc2b2ae35) >>> 0;
    return (h ^ (h >>> 16)) >>> 0;
  }

  // ---------- Regras ----------
  function xpNeed(data, level) {
    const x = data.rules.xpToLevel;
    return Math.floor(x.base * Math.pow(level, x.exp));
  }
  function attack(data, level) {
    return data.rules.baseAttack + data.rules.attackPerLevel * (level - 1);
  }
  function stageOf(data, id) {
    return data.stages.find(s => s.id === id) || data.stages[0];
  }
  function monsterMax(data, stage, level) {
    return stage.hp + (stage.hpPerLevel || 0) * (level - 1);
  }
  function spellsFor(data, level) {
    return data.spells.filter(s => s.minLevel <= level);
  }
  function pickSpell(data, c) {
    const list = spellsFor(data, c.level);
    let total = 0;
    for (const s of list) total += s.weight;
    let r = rand(c) * total;
    for (const s of list) { r -= s.weight; if (r < 0) return s; }
    return list[list.length - 1];
  }

  // ---------- Trecho (o que é salvo) ----------
  // { stageId, start (ms), seed, level, xp, gold, kills, hp (null = monstro novo) }
  function newSegment(data, from, now, stageId) {
    const stage = stageOf(data, stageId || from.stageId);
    const sameStage = stage.id === from.stageId;
    return {
      stageId: stage.id,
      start: now,
      seed: mixSeed(from.seed >>> 0, now),
      level: from.level, xp: from.xp, gold: from.gold, kills: from.kills,
      hp: sameStage && from.hp > 0 ? from.hp : null,
    };
  }

  function firstSegment(data, now, seed) {
    return {
      stageId: data.stages[0].id, start: now, seed: seed >>> 0,
      level: 1, xp: 0, gold: 0, kills: 0, hp: null,
    };
  }

  // ---------- Cursor: estado "à frente" usado para gerar eventos ----------
  function cursorFrom(data, seg) {
    const stage = stageOf(data, seg.stageId);
    const max = monsterMax(data, stage, seg.level);
    return {
      t: seg.start, seed: seg.seed >>> 0, stageId: stage.id,
      level: seg.level, xp: seg.xp, gold: seg.gold, kills: seg.kills,
      hp: seg.hp == null ? max : seg.hp, max,
    };
  }

  // Uma ação do herói: conjura, dispara 1+ projéteis, acerta, talvez mata.
  function step(data, c, out) {
    const R = data.rules;
    const stage = stageOf(data, c.stageId);
    const spell = pickSpell(data, c);
    const hits = spell.hits || 1;
    const gap = spell.hitGapMs || 0;
    const t0 = c.t;
    const castEnd = t0 + spell.castMs;

    out.push({ t: t0, type: "cast", spell: spell.id, ms: spell.castMs });

    let end = castEnd + spell.recoverMs;
    for (let i = 0; i < hits; i++) {
      const launch = castEnd + i * gap;
      const impact = launch + spell.travelMs;
      const crit = rand(c) < R.critChance;
      const spread = 0.9 + 0.2 * rand(c);
      const dmg = Math.max(1, Math.round(attack(data, c.level) * spell.mult * (crit ? R.critMult : 1) * spread));

      out.push({ t: launch, type: "shot", spell: spell.id, ms: spell.travelMs, i });
      c.hp = Math.max(0, c.hp - dmg);
      out.push({ t: impact, type: "hit", spell: spell.id, dmg, crit, hp: c.hp, max: c.max, i });
      end = Math.max(end, impact + spell.recoverMs);

      if (c.hp === 0) {
        const xp = c.level > (stage.levelCap || Infinity) ? 1 : stage.xp;
        c.kills++; c.gold += stage.gold; c.xp += xp;
        out.push({ t: impact, type: "kill", gold: stage.gold, xp, monster: stage.monster });
        while (c.xp >= xpNeed(data, c.level)) {
          c.xp -= xpNeed(data, c.level);
          c.level++;
          out.push({ t: impact, type: "levelup", level: c.level });
        }
        c.max = monsterMax(data, stage, c.level);
        c.hp = c.max;
        const spawn = impact + R.respawnMs;
        out.push({ t: spawn, type: "spawn", monster: stage.monster, hp: c.hp, max: c.max });
        end = Math.max(end, spawn);
        break;
      }
    }
    c.t = end;
  }

  // ---------- Luta: cursor + fila de eventos + estado VISÍVEL ----------
  // O cursor gera eventos um pouco no futuro; o estado visível só muda
  // quando o relógio passa pelo evento. Tela, notificação e save usam o visível.
  function Fight(data, seg) {
    this.data = data;
    this.seg = seg;
    this.c = cursorFrom(data, seg);
    this.queue = [];
    this.view = {
      stageId: this.c.stageId, level: seg.level, xp: seg.xp, gold: seg.gold, kills: seg.kills,
      hp: this.c.hp, max: this.c.max, alive: true, t: seg.start,
    };
  }

  // Avança até `now` e devolve os eventos que "aconteceram" nesse intervalo.
  // Uma ação sempre termina depois de todos os seus eventos, então a fila só
  // precisa ser ordenada dentro de cada ação (24 h offline continuam rápidas).
  Fight.prototype.update = function (now) {
    const fired = [];
    const v = this.view;
    const data = this.data;
    const apply = e => {
      if (e.type === "hit") { v.hp = e.hp; v.max = e.max; }
      else if (e.type === "kill") { v.kills++; v.gold += e.gold; v.xp += e.xp; v.alive = false; }
      else if (e.type === "levelup") { v.xp -= xpNeed(data, v.level); v.level = e.level; }
      else if (e.type === "spawn") { v.hp = e.hp; v.max = e.max; v.alive = true; }
      fired.push(e);
    };
    let i = 0;
    while (i < this.queue.length && this.queue[i].t <= now) apply(this.queue[i++]);
    if (i) this.queue = this.queue.slice(i);
    while (this.c.t <= now) {
      const fresh = [];
      step(data, this.c, fresh);
      fresh.sort((a, b) => a.t - b.t);
      for (const e of fresh) (e.t <= now ? apply(e) : this.queue.push(e));
    }
    v.t = now;
    return fired;
  };

  // Começa um trecho novo a partir do que está visível agora
  // (troca de fase, item equipado, abrir o app depois de muito tempo...).
  Fight.prototype.rebase = function (now, stageId) {
    this.update(now);
    const v = this.view;
    const from = { stageId: v.stageId, seed: this.seg.seed, level: v.level, xp: v.xp, gold: v.gold, kills: v.kills, hp: v.alive ? v.hp : 0 };
    return newSegment(this.data, from, now, stageId);
  };

  const Sim = { rand, mixSeed, xpNeed, attack, stageOf, spellsFor, firstSegment, newSegment, cursorFrom, step, Fight };
  root.Sim = Sim;
  if (typeof module !== "undefined" && module.exports) module.exports = Sim;
})(typeof window !== "undefined" ? window : globalThis);
