// Testes da simulação. Rode: node tests/sim.test.js
// Adicione um caso para cada regra nova ou bug corrigido.

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const Sim = require("../src/sim.js");
const data = JSON.parse(fs.readFileSync(path.join(__dirname, "../data/game.json"), "utf8"));

const casos = {
  "mesma semente e mesmo tempo dão o mesmo resultado"() {
    const seg = Sim.firstSegment(data, 1000, 42);
    const a = new Sim.Fight(data, seg); a.update(1000 + 3600e3);
    const b = new Sim.Fight(data, seg); b.update(1000 + 3600e3);
    assert.deepStrictEqual(a.view, b.view);
    assert.ok(a.view.kills > 0);
  },

  "atualizar aos poucos ou de uma vez dá o mesmo resultado"() {
    const seg = Sim.firstSegment(data, 0, 7);
    const aos = new Sim.Fight(data, seg);
    for (let t = 0; t <= 600e3; t += 16) aos.update(t);
    const uma = new Sim.Fight(data, seg); uma.update(600e3);
    assert.deepStrictEqual(aos.view, uma.view);
  },

  "sementes diferentes dão lutas diferentes"() {
    const a = new Sim.Fight(data, Sim.firstSegment(data, 0, 1)); a.update(300e3);
    const b = new Sim.Fight(data, Sim.firstSegment(data, 0, 2)); b.update(300e3);
    assert.notDeepStrictEqual(a.view, b.view);
  },

  "eventos saem em ordem de tempo"() {
    const f = new Sim.Fight(data, Sim.firstSegment(data, 0, 3));
    const ev = f.update(120e3);
    for (let i = 1; i < ev.length; i++) assert.ok(ev[i].t >= ev[i - 1].t);
  },

  "sobe de nível e libera magias novas"() {
    const f = new Sim.Fight(data, Sim.firstSegment(data, 0, 9));
    const ev = f.update(3600e3);
    assert.ok(f.view.level >= 3, "nível " + f.view.level);
    const usadas = new Set(ev.filter(e => e.type === "cast").map(e => e.spell));
    assert.ok(usadas.has("bola-de-fogo") && usadas.has("raio"));
  },

  "rebase mantém o progresso e troca de fase"() {
    const f = new Sim.Fight(data, Sim.firstSegment(data, 0, 5));
    const seg = f.rebase(900e3, "caverna");
    assert.strictEqual(seg.stageId, "caverna");
    assert.strictEqual(seg.gold, f.view.gold);
    assert.strictEqual(seg.level, f.view.level);
    assert.strictEqual(seg.hp, null);
    const g = new Sim.Fight(data, seg); g.update(seg.start + 60e3);
    assert.ok(g.view.gold >= seg.gold);
  },

  "24 horas offline calculam rápido"() {
    const t = Date.now();
    const f = new Sim.Fight(data, Sim.firstSegment(data, 0, 11));
    f.update(24 * 3600e3);
    assert.ok(Date.now() - t < 1500, (Date.now() - t) + " ms");
  },

  "sprites só usam cores que existem"() {
    const all = [data.hero.sprite, ...Object.values(data.monsters).map(m => m.sprite)];
    for (const s of all)
      for (const row of s.rows)
        for (const ch of row)
          if (ch !== ".") assert.ok(data.palette[s.colors[ch]], "cor faltando: " + ch);
    for (const sp of data.spells) assert.ok(data.palette[sp.visual.color], sp.id);
  },
};

let falhas = 0;
for (const [nome, fn] of Object.entries(casos)) {
  try { fn(); console.log("ok   " + nome); }
  catch (e) { falhas++; console.log("FALHA " + nome + "\n     " + e.message); }
}
console.log(falhas ? `${falhas} falha(s)` : `todos os ${Object.keys(casos).length} passaram`);
process.exit(falhas ? 1 : 0);
