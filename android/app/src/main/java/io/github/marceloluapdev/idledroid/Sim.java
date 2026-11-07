package io.github.marceloluapdev.idledroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Porte linha a linha de src/sim.js (simulação determinística da luta idle).
 * Mesmas contas, mesma ordem de sorteios: mesmo trecho + mesmo instante = mesma luta do JS.
 * O teste SimTest compara os eventos com os gerados pelo JS (tools/gerar-fixture-sim.js).
 * Sem dependências do Android, para rodar também em Java puro.
 */
public final class Sim {

    private Sim() {
    }

    // ---------- Dados (data/game.json) ----------
    public static final class Spell {
        public String id;
        public int minLevel, hits;
        public double weight, mult;
        public long castMs, travelMs, recoverMs, hitGapMs;
        public Map<String, Object> visual;
    }

    public static final class Stage {
        public String id, name, monster;
        public int minLevel, hp, hpPerLevel, gold, xp;
        public double levelCap; // Infinity quando não definido
        public Map<String, Object> bg;
    }

    public static final class Data {
        public final List<Stage> stages = new ArrayList<>();
        public final List<Spell> spells = new ArrayList<>();
        public double baseAttack, attackPerLevel, critChance, critMult, xpBase, xpExp;
        public long respawnMs;
        public Map<String, Object> raw;

        public static Data from(String json) {
            Data d = new Data();
            d.raw = Json.obj(Json.parse(json));
            for (Object o : Json.list(d.raw.get("stages"))) {
                Map<String, Object> m = Json.obj(o);
                Stage s = new Stage();
                s.id = Json.str(m, "id");
                s.name = Json.str(m, "name");
                s.monster = Json.str(m, "monster");
                s.minLevel = (int) Json.num(m, "minLevel", 1);
                s.hp = (int) Json.num(m, "hp", 1);
                s.hpPerLevel = (int) Json.num(m, "hpPerLevel", 0);
                s.gold = (int) Json.num(m, "gold", 0);
                s.xp = (int) Json.num(m, "xp", 0);
                double cap = Json.num(m, "levelCap", 0);
                s.levelCap = cap == 0 ? Double.POSITIVE_INFINITY : cap; // JS: stage.levelCap || Infinity
                s.bg = Json.obj(m.get("bg"));
                d.stages.add(s);
            }
            for (Object o : Json.list(d.raw.get("spells"))) {
                Map<String, Object> m = Json.obj(o);
                Spell s = new Spell();
                s.id = Json.str(m, "id");
                s.minLevel = (int) Json.num(m, "minLevel", 1);
                s.weight = Json.num(m, "weight", 1);
                s.mult = Json.num(m, "mult", 1);
                s.castMs = (long) Json.num(m, "castMs", 0);
                s.travelMs = (long) Json.num(m, "travelMs", 0);
                s.recoverMs = (long) Json.num(m, "recoverMs", 0);
                s.hits = (int) Json.num(m, "hits", 0);
                if (s.hits == 0) s.hits = 1; // JS: spell.hits || 1
                s.hitGapMs = (long) Json.num(m, "hitGapMs", 0);
                s.visual = Json.obj(m.get("visual"));
                d.spells.add(s);
            }
            Map<String, Object> r = Json.obj(d.raw.get("rules"));
            d.baseAttack = Json.num(r, "baseAttack", 6);
            d.attackPerLevel = Json.num(r, "attackPerLevel", 3);
            d.critChance = Json.num(r, "critChance", 0.1);
            d.critMult = Json.num(r, "critMult", 2);
            d.respawnMs = (long) Json.num(r, "respawnMs", 1000);
            Map<String, Object> x = Json.obj(r.get("xpToLevel"));
            d.xpBase = Json.num(x, "base", 10);
            d.xpExp = Json.num(x, "exp", 1.5);
            return d;
        }

        public Stage stage(String id) {
            for (Stage s : stages) if (s.id.equals(id)) return s;
            return stages.get(0);
        }

