package io.github.marceloluapdev.idledroid;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Papel de parede animado: a luta na tela de bloqueio e atrás dos ícones.
 * - Lê o save que o jogo guarda (SharedPreferences "idledroid", via Janelinha.guardar).
 * - Recalcula a luta com Sim.java (idêntica à do JS) e desenha com Cena.java.
 * - Só anima com a tela visível (20 quadros/s); apagada, não gasta nada.
 */
public class IdleWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new Motor();
    }

    private String lerAsset(String caminho) throws Exception {
        try (InputStream in = getAssets().open(caminho)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private class Motor extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable quadro = this::desenhar;
        private Sim.Data data;
        private Cena cena;
        private Sim.Fight fight;
        private String ultimoSave = "";
        private long relogio = 0, ultimaLeitura = 0;
        private boolean visivel = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            try {
                data = Sim.Data.from(lerAsset("public/data/game.json"));
                cena = new Cena(data);
            } catch (Exception e) {
                data = null;
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            if (cena != null) cena.tamanho(width, height);
            desenhar();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visivel = v;
            handler.removeCallbacks(quadro);
            if (v) {
                ultimaLeitura = 0;
                handler.post(quadro);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visivel = false;
            handler.removeCallbacks(quadro);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(quadro);
            super.onDestroy();
        }

        /** Relê o save do jogo (troca de fase, progresso novo). */
        private void lerSave(long agora) {
            if (data == null) return;
            if (agora - ultimaLeitura < 3000 && fight != null) return;
            ultimaLeitura = agora;
            SharedPreferences p = getSharedPreferences("idledroid", MODE_PRIVATE);
            String raw = p.getString("save", null);
            try { relogio = Long.parseLong(p.getString("relogio", "0")); } catch (Exception e) { relogio = 0; }
            String chave = raw == null ? "" : raw;
            if (fight != null && chave.equals(ultimoSave)) return;
            ultimoSave = chave;
            Sim.Seg seg;
            try {
                seg = raw != null ? Sim.Seg.fromSave(raw) : Sim.Seg.first(data, System.currentTimeMillis(), 1);
            } catch (Exception e) {
                seg = Sim.Seg.first(data, System.currentTimeMillis(), 1);
            }
            fight = new Sim.Fight(data, seg);
            long t = System.currentTimeMillis() + relogio;
            fight.update(t, false);   // pula direto para agora, sem animar o passado
        }

        private void desenhar() {
            if (data == null || cena == null) return;
            long agora = System.currentTimeMillis();
            lerSave(agora);
            long t = agora + relogio;
            cena.onEvents(fight.update(t, true), t);
            SurfaceHolder h = getSurfaceHolder();
            Canvas c = null;
            try {
                c = h.lockCanvas();
                if (c != null) cena.draw(c, t, fight.view);
            } catch (Exception ignored) {
                // superfície pode sumir no meio do quadro
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            handler.removeCallbacks(quadro);
            if (visivel) handler.postDelayed(quadro, 50);
        }
    }
}
