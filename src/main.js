// Liga tudo: carrega os dados, recalcula a luta pelo tempo, desenha e salva.
// O save guarda só o TRECHO (ver src/sim.js); o progresso é sempre recalculado.

(async function () {
  "use strict";

  const SAVE_KEY = "idle.save";
  const CLOCK_KEY = "idle.relogio";      // atraso/avanço do relógio (menu admin)
  const $ = id => document.getElementById(id);

  const data = await (await fetch("data/game.json?v=1")).json();

  let clockOffset = 0;
  try { clockOffset = +localStorage.getItem(CLOCK_KEY) || 0; } catch (e) {}
  const now = () => Date.now() + clockOffset;
  let leaving = false;   // true ao simular saída (admin): não salva por cima

  // ---------- Save ----------
  function loadSave() {
    try { const s = JSON.parse(localStorage.getItem(SAVE_KEY)); if (s && s.v === 1) return s; } catch (e) {}
    return null;
  }
  function writeSave() {
    const v = fight.view;
    const s = { v: 1, seg: fight.seg, seen: { t: now(), gold: v.gold, kills: v.kills, level: v.level } };
    const raw = JSON.stringify(s);
    try { localStorage.setItem(SAVE_KEY, raw); } catch (e) {}
    // No app, o modo flutuante lê o save por aqui (FlutuanteService.java)
    if (nativo) Cap.nativePromise("Janelinha", "guardar", { save: raw, relogio: String(clockOffset) }).catch(() => {});
  }

  // ---------- Janelinha ----------
  // No app Android: PiP nativo (MainActivity.java + JanelinhaPlugin.java).
  // No navegador: experimento canvas -> vídeo -> Picture-in-Picture.
  const Cap = window.Capacitor;
  const nativo = !!(Cap && Cap.isNativePlatform && Cap.isNativePlatform() && Cap.nativePromise);
  const pip = {
    on: false, nativa: false, big: null, g: null, video: null,
    copy() { this.g.drawImage($("stage"), 0, 0, this.big.width, this.big.height); },
  };
  const player = new Player($("stage"), data);
  let fight;

  function start(seg) {
    fight = new Sim.Fight(data, seg);
    player.fx = [];
  }

  // ---------- Início: recalcula o tempo fora ----------
  const save = loadSave();
  const t0 = now();
  if (!save) {
    start(Sim.firstSegment(data, t0, (Math.random() * 4294967296) >>> 0));
  } else {
    start(save.seg);
    player.onEvents(fight.update(t0), t0);
    const v = fight.view, seen = save.seen;
    if (seen && t0 - seen.t > 60e3 && v.kills > seen.kills) {
      showAway(t0 - seen.t, v.kills - seen.kills, v.gold - seen.gold, v.level - seen.level);
    }
    // trecho novo a partir de agora: o próximo carregamento não refaz tudo
    start(fight.rebase(t0));
  }
  writeSave();

  // ---------- Loop ----------
  let lastSave = now();
  function frame() {
    const t = now();
    player.onEvents(fight.update(t), t);
    player.draw(t, fight.view);
    if (pip.on && pip.big) pip.copy();
    hud();
    if (t - lastSave > 5000 && !leaving) { writeSave(); lastSave = t; }
  }
  (function raf() { if (!document.hidden) frame(); requestAnimationFrame(raf); })();
  // Com a aba escondida o requestAnimationFrame para; a janelinha continua por timer.
  setInterval(() => { if (document.hidden && (pip.on || pip.nativa)) frame(); }, 1000 / 20);
  document.addEventListener("visibilitychange", () => { if (document.hidden && !leaving) writeSave(); });
  window.addEventListener("pagehide", () => { if (!leaving) writeSave(); });

  // ---------- HUD em HTML ----------
  function hud() {
    const v = fight.view;
    const stage = Sim.stageOf(data, v.stageId);
    $("stage-name").textContent = stage.name;
    $("gold").textContent = v.gold.toLocaleString("pt-BR");
    $("level").textContent = v.level;
    $("kills").textContent = v.kills.toLocaleString("pt-BR");
    const need = Sim.xpNeed(data, v.level);
    $("xp-fill").style.width = Math.min(100, (v.xp / need) * 100) + "%";
    $("xp-text").textContent = `${v.xp} / ${need}`;
  }

  // ---------- Fases ----------
  function openStages() {
    const list = $("stage-list");
    list.innerHTML = "";
    for (const s of data.stages) {
      const b = document.createElement("button");
      const locked = fight.view.level < s.minLevel;
      const here = s.id === fight.view.stageId;
      b.className = "stage-btn" + (here ? " here" : "");
      b.disabled = locked;
      b.innerHTML = `<strong>${s.name}</strong><small>${locked ? "Nível " + s.minLevel : data.monsters[s.monster].name + " · " + s.gold + " ouro"}</small>`;
      b.onclick = () => {
        if (!here) { start(fight.rebase(now(), s.id)); writeSave(); }
        $("stages").hidden = true;
      };
      list.appendChild(b);
    }
    $("stages").hidden = false;
  }
  $("btn-stages").onclick = openStages;
  $("stages-close").onclick = () => { $("stages").hidden = true; };

  function showAway(ms, kills, gold, levels) {
    const h = Math.floor(ms / 3600e3), m = Math.floor((ms % 3600e3) / 60e3);
    $("away-time").textContent = h ? `${h} h ${m} min` : `${m} min`;
    $("away-text").innerHTML =
      `${kills.toLocaleString("pt-BR")} monstros derrotados<br>+${gold.toLocaleString("pt-BR")} de ouro` +
      (levels > 0 ? `<br>+${levels} nível${levels > 1 ? "s" : ""}` : "");
    $("away").hidden = false;
  }
  $("away-ok").onclick = () => { $("away").hidden = true; };

  // O app avisa quando entra/sai da janelinha: só o palco da luta fica na tela
  window.addEventListener("janelinha", ev => {
    pip.nativa = !!ev.ativa;
    document.body.classList.toggle("janelinha", pip.nativa);
    if (!pip.nativa) writeSave();
  });

  const canPip = nativo || (document.pictureInPictureEnabled && HTMLCanvasElement.prototype.captureStream);
  $("btn-pip").hidden = !canPip;
  $("btn-pip").onclick = async () => {
    if (nativo) {
      try {
        const r = await Cap.nativePromise("Janelinha", "entrar", {});
        if (!r || !r.entrou) alert("Este celular não permite a janelinha (veja se está liberada nas configurações do app).");
      } catch (e) { alert("Janelinha falhou: " + e.message); }
      return;
    }
    try {
      if (document.pictureInPictureElement) { await document.exitPictureInPicture(); return; }
      if (!pip.big) {
        pip.big = document.createElement("canvas");
        pip.big.width = Player.W * 4; pip.big.height = Player.H * 4;
        pip.g = pip.big.getContext("2d"); pip.g.imageSmoothingEnabled = false;
        pip.video = document.createElement("video");
        pip.video.muted = true; pip.video.playsInline = true;
        pip.video.srcObject = pip.big.captureStream(30);
        pip.video.addEventListener("leavepictureinpicture", () => { pip.on = false; });
      }
      pip.on = true; pip.copy();
      await pip.video.play();
      await pip.video.requestPictureInPicture();
    } catch (e) {
      pip.on = false;
      alert("Janelinha não disponível neste navegador: " + e.message);
    }
  };

  // ---------- Modo flutuante (só no app) ----------
  // Herói por cima dos outros apps, com fundo vazado. Precisa de "Sobrepor a outros apps".
  $("btn-flutuar").hidden = !nativo;
  $("btn-flutuar").onclick = async () => {
    try {
      const r = await Cap.nativePromise("Janelinha", "flutuantePermitido", {});
      if (!r || !r.permitido) { $("flut-perm").hidden = false; return; }
      writeSave();
      const raw = localStorage.getItem(SAVE_KEY);
      await Cap.nativePromise("Janelinha", "flutuar", { save: raw, relogio: String(clockOffset) });
    } catch (e) { alert("Modo flutuante falhou: " + e.message); }
  };
  $("flut-perm-ok").onclick = async () => {
    $("flut-perm").hidden = true;
    try {
      await Cap.nativePromise("Janelinha", "pedirNotificacao", {});
      await Cap.nativePromise("Janelinha", "pedirPermissaoFlutuante", {});
    } catch (e) { alert("Não consegui abrir as configurações: " + e.message); }
  };
  $("flut-perm-nao").onclick = () => { $("flut-perm").hidden = true; };

  // ---------- Menu de administrador (?admin) ----------
  window.IdleAdmin = {
    data, get fight() { return fight; }, now,
    skip(ms, salvar = true) {
      clockOffset += ms;
      try { localStorage.setItem(CLOCK_KEY, String(clockOffset)); } catch (e) {}
      if (salvar) writeSave();
    },
    resetClock() { clockOffset = 0; try { localStorage.removeItem(CLOCK_KEY); } catch (e) {} },
    reset() { try { localStorage.removeItem(SAVE_KEY); localStorage.removeItem(CLOCK_KEY); } catch (e) {} location.reload(); },
    // simula fechar o app por `ms` e voltar (mostra o resumo do tempo fora)
    away(ms) { writeSave(); leaving = true; this.skip(ms, false); location.reload(); },
  };
  document.dispatchEvent(new Event("idle-pronto"));
})();
