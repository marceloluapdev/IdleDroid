package io.github.marceloluapdev.idledroid;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * A simulação em Java (wallpaper) tem de dar exatamente a mesma luta que a do JS.
 * Os casos vêm de tools/gerar-fixture-sim.js (o Actions gera antes de rodar este teste).
 */
public class SimTest {

    static String ler(String... candidatos) throws Exception {
        for (String c : candidatos) {
            Path p = Paths.get(c);
            if (Files.exists(p)) return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("não achei " + candidatos[0]);
    }

    @Test
    public void mesmaLutaQueOJs() throws Exception {
        Sim.Data data = Sim.Data.from(ler("../../data/game.json", "data/game.json", "src/main/assets/public/data/game.json"));
        List<Object> casos = Json.list(Json.parse(ler("src/test/resources/sim_fixture.json",
            "android/app/src/test/resources/sim_fixture.json")));
        for (Object o : casos) {
            Map<String, Object> caso = Json.obj(o);
            Map<String, Object> s = Json.obj(caso.get("seg"));
            Sim.Seg seg = new Sim.Seg();
            seg.stageId = Json.str(s, "stageId");
            seg.start = (long) Json.num(s, "start", 0);
            seg.seed = (int) (long) Json.num(s, "seed", 0);
            seg.level = (int) Json.num(s, "level", 1);
            Sim.Fight f = new Sim.Fight(data, seg);
            List<Sim.Ev> ev = f.update((long) Json.num(caso, "ate", 0), true);
            String nome = seg.stageId + "/" + seg.seed;

            assertEquals(nome + " total de eventos", (long) Json.num(caso, "total", 0), ev.size());
            List<Object> prim = Json.list(caso.get("primeiros"));
            for (int i = 0; i < prim.size(); i++) {
                List<Object> e = Json.list(prim.get(i));
                Sim.Ev x = ev.get(i);
                String onde = nome + " evento " + i;
                assertEquals(onde + " t", (long) (double) (Double) e.get(0), x.t);
                assertEquals(onde + " tipo", e.get(1), x.type);
                assertEquals(onde + " magia", e.get(2), x.spell == null ? "" : x.spell);
                assertEquals(onde + " dano", (int) (double) (Double) e.get(3), x.dmg);
                assertEquals(onde + " crítico", (int) (double) (Double) e.get(4), x.crit ? 1 : 0);
                assertEquals(onde + " nível", (int) (double) (Double) e.get(5), x.level);
            }
            Map<String, Object> v = Json.obj(caso.get("view"));
            assertEquals(nome + " nível final", (int) Json.num(v, "level", 0), f.view.level);
            assertEquals(nome + " xp final", (long) Json.num(v, "xp", 0), f.view.xp);
            assertEquals(nome + " ouro final", (long) Json.num(v, "gold", 0), f.view.gold);
            assertEquals(nome + " abates", (long) Json.num(v, "kills", 0), f.view.kills);
            assertEquals(nome + " vida", (int) Json.num(v, "hp", 0), f.view.hp);
        }
    }
}
