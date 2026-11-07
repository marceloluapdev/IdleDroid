// Menu de teste: abra o jogo com ?admin (fica ligado no aparelho; ?admin=off desliga).
// Ao criar mecânica nova, adicione aqui um atalho para testá-la.

(function () {
  "use strict";
  const q = location.search;
  try {
    if (/[?&]admin=off\b/.test(q)) localStorage.removeItem("idle.admin");
    else if (/[?&]admin\b/.test(q)) localStorage.setItem("idle.admin", "1");
  } catch (e) {}
  let on = false;
  try { on = localStorage.getItem("idle.admin") === "1"; } catch (e) {}
  if (!on) return;

  const acoes = {
    "Avançar 1 min": () => IdleAdmin.skip(60e3),
    "Avançar 10 min": () => IdleAdmin.skip(600e3),
    "Avançar 1 h": () => IdleAdmin.skip(3600e3),
    "Sair por 8 h": () => IdleAdmin.away(8 * 3600e3),
    "Zerar tudo": () => { if (confirm("Apagar o progresso?")) IdleAdmin.reset(); },
  };

  function montar() {
    const box = document.createElement("div");
    box.className = "admin";
    box.innerHTML = `<button class="admin-toggle">⚙</button><div class="admin-menu" hidden></div>`;
    const menu = box.querySelector(".admin-menu");
    for (const [nome, fn] of Object.entries(acoes)) {
      const b = document.createElement("button");
      b.textContent = nome;
      b.onclick = fn;
      menu.appendChild(b);
    }
    box.querySelector(".admin-toggle").onclick = () => { menu.hidden = !menu.hidden; };
    document.body.appendChild(box);
  }
  if (window.IdleAdmin) montar(); else document.addEventListener("idle-pronto", montar);
})();
