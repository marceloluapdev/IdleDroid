// Gera os eventos de referência da simulação JS para o teste do Java (SimTest).
// Uso: node tools/gerar-fixture-sim.js  -> android/app/src/test/resources/sim_fixture.json
const fs = require("fs");
const path = require("path");
const Sim = require("../src/sim.js");
const raiz = path.join(__dirname, "..");
const data = JSON.parse(fs.readFileSync(path.join(raiz, "data/game.json"), "utf8"));

const casos = [];
for (const [seed, stageId, level, horas] of [[1, "floresta", 1, 2], [42, "caverna", 5, 1], [3999999999, "cripta", 9, 3], [7, "floresta", 3, 24]]) {
  const seg = Object.assign(Sim.firstSegment(data, 1794000000000, seed), { stageId, level });
  const f = new Sim.Fight(data, seg);
  const ev = f.update(seg.start + horas * 3600e3);
  casos.push({
    seg,
    ate: seg.start + horas * 3600e3,
    primeiros: ev.slice(0, 300).map(e => [e.t, e.type, e.spell || "", e.dmg || 0, e.crit ? 1 : 0, e.level || 0]),
    total: ev.length,
    view: { level: f.view.level, xp: f.view.xp, gold: f.view.gold, kills: f.view.kills, hp: f.view.hp, max: f.view.max, alive: f.view.alive },
  });
}
const saida = path.join(raiz, "android/app/src/test/resources/sim_fixture.json");
fs.mkdirSync(path.dirname(saida), { recursive: true });
fs.writeFileSync(saida, JSON.stringify(casos));
console.log("fixture: " + casos.length + " casos -> " + path.relative(raiz, saida));
