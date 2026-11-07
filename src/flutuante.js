// Modo flutuante: a mesma luta do app, sem fundo, para ficar por cima dos outros apps.
// Só MOSTRA: recalcula a luta a partir do trecho salvo pelo app (a simulação é determinística),
// então fica igual ao que o app mostraria. Quem decide e salva é sempre o app.
(async function () {
  "use strict";

  const data = await (await fetch("data/game.json?v=2")).json();

  // No app o save vem da "Ponte" nativa (FlutuanteService.java); no navegador, do localStorage.
  const ler = (metodo, chave) => {
    try { return window.Ponte ? window.Ponte[metodo]() : localStorage.getItem(chave); } catch (e) { return null; }
  };

  const player = new Player(document.getElementById("stage"), data, { transparente: true });
  let fight = null, offset = 0, ultimoSave = null;
  const now = () => Date.now() + offset;

  function carregar() {
    const raw = ler("save", "idle.save");
    if (!raw || raw === ultimoSave) return;
    ultimoSave = raw;
    let s;
    try { s = JSON.parse(raw); } catch (e) { return; }
    if (!s || s.v !== 1) return;
    offset = +ler("relogio", "idle.relogio") || 0;
    fight = new Sim.Fight(data, s.seg);
    player.fx = [];
    const t = now();
    player.onEvents(fight.update(t), t);
  }

  carregar();
  setInterval(carregar, 3000);   // pega troca de fase feita no app

  (function loop() {
    if (fight) {
      const t = now();
      player.onEvents(fight.update(t), t);
      player.draw(t, fight.view);
    }
    requestAnimationFrame(loop);
  })();

  window.IdleFlutuante = { get fight() { return fight; } };
})();
