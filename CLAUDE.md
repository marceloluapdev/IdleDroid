# CLAUDE.md

Jogo idle 2D para Android (HTML/CSS/JS puro, sem build), publicado no GitHub Pages e,
no futuro, empacotado com Capacitor. Desenvolvido principalmente pelo celular — mantenha tudo
testável no navegador mobile.

Leia só quando precisar:
- `docs/GAME.md` — conceito, arquitetura (luta calculada por trechos + dados), decisões
- `docs/TODO.md` — próximos passos e o que já foi feito
- `docs/ANDROID.md` — app Android (Capacitor), APK no GitHub Actions, chave de assinatura, instalação

Antes de propor algo de design ou arquitetura, leia o GAME.md e o TODO.md.

Convenções:
- `src/sim.js` = simulação determinística (sem Date.now, sem Math.random; tudo pela semente).
  Vai ser portada para Kotlin (wallpaper): mantenha simples e sem dependências.
- `src/player.js` = tocador: só desenha eventos da simulação; não decide nada.
- `src/main.js` = save, relógio, HUD, fases, janelinha. `src/admin.js` = menu de teste.
- `data/game.json` = conteúdo (paleta, sprites, monstros, fases, magias, regras). Magia nova = dados, não código.
- Paleta fixa: as cores de `data/game.json` (espelhadas em `:root` no `src/style.css`). Use só essas.
- Textos do jogo em português.

Branches: trabalhe sempre na `dev`. A `main` é a versão estável publicada no GitHub Pages
(https://marceloluapdev.github.io/IdleDroid/). Só leve a `dev` para a `main` quando o Marcel pedir,
depois de ele testar no celular. Prévia da `dev` no celular:
https://raw.githack.com/marceloluapdev/IdleDroid/dev/index.html?admin

App Android: `android/` é gerado pelo Capacitor (só edite o nativo de propósito). Depois de mudar o jogo,
não precisa fazer nada: o Actions roda `npm run sync` e gera o APK. Não crie arquivos dentro de `www/`.

Teste manual no celular: abra com `?admin` (fica ligado no aparelho; `?admin=off` desliga).
Ao criar mecânica nova, adicione um atalho no menu de `src/admin.js`.

Ao mudar CSS/JS/JSON, aumente o `?v=N` deles no `index.html` (e o do `fetch` em `main.js` para o JSON).

Testes: `python3 tests/run_tests.py` (Playwright + `node tests/sim.test.js`).
Adicione um caso para cada regra nova ou bug corrigido. Leia só as últimas linhas do resultado.