        public Spell spell(String id) {
            for (Spell s : spells) if (s.id.equals(id)) return s;
            return spells.get(0);
        }
    }

    // ---------- Aleatório com semente (mulberry32, igual ao JS) ----------
    static double rand(Cursor c) {
        c.seed += 0x6d2b79f5;
        int t = c.seed;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
    }

    // ---------- Regras ----------
    public static long xpNeed(Data d, int level) {
        return (long) Math.floor(d.xpBase * StrictMath.pow(level, d.xpExp));
    }

    static double attack(Data d, int level) {
        return d.baseAttack + d.attackPerLevel * (level - 1);
    }

    static int monsterMax(Stage s, int level) {
        return s.hp + s.hpPerLevel * (level - 1);
    }

    static Spell pickSpell(Data d, Cursor c) {
        List<Spell> list = new ArrayList<>();
        for (Spell s : d.spells) if (s.minLevel <= c.level) list.add(s);
        double total = 0;
        for (Spell s : list) total += s.weight;
        double r = rand(c) * total;
        for (Spell s : list) {
            r -= s.weight;
            if (r < 0) return s;
        }
        return list.get(list.size() - 1);
    }

    // ---------- Trecho (o que o jogo salva) ----------
    public static final class Seg {
        public String stageId;
        public long start;
        public int seed;
        public int level;
        public long xp, gold, kills;
        public Integer hp; // null = monstro novo

        /** Lê o "seg" do save do jogo (localStorage "idle.save" -> {v, seg, seen}). */
        public static Seg fromSave(String saveJson) {
            Map<String, Object> s = Json.obj(Json.parse(saveJson));
            Map<String, Object> m = Json.obj(s.get("seg"));
            Seg g = new Seg();
            g.stageId = Json.str(m, "stageId");
            g.start = (long) Json.num(m, "start", 0);
            g.seed = (int) (long) Json.num(m, "seed", 1);
            g.level = (int) Json.num(m, "level", 1);
            g.xp = (long) Json.num(m, "xp", 0);
            g.gold = (long) Json.num(m, "gold", 0);
            g.kills = (long) Json.num(m, "kills", 0);
            Object hp = m.get("hp");
            g.hp = hp instanceof Double ? (int) (double) (Double) hp : null;
            return g;
        }

        public static Seg first(Data d, long now, int seed) {
            Seg g = new Seg();
            g.stageId = d.stages.get(0).id;
            g.start = now;
            g.seed = seed;
            g.level = 1;
            return g;
        }
    }

    // ---------- Eventos ----------
    public static final class Ev {
        public long t;
        public String type, spell, monster;
        public long ms;
        public int i, dmg, hp, max, level;
        public boolean crit;
        public long gold, xp;

        Ev(long t, String type) {
            this.t = t;
            this.type = type;
        }
    }

    static final class Cursor {
        long t;
        int seed;
        String stageId;
        int level, hp, max;
        long xp, gold, kills;
    }

    static Cursor cursorFrom(Data d, Seg g) {
        Stage st = d.stage(g.stageId);
        Cursor c = new Cursor();
        c.t = g.start;
        c.seed = g.seed;
        c.stageId = st.id;
        c.level = g.level;
        c.xp = g.xp;
        c.gold = g.gold;
        c.kills = g.kills;
        c.max = monsterMax(st, g.level);
        c.hp = g.hp == null ? c.max : g.hp;
        return c;
    }

