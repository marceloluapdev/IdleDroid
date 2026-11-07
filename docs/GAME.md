# IdleDroid — Conceito

## Ideia
Jogo idle de grind para celular (Android), com troca de itens entre jogadores num hub.

## Fluxo do jogador
1. **App aberto (tela cheia):** escolhe a fase, equipa itens e usa o hub de trocas.
2. **Modo idle:** depois de escolher a fase, o herói luta sozinho contra os monstros, soltando magias e efeitos. O jogador só assiste.
3. Esse combate aparece em vários lugares, todos mostrando o mesmo estado:
   - **Janela flutuante (Picture-in-Picture):** como o vídeo minimizado do YouTube.
   - **Tela de bloqueio:** papel de parede animado (Live Wallpaper) com a mesma luta.
   - **Notificação persistente / Live Update:** fase, monstros derrotados, ouro e XP.
   - **Widget na tela de bloqueio** (Android 16 QPR2+, Pixel primeiro): painel de status, sem animação contínua.

## Arquitetura: progresso calculado, não simulado
- O jogo **não roda em segundo plano** (o Android mata processos e gasta bateria).
- Salva só um **trecho**: fase, nível, XP, ouro, abates, vida do monstro, horário de início e semente.
- Ao mostrar qualquer tela, recalcula a luta pelo tempo decorrido (`src/sim.js`).
- **Semente fixa (mulberry32):** mesmo trecho + mesmo instante = mesma luta (magia, crítico, morte).
- **Trecho novo** quando o estado muda: troca de fase, item equipado, troca no hub, ou ao abrir o app (para não recalcular desde o início toda vez).
- A linha do tempo **não é pré-gerada**: é calculada sob demanda. 24 h offline levam poucos ms.
- Vantagem extra: o servidor do hub pode refazer o cálculo e conferir se o loot é legítimo.

### Simulação × estado visível
- O cursor da simulação gera eventos um pouco à frente (uma ação inteira).
- O **estado visível** só muda quando o relógio passa pelo evento. Tela, save e notificação usam o visível.
- Eventos: `cast`, `shot`, `hit`, `kill`, `levelup`, `spawn`.

## Luta orientada a dados (decidido)
- Magias, monstros, fases, sprites e paleta ficam em `data/game.json`.
- Cada magia tem regras (peso, multiplicador, tempos, nº de acertos) e um visual
  (`projectile`, `beam` ou `nova`, cores, arco, rastro, impacto com partículas, tremida, flash).
- Dois **tocadores** leem os mesmos dados: JS (`src/player.js`, app e PiP) e, no futuro, Kotlin (wallpaper).
- Magia nova = só dados. Sprites hoje são grades de caracteres; depois podem virar spritesheets PNG.

### Dois tipos de dado
- **Estado do jogador:** muda com as ações dele (o trecho salvo).
- **Conteúdo do jogo:** muda com atualizações do app ou do servidor (`data/game.json` + imagens).

## Tecnologia
- JavaScript puro sem build; canvas 160×100 ampliado com `image-rendering: pixelated` (legível no PiP).
- Android: Capacitor (o mesmo HTML/JS num WebView). Nativo:
  - PiP: `enterPictureInPictureMode()` na Activity; o WebView continua desenhando (30 fps basta).
  - Notificação: plugin do Capacitor.
  - Live Wallpaper: `WallpaperService` com porte de `sim.js` + `player.js` em Kotlin.
- Experimento só web (já no jogo): botão **Janelinha** = canvas → `captureStream()` → `<video>` → PiP.

## Regras atuais (protótipo)
- Ataque = 6 + 3 por nível. Crítico 12% (×2). Dano varia ±10%.
- XP para subir: 15 × nível^1.8. Acima do `levelCap` da fase, cada abate dá só 1 XP.
- Fases: Floresta Úmida (Gosma, nv 1), Caverna Ecoante (Morcego, nv 4), Cripta Esquecida (Esqueleto, nv 8).
- Magias: Faísca (nv 1), Bola de Fogo (2), Raio (3), Estilhaço de Gelo (5, 3 acertos), Nova Arcana (7).

## Modo flutuante (visual)
- Baseado no desenho do Marcel: personagens no chão, cerca baixa na frente (só os pés), mato perto do herói,
  contorno de montanha bem ao fundo; o meio entre os grupos fica vazado.
- Meta futura: até 3 heróis contra 3 inimigos na cena (ir ajustando nos testes).

## Em aberto
- Backend do hub de trocas (Cloudflare Workers + D1/Durable Objects é candidato).
- Economia de itens, equipamentos e prevenção de trapaça.
- Limite de ganho offline (ex.: 8–12 h) e balanceamento.
- Nome do jogo.
