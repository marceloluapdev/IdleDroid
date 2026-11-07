# TODO

## Feito
- [x] Simulação determinística por trechos com semente (`src/sim.js`) + testes
- [x] Conteúdo em dados (`data/game.json`): 3 fases, 3 monstros, 5 magias
- [x] Tocador em canvas: projéteis em arco, raio, nova, impactos, tremida, números de dano, moedas, subida de nível
- [x] Save só do trecho; resumo "Enquanto você estava fora"
- [x] Escolha de fase (liberada por nível)
- [x] Menu `?admin`: avançar tempo, sair por 8 h, zerar
- [x] Experimento de janelinha (PiP) só com web
- [x] Menu de teste no app: 7 toques em "Nível"
- [x] App Android com Capacitor + APK assinado gerado pelo GitHub Actions (ver ANDROID.md)

## Próximos
- [ ] Testar o botão Janelinha no Chrome do Android (anotar se funciona e se congela fora da aba)
- [ ] Limite de ganho offline e balanceamento de XP/ouro
- [ ] Monstros revidando (vida do herói, derrota/recuo)
- [ ] Itens: drop, inventário, equipamento (equipar = trecho novo)
- [x] PiP nativo no app (MainActivity.java + JanelinhaPlugin.java): entra sozinho ao sair do app e pelo botão Janelinha
- [ ] Notificação persistente com o progresso
- [ ] Portar `sim.js` + `player.js` para Kotlin e fazer o Live Wallpaper
- [ ] Teste que compara a luta do JS com a do Kotlin (mesma semente → mesmos eventos)
- [ ] Antes dos amigos: `ADMIN_PADRAO = false` em `src/admin.js` (esconder o menu ⚙)
- [ ] Conta de distribuição limitada (amigos, até 20 aparelhos) e registrar o pacote
- [ ] Hub de trocas (servidor) e validação do loot refazendo o cálculo
- [ ] Trocar sprites de grade por spritesheets (PixelLab ou arte gratuita)