    // Uma ação do herói: conjura, dispara 1+ projéteis, acerta, talvez mata.
    static void step(Data d, Cursor c, List<Ev> out) {
        Stage stage = d.stage(c.stageId);
        Spell spell = pickSpell(d, c);
        int hits = spell.hits;
        long gap = spell.hitGapMs;
        long t0 = c.t;
        long castEnd = t0 + spell.castMs;

        Ev cast = new Ev(t0, "cast");
        cast.spell = spell.id;
        cast.ms = spell.castMs;
        out.add(cast);

        long end = castEnd + spell.recoverMs;
        for (int i = 0; i < hits; i++) {
            long launch = castEnd + i * gap;
            long impact = launch + spell.travelMs;
            boolean crit = rand(c) < d.critChance;
            double spread = 0.9 + 0.2 * rand(c);
            int dmg = (int) Math.max(1, Math.round(attack(d, c.level) * spell.mult * (crit ? d.critMult : 1) * spread));

            Ev shot = new Ev(launch, "shot");
            shot.spell = spell.id;
            shot.ms = spell.travelMs;
            shot.i = i;
            out.add(shot);
            c.hp = Math.max(0, c.hp - dmg);
            Ev hit = new Ev(impact, "hit");
            hit.spell = spell.id;
            hit.dmg = dmg;
            hit.crit = crit;
            hit.hp = c.hp;
            hit.max = c.max;
            hit.i = i;
            out.add(hit);
            end = Math.max(end, impact + spell.recoverMs);

            if (c.hp == 0) {
                long xp = c.level > stage.levelCap ? 1 : stage.xp;
                c.kills++;
                c.gold += stage.gold;
                c.xp += xp;
                Ev kill = new Ev(impact, "kill");
                kill.gold = stage.gold;
                kill.xp = xp;
                kill.monster = stage.monster;
                out.add(kill);
                while (c.xp >= xpNeed(d, c.level)) {
                    c.xp -= xpNeed(d, c.level);
                    c.level++;
                    Ev up = new Ev(impact, "levelup");
                    up.level = c.level;
                    out.add(up);
                }
                c.max = monsterMax(stage, c.level);
                c.hp = c.max;
                long spawnT = impact + d.respawnMs;
                Ev spawn = new Ev(spawnT, "spawn");
                spawn.monster = stage.monster;
                spawn.hp = c.hp;
                spawn.max = c.max;
                out.add(spawn);
                end = Math.max(end, spawnT);
                break;
            }
        }
        c.t = end;
    }

    // ---------- Luta: cursor + fila + estado VISÍVEL ----------
    public static final class View {
        public String stageId;
        public int level, hp, max;
        public long xp, gold, kills, t;
        public boolean alive = true;
    }

    public static final class Fight {
        public final Data data;
        public final Seg seg;
        final Cursor c;
        private List<Ev> queue = new ArrayList<>();
        public final View view = new View();

        public Fight(Data d, Seg g) {
            data = d;
            seg = g;
            c = cursorFrom(d, g);
            view.stageId = c.stageId;
            view.level = g.level;
            view.xp = g.xp;
            view.gold = g.gold;
            view.kills = g.kills;
            view.hp = c.hp;
            view.max = c.max;
            view.t = g.start;
        }

        private void apply(Ev e, List<Ev> fired) {
            View v = view;
            switch (e.type) {
                case "hit": v.hp = e.hp; v.max = e.max; break;
                case "kill": v.kills++; v.gold += e.gold; v.xp += e.xp; v.alive = false; break;
                case "levelup": v.xp -= xpNeed(data, v.level); v.level = e.level; break;
                case "spawn": v.hp = e.hp; v.max = e.max; v.alive = true; break;
                default: break;
            }
            if (fired != null) fired.add(e);
        }

        /** Avança até "now". Devolve os eventos que aconteceram (ou null se recolher = false). */
        public List<Ev> update(long now, boolean recolher) {
            List<Ev> fired = recolher ? new ArrayList<>() : null;
            int i = 0;
            while (i < queue.size() && queue.get(i).t <= now) apply(queue.get(i++), fired);
            if (i > 0) queue = new ArrayList<>(queue.subList(i, queue.size()));
            while (c.t <= now) {
                List<Ev> fresh = new ArrayList<>();
                step(data, c, fresh);
                Collections.sort(fresh, (a, b) -> Long.compare(a.t, b.t));
                for (Ev e : fresh) {
                    if (e.t <= now) apply(e, fired);
                    else queue.add(e);
                }
            }
            view.t = now;
            return fired;
        }
    }
}
