# App Android (Capacitor)

O app é o mesmo jogo da web dentro de um WebView (Capacitor). Partes nativas (PiP, notificação,
wallpaper) entram depois em `android/`.

## Como o APK é gerado
- `npm run sync` copia o jogo (`index.html`, `src/`, `data/`, `assets/`) para `www/` e atualiza `android/`.
- O GitHub Actions (`.github/workflows/apk.yml`) roda a cada envio na `dev` ou na `main`,
  gera o APK assinado e publica em **Releases** (a `dev` sai como pré-lançamento).
- `versionCode` = número do build no Actions, então cada APK novo atualiza o anterior.

## Chave de assinatura (nunca trocar)
- Pacote: `io.github.marceloluapdev.idledroid`
- Alias: `idledroid`
- SHA-256 do certificado (pedido pelo Android Developer Console ao registrar o app):
  `8E:27:0C:F7:F6:3B:BC:48:91:D7:78:47:AF:C9:D1:9A:86:E4:AA:B5:B2:F7:9C:0B:89:7D:E5:82:ED:27:7B:2B`
- A chave fica só nos segredos do GitHub e no backup do Marcel. Nunca no repositório.
- Segredos do repositório (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Se a chave for perdida, o app não pode mais ser atualizado: seria preciso desinstalar e instalar de novo.

## Instalar no celular (sem conta, pelo PC)
1. Celular: Configurações → Sobre o telefone → tocar 7× em "Número da versão"; depois Opções do desenvolvedor → Depuração USB.
2. PC: baixar o Android SDK Platform-Tools e descompactar.
3. Baixar o `IdleDroid.apk` da página Releases do repositório.
4. Ligar o celular no cabo, aceitar "Permitir depuração USB".
5. Na pasta do platform-tools: `adb install -r caminho/do/IdleDroid.apk` (`-r` atualiza mantendo o progresso).

## Amigos (depois)
- Conta de distribuição limitada no Android Developer Console (gratuita, até 20 aparelhos, sem documento).
  Não dá para trocar de plano depois de criada.
- Registrar o pacote com o SHA-256 acima; cada amigo autoriza o aparelho por link/QR code.
