package io.github.marceloluapdev.idledroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Desenho da luta para o papel de parede (porte de src/player.js, modo "cena completa").
 * Desenha numa imagem pequena (160 px de largura, altura na proporção da tela) e amplia
 * sem borrar. Só lê eventos da simulação: não decide nada.
 */
public final class Cena {

    private static final int W = 160;
    private static final int FLOOR = 84;      // chão em coordenadas da cena (igual ao JS)
    private static final int HERO_X = 22;
    private static final int MON_X = 120;
    private static final int PERIODO = 160;
    private static final int[] CERCA_HEROI = { 4, 60 };
    private static final int[][] CERCA_MONSTRO = { { 96, 108 }, { 133, 157 } };

    private static final String[] FONTE_CHARS = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "!", "N", "I", "V", "E", "L", "U", "P", "X", "C", "R", "T", "O", "K", " " };
    private static final String[] FONTE_BITS = {
        "111101101101111", "010110010010111", "111001111100111", "111001111001111",
        "101101111001001", "111100111001111", "111100111101111", "111001010010010",
        "111101111101111", "111101111001111", "000010111010000", "010010010000010",
        "101111111111101", "111010010010111", "101101101101010", "111100110100111",
        "100100100100111", "101101101101111", "111101111100100", "101101010101101",
        "111100100100111", "110101110101101", "111010010010010", "111101101101111",
        "101101110101101", "000000000000000" };

    private final Sim.Data data;
    private final Map<String, Integer> pal = new HashMap<>();
    private final Map<String, Map<String, Object>> visuais = new HashMap<>();
    private final Paint tinta = new Paint();
    private final Paint ampliar = new Paint();

    private Bitmap buf;
    private Canvas g;
    private int H = 300;     // altura lógica (depende da tela)
    private int oy = 0;      // deslocamento vertical da cena
    private int telaW = 1, telaH = 1;

    private Bitmap heroi;
    private int[] pontaCajado = { 11, 2 };
    private final Map<String, Bitmap[]> monstros = new HashMap<>(); // [normal, branco]
    private final Map<String, Map<String, Object>> defMonstros = new HashMap<>();
    private final Map<String, Bitmap> montanhas = new HashMap<>();

    private final List<Sim.Ev> fx = new ArrayList<>();
    private long deathT = -1_000_000_000_000L, spawnT = -1_000_000_000_000L, lastHitT = -1_000_000_000_000L;
    private int walks = 0;
    private double walkP = 0, cam = 0;

    public Cena(Sim.Data data) {
        this.data = data;
        ampliar.setFilterBitmap(false);
        ampliar.setAntiAlias(false);
        tinta.setAntiAlias(false);
        Map<String, Object> p = Json.obj(data.raw.get("palette"));
        for (Map.Entry<String, Object> e : p.entrySet()) pal.put(e.getKey(), Color.parseColor((String) e.getValue()));
        for (Sim.Spell s : data.spells) visuais.put(s.id, s.visual);
        Map<String, Object> hero = Json.obj(Json.obj(data.raw.get("hero")).get("sprite"));
        heroi = sprite(hero, null);
        List<Object> tip = Json.list(hero.get("staffTip"));
        if (tip != null) pontaCajado = new int[] { (int) (double) (Double) tip.get(0), (int) (double) (Double) tip.get(1) };
        Map<String, Object> ms = Json.obj(data.raw.get("monsters"));
        for (Map.Entry<String, Object> e : ms.entrySet()) {
            Map<String, Object> def = Json.obj(e.getValue());
            Map<String, Object> spr = Json.obj(def.get("sprite"));
            monstros.put(e.getKey(), new Bitmap[] { sprite(spr, null), sprite(spr, "white") });
            defMonstros.put(e.getKey(), def);
        }
    }

    // ---------- Imagens ----------
    private int cor(String nome) {
        Integer c = pal.get(nome);
        if (c != null) return c;
        try { return Color.parseColor(nome); } catch (Exception e) { return Color.MAGENTA; }
    }

    private Bitmap sprite(Map<String, Object> spr, String so) {
        List<Object> rows = Json.list(spr.get("rows"));
        Map<String, Object> cores = Json.obj(spr.get("colors"));
        int h = rows.size(), w = 0;
        for (Object r : rows) w = Math.max(w, ((String) r).length());
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            String row = (String) rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                char ch = row.charAt(x);
                if (ch == '.') continue;
                String nome = so != null ? so : (String) cores.get(String.valueOf(ch));
                if (nome != null) b.setPixel(x, y, cor(nome));
            }
        }
        return b;
    }

    /** Ajusta ao tamanho da tela: largura lógica 160, altura proporcional. */
    public void tamanho(int largura, int altura) {
        telaW = Math.max(1, largura);
        telaH = Math.max(1, altura);
        H = Math.max(110, Math.round(W * (float) telaH / telaW));
        oy = Math.round(H * 0.70f) - FLOOR;  // chão a ~70% da altura (abaixo do relógio)
        buf = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        g = new Canvas(buf);
        montanhas.clear();
    }

    // ---------- Desenho básico (coordenadas da cena; oy aplicado aqui) ----------
    private void px(String c, double x, double y, int w, int h) {
        tinta.setColor(cor(c));
        int xi = (int) Math.round(x), yi = (int) Math.round(y) + oy;
        g.drawRect(xi, yi, xi + w, yi + h, tinta);
    }

    private void px(String c, double x, double y) {
        px(c, x, y, 1, 1);
    }

    private void line(String c, double fx0, double fy0, double fx1, double fy1) {
        int x0 = (int) Math.round(fx0), y0 = (int) Math.round(fy0), x1 = (int) Math.round(fx1), y1 = (int) Math.round(fy1);
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int n = 0; n < 400; n++) {
            px(c, x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private static int textW(String s, int esc) {
        return s.length() * 4 * esc - esc;
    }

    private void text(String s, double x, double y, String c, int esc) {
        int cx = (int) Math.round(x);
        for (char ch : s.toUpperCase().toCharArray()) {
            String bits = FONTE_BITS[FONTE_BITS.length - 1];
            for (int k = 0; k < FONTE_CHARS.length; k++) if (FONTE_CHARS[k].charAt(0) == ch) { bits = FONTE_BITS[k]; break; }
            for (int i = 0; i < 15; i++) {
                if (bits.charAt(i) != '1') continue;
                int gx = cx + (i % 3) * esc, gy = (int) Math.round(y) + (i / 3) * esc;
                px("ink", gx + esc, gy + esc, esc, esc);
                px(c, gx, gy, esc, esc);
            }
            cx += 4 * esc;
        }
    }

    private void ring(double cx, double cy, double r, String c) {
        if (r <= 0) return;
        int n = Math.max(8, (int) Math.round(r * 5));
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2;
            px(c, cx + Math.cos(a) * r, cy + Math.sin(a) * r * 0.7);
        }
    }

    private void imagem(Bitmap b, double x, double y) {
        g.drawBitmap(b, (int) Math.round(x), (int) Math.round(y) + oy, null);
    }

    // Aleatório só para enfeite (partículas)
    private static final class Prng {
        int s;
        Prng(long seed) { s = (int) (seed * 2654435761L); }
        double next() {
            s += 0x6d2b79f5;
            int t = s;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
        }
    }

    private static double clamp01(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    private static double easeOut(double p) {
        return 1 - (1 - p) * (1 - p);
    }

    // ---------- Geometria ----------
    private double heroY(long now) {
        long passo = walkP > 0 && walkP < 1 ? (now / 110) % 2 : (now / 450) % 2;
        return FLOOR - heroi.getHeight() + passo;
    }

    private double[] ponta(long now) {
        return new double[] { HERO_X + pontaCajado[0], heroY(now) + pontaCajado[1] };
    }

    private double[] alvo(Sim.Stage st) {
        Bitmap m = monstros.get(st.monster)[0];
        double hover = Json.num(defMonstros.get(st.monster), "hover", 0);
        double y = FLOOR - m.getHeight() - hover;
        return new double[] { MON_X, y + m.getHeight() / 2.0 };
    }

    // ---------- Eventos -> efeitos ----------
    public void onEvents(List<Sim.Ev> fired, long now) {
        if (fired == null) return;
        for (Sim.Ev e : fired) {
            switch (e.type) {
                case "kill": deathT = e.t; break;
                case "spawn": spawnT = e.t; walks++; break;
                case "hit": lastHitT = e.t; break;
                default: break;
            }
            if (now - e.t > 2500) continue;
            if (!e.type.equals("spawn")) fx.add(e);
        }
    }

    private void updateWalk(long now) {
        walkP = 0;
        if (deathT > spawnT) {
            long ini = deathT + 450, fim = deathT + data.respawnMs;
            double p = clamp01((now - ini) / (double) Math.max(1, fim - ini));
            walkP = p < 0.5 ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p);
        }
        cam = (walks % 64) * PERIODO + walkP * PERIODO;
    }

    // ---------- Quadro ----------
    public void draw(Canvas tela, long now, Sim.View v) {
        if (g == null) return;
        Sim.Stage st = data.stage(v.stageId);
        updateWalk(now);

        // tremida
        double shake = 0;
        for (Sim.Ev e : fx) {
            if (!e.type.equals("hit")) continue;
            Map<String, Object> im = Json.obj(visuais.get(e.spell).get("impact"));
            double p = (now - e.t) / 220.0;
            if (p >= 0 && p < 1) shake = Math.max(shake, Json.num(im, "shake", 0) * (e.crit ? 1.5 : 1) * (1 - p));
        }
        int sx = shake > 0 ? (int) Math.round(((now / 30) % 2 == 1 ? 1 : -1) * shake) : 0;
        int sy = shake > 0 ? (int) Math.round(((now / 45) % 2 == 1 ? 1 : -1) * shake * 0.5) : 0;

        g.save();
        g.translate(sx, sy);
        ceu(st);
        g.save();
        g.translate((float) -(((cam * 0.12 + now * 0.001) % (W * 2)) + W * 2) % (W * 2), 0);
        Bitmap mt = montanhaStrip(st);
        g.drawBitmap(mt, 0, 0, null);
        g.drawBitmap(mt, W * 2, 0, null);
        g.restore();
        terreno(st);
        imagem(heroi, HERO_X, heroY(now));
        monstro(now, st, v);
        List<Sim.Ev> manter = new ArrayList<>();
        for (Sim.Ev e : fx) if (efeito(e, now, st)) manter.add(e);
        fx.clear();
        fx.addAll(manter);
        frente(now, st);
        g.restore();
        hud(v);

        tela.drawColor(Color.BLACK);
        tela.drawBitmap(buf, new Rect(0, 0, W, H), new Rect(0, 0, telaW, telaH), ampliar);
    }

    private void ceu(Sim.Stage st) {
        String topo = Json.str(st.bg, "top"), base = Json.str(st.bg, "bottom");
        int meio = FLOOR + oy - 20;
        tinta.setColor(cor(topo));
        g.drawRect(0, -4, W + 4, meio, tinta);
        // faixa pontilhada entre as duas cores
        for (int y = meio - 16; y < meio; y++) {
            for (int x = (y % 2 == 0 ? 0 : 1); x < W; x += (y < meio - 8 ? 4 : 2)) {
                tinta.setColor(cor(base));
                g.drawRect(x, y, x + 1, y + 1, tinta);
            }
        }
        tinta.setColor(cor(base));
        g.drawRect(0, meio, W + 4, H + 4, tinta);
        // estrelinhas fixas no alto
        Prng r = new Prng(st.id.length() * 31L + 5);
        for (int i = 0; i < 26; i++) {
            int x = (int) (r.next() * W), y = (int) (r.next() * (meio - 30));
            tinta.setColor(cor(i % 5 == 0 ? "bone" : "lilac"));
            g.drawRect(x, y, x + 1, y + 1, tinta);
        }
        // terra abaixo do chão
        tinta.setColor(cor("ink"));
        g.drawRect(0, FLOOR + oy + 5, W + 4, H + 4, tinta);
    }

    private Bitmap montanhaStrip(Sim.Stage st) {
        Bitmap b = montanhas.get(st.id);
        if (b != null) return b;
        int sw = W * 2;
        b = Bitmap.createBitmap(sw, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        Prng r = new Prng(st.id.length() * 53L + 11);
        double a0 = r.next() * 6, a1 = r.next() * 6, a2 = r.next() * 6;
        for (int x = 0; x < sw; x++) {
            double t = (double) x / sw * Math.PI * 2;
            double h = 9 + Math.sin(t * 2 + a0) * 5 + Math.sin(t * 5 + a1) * 3 + Math.sin(t * 9 + a2) * 1.5;
            int top = (int) Math.round(FLOOR - 2 - h) + oy;
            p.setColor(cor("dusk"));
            c.drawRect(x, top, x + 1, FLOOR + oy, p);
            p.setColor(cor(h > 14 ? "bone" : "violet"));
            c.drawRect(x, top, x + 1, top + 1, p);
        }
        montanhas.put(st.id, b);
        return b;
    }

    private void cadaPeriodo(java.util.function.IntConsumer fn) {
        double off = -(cam % PERIODO);
        for (double base = off; base < W; base += PERIODO) fn.accept((int) Math.round(base));
    }

    private void chao(Sim.Stage st, int x0, int x1) {
        String linha = Json.str(st.bg, "floorLine"), chao = Json.str(st.bg, "floor");
        px(linha, x0, FLOOR, x1 - x0, 1);
        px(chao, x0, FLOOR + 1, x1 - x0, 3);
        px(chao, x0 + 2, FLOOR + 4, x1 - x0 - 4, 1);
    }

    private void ponte(int x0, int x1) {
        for (int x = x0; x < x1; x += 3) {
            px("ink", x, FLOOR, 3, 2);
            px("wood", x, FLOOR, 2, 1);
        }
    }

    private void terreno(Sim.Stage st) {
        cadaPeriodo(b -> {
            chao(st, b + 2, b + 64);
            chao(st, b + 92, b + 158);
            ponte(b + 62, b + 94);
            ponte(b + 156, b + 164);
        });
    }

    private void cerca(int x0, int x1) {
        int top = FLOOR - 3;
        px("wood", x0, top + 2, x1 - x0, 1);
        for (int x = x0; x <= x1 - 1; x += 9) {
            px("ink", x - 1, top - 1, 3, 7);
            px("wood", x, top, 1, 5);
        }
    }

    private void frente(long now, Sim.Stage st) {
        int sway = (int) Math.round(Math.sin(now / 500.0));
        int[][] tufos = { { 6, 6 }, { 12, 4 }, { 40, 5 }, { 46, 7 }, { 53, 4 }, { 99, 4 }, { 150, 5 } };
        cadaPeriodo(base -> {
            for (int[] t : tufos) {
                for (int k = 0; k < 3; k++) {
                    int hh = t[1] - (k == 1 ? 0 : 2);
                    px(k == 1 ? "green" : "greenD", base + t[0] + k * 2 + (k == 1 ? sway : 0), FLOOR + 2 - hh, 1, hh);
                }
            }
            cerca(base + CERCA_HEROI[0], base + CERCA_HEROI[1]);
            for (int[] c : CERCA_MONSTRO) cerca(base + c[0], base + c[1]);
        });
        String deco = Json.str(st.bg, "deco");
        String col = "trees".equals(deco) ? "green" : "graves".equals(deco) ? "lilac" : "gray";
        for (int i = 0; i < 5; i++) {
            double sp = 0.006 + i * 0.002;
            double x = W - ((now * sp + i * 41) % (W + 20)) + 10;
            double y = FLOOR - 26 + ((i * 7) % 18) + Math.sin(now / 600.0 + i) * 3;
            px(col, x, y, i % 2 == 1 ? 1 : 2, 1);
        }
    }

    private void monstro(long now, Sim.Stage st, Sim.View v) {
        Bitmap[] m = monstros.get(st.monster);
        Map<String, Object> def = defMonstros.get(st.monster);
        double hover = Json.num(def, "hover", 0);
        double x = MON_X - Math.floor(m[0].getWidth() / 2.0), y = FLOOR - m[0].getHeight() - hover;
        boolean morto = deathT > spawnT;
        double dx = 0, dy = 0;
        String idle = Json.str(def, "idle");
        if ("flap".equals(idle)) dy += Math.round(Math.sin(now / 160.0) * 2);
        if ("sway".equals(idle)) dx += (now / 500) % 2;
        if ("squish".equals(idle)) dy += (now / 300) % 2;
        Bitmap img = now - lastHitT < 90 ? m[1] : m[0];
        if (now - lastHitT < 140) dx += 2;

        if (morto) {
            if (walkP > 0) imagem(m[0], x + PERIODO * (1 - walkP), y);
            double p = clamp01((now - deathT) / 450.0);
            if (p < 1) {
                // some em pontilhado
                Bitmap tmp = m[0].copy(Bitmap.Config.ARGB_8888, true);
                Prng r = new Prng(deathT);
                for (int yy = 0; yy < tmp.getHeight(); yy++)
                    for (int xx = 0; xx < tmp.getWidth(); xx++) if (r.next() < p) tmp.setPixel(xx, yy, Color.TRANSPARENT);
                imagem(tmp, x + dx, y + dy + Math.round(p * 4));
            }
            return;
        }
        imagem(img, x + dx, y + dy);
        int bw = 26;
        double bx = MON_X - bw / 2.0, by = y - 6 + (hover > 0 ? -2 : 0);
        px("ink", bx - 1, by - 1, bw + 2, 4);
        px("dusk", bx, by, bw, 2);
        px("red", bx, by, Math.max(0, (int) Math.round(bw * (double) v.hp / Math.max(1, v.max))), 2);
    }

    private boolean efeito(Sim.Ev e, long now, Sim.Stage st) {
        long age = now - e.t;
        if (age < 0) return true;
        double[] tip = ponta(now), tgt = alvo(st);
        Map<String, Object> vis = e.spell != null ? visuais.get(e.spell) : null;
        switch (e.type) {
            case "cast": {
                double p = age / (double) Math.max(1, e.ms);
                if (p >= 1) return false;
                String c1 = Json.str(vis, "color"), c2 = Json.str(vis, "color2");
                int size = (int) Json.num(vis, "size", 1);
                Prng r = new Prng(e.t);
                int n = 6 + size * 2;
                for (int i = 0; i < n; i++) {
                    double a = r.next() * Math.PI * 2, d = (1 - p) * (8 + r.next() * 6);
                    px(i % 2 == 1 ? c1 : c2, tip[0] + Math.cos(a) * d, tip[1] + Math.sin(a) * d);
                }
                int s = 1 + (int) Math.round(p * size);
                px(c2, tip[0] - s / 2, tip[1] - s / 2, s, s);
                if ("nova".equals(Json.str(vis, "kind"))) ring(tgt[0], tgt[1], 16 - Math.round(p * 6), c1);
                return true;
            }
            case "shot": {
                String kind = Json.str(vis, "kind"), c1 = Json.str(vis, "color"), c2 = Json.str(vis, "color2");
                if ("nova".equals(kind)) return false;
                if ("beam".equals(kind)) {
                    long dur = Math.max(e.ms, 200);
                    if (age > dur) return false;
                    Prng r = new Prng(e.t + age / 50);
                    double x0 = tip[0], y0 = tip[1];
                    for (int i = 1; i <= 8; i++) {
                        double k = i / 8.0;
                        double x1 = i == 8 ? tgt[0] : tip[0] + (tgt[0] - tip[0]) * k;
                        double y1 = i == 8 ? tgt[1] : tip[1] + (tgt[1] - tip[1]) * k + (r.next() - 0.5) * 12;
                        line(c1, x0, y0 - 1, x1, y1 - 1);
                        line(c2, x0, y0, x1, y1);
                        x0 = x1;
                        y0 = y1;
                    }
                    return true;
                }
                double p = age / (double) Math.max(1, e.ms);
                if (p >= 1) return false;
                double arc = Json.num(vis, "arc", 0) * (e.i % 2 == 1 ? -0.6 : 1);
                int size = (int) Json.num(vis, "size", 1), trail = (int) Json.num(vis, "trail", 0);
                for (int j = trail; j >= 1; j--) {
                    double k = p - j * 0.035;
                    if (k < 0) continue;
                    double qx = tip[0] + (tgt[0] - tip[0]) * k, qy = tip[1] + (tgt[1] - tip[1]) * k - Math.sin(Math.PI * k) * arc;
                    int s = Math.max(1, (int) Math.round(size * (1 - j / (double) (trail + 1))));
                    px(j % 2 == 1 ? c1 : c2, qx - s / 2.0, qy - s / 2.0, s, s);
                }
                double hx = tip[0] + (tgt[0] - tip[0]) * p, hy = tip[1] + (tgt[1] - tip[1]) * p - Math.sin(Math.PI * p) * arc;
                px(c1, hx - size / 2.0 - 1, hy - size / 2.0 - 1, size + 2, size + 2);
                px(c2, hx - size / 2.0, hy - size / 2.0, size, size);
                return true;
            }
            case "hit": {
                Map<String, Object> im = Json.obj(vis.get("impact"));
                String c1 = Json.str(vis, "color"), c2 = Json.str(vis, "color2");
                double ms = Json.num(im, "ms", 300), p = age / ms, pn = age / 800.0;
                if (pn < 1) {
                    int esc = e.crit ? 2 : 1;
                    String label = e.crit ? e.dmg + "!" : String.valueOf(e.dmg);
                    double nx = tgt[0] - textW(label, esc) / 2.0 + e.i * 6 - 4;
                    double ny = tgt[1] - 12 - easeOut(pn) * 14 - e.i * 4;
                    if (!(pn > 0.7 && (now / 60) % 2 == 1)) text(label, nx, ny, e.crit ? "amber" : "white", esc);
                }
                if (p < 1) {
                    if (Boolean.TRUE.equals(im.get("flash")) && age < 70) {
                        tinta.setColor(cor(c2));
                        tinta.setAlpha(64);
                        g.drawRect(0, 0, W, H, tinta);
                        tinta.setAlpha(255);
                    }
                    Prng r = new Prng(e.t + e.i);
                    int n = (int) Json.num(im, "particles", 6);
                    double rad = Json.num(im, "radius", 8);
                    for (int i = 0; i < n; i++) {
                        double a = r.next() * Math.PI * 2, sp = 0.4 + r.next() * 0.6;
                        double d = easeOut(p) * rad * sp * (e.crit ? 1.4 : 1);
                        int s = p < 0.5 ? 2 : 1;
                        px(i % 3 != 0 ? c1 : c2, tgt[0] + Math.cos(a) * d, tgt[1] + Math.sin(a) * d * 0.8 + p * p * 6, s, s);
                    }
                    if ("nova".equals(Json.str(vis, "kind"))) ring(tgt[0], tgt[1], easeOut(p) * rad * 1.4, c2);
                }
                return pn < 1;
            }
            case "kill": {
                double p = age / 900.0;
                if (p >= 1) return false;
                Prng r = new Prng(e.t * 3);
                double hudX = 6, hudY = FLOOR - 40;
                for (int i = 0; i < 5; i++) {
                    double k = clamp01((age - i * 70) / 600.0);
                    double sx0 = tgt[0] + (r.next() - 0.5) * 12;
                    if (k <= 0 || k >= 1) continue;
                    double x = sx0 + (hudX - sx0) * easeOut(k), y = tgt[1] + (hudY - tgt[1]) * k - Math.sin(Math.PI * k) * 18;
                    px("orange", x - 1, y - 1, 3, 3);
                    px("amber", x - 1, y - 1, 2, 2);
                }
                String label = "+" + e.gold;
                text(label, tgt[0] - textW(label, 1) / 2.0, tgt[1] - 4 - p * 10, "amber", 1);
                return true;
            }
            case "levelup": {
                double p = age / 1800.0;
                if (p >= 1) return false;
                ring(HERO_X + 6, heroY(now) + 8, 4 + easeOut(clamp01(p * 3)) * 14, "amber");
                String label = "NIVEL " + e.level;
                int bounce = (int) Math.round(Math.abs(Math.sin(p * Math.PI * 3)) * 3 * (1 - p));
                text(label, W / 2.0 - textW(label, 1) / 2.0, FLOOR - 52 - bounce, "amber", 1);
                return true;
            }
            default:
                return false;
        }
    }

    private void hud(Sim.View v) {
        double y = FLOOR - 42;
        px("amber", 2, y, 4, 4);
        px("orange", 3, y + 1, 3, 3);
        px("amber", 3, y + 1, 2, 2);
        text(String.valueOf(v.gold), 8, y, "white", 1);
        text("NV" + v.level, 2, y + 7, "lilac", 1);
        long need = Sim.xpNeed(data, v.level);
        px("ink", 2, y + 14, 26, 3);
        px("teal", 3, y + 15, (int) Math.round(24 * Math.min(1, v.xp / (double) Math.max(1, need))), 1);
    }
}
