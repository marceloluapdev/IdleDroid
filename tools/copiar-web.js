// Copia o jogo (o mesmo do GitHub Pages) para www/, que o Capacitor empacota no app.
// O jogo continua sem build: isto só junta os arquivos numa pasta.
const fs = require("fs");
const path = require("path");
const raiz = path.join(__dirname, "..");
const destino = path.join(raiz, "www");
fs.rmSync(destino, { recursive: true, force: true });
for (const item of ["index.html", "src", "data", "assets"]) {
  const origem = path.join(raiz, item);
  if (fs.existsSync(origem)) fs.cpSync(origem, path.join(destino, item), { recursive: true });
}
console.log("jogo copiado para www/");
