package io.github.marceloluapdev.idledroid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de JSON mínimo (objetos, listas, números, textos, true/false/null).
 * Sem dependências, para a simulação rodar igual no app e nos testes em Java puro.
 * Números viram Double; objetos viram Map (na ordem do arquivo); listas viram List.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String texto) {
        Json p = new Json(texto);
        p.espacos();
        Object v = p.valor();
        p.espacos();
        if (p.i != p.s.length()) throw p.erro("sobrou texto");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    public static double num(Map<String, Object> m, String k, double padrao) {
        Object v = m.get(k);
        return v instanceof Double ? (Double) v : padrao;
    }

    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : null;
    }

    private RuntimeException erro(String msg) {
        return new IllegalArgumentException("JSON: " + msg + " na posição " + i);
    }

    private void espacos() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object valor() {
        if (i >= s.length()) throw erro("fim inesperado");
        char c = s.charAt(i);
        if (c == '{') return objeto();
        if (c == '[') return lista();
        if (c == '"') return texto();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return numero();
    }

    private Map<String, Object> objeto() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        espacos();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            espacos();
            String k = texto();
            espacos();
            if (s.charAt(i++) != ':') throw erro("esperava ':'");
            espacos();
            m.put(k, valor());
            espacos();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw erro("esperava ',' ou '}'");
        }
    }

    private List<Object> lista() {
        List<Object> l = new ArrayList<>();
        i++;
        espacos();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            espacos();
            l.add(valor());
            espacos();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw erro("esperava ',' ou ']'");
        }
    }

    private String texto() {
        if (s.charAt(i) != '"') throw erro("esperava texto");
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: b.append(e);
            }
        }
    }

    private Double numero() {
        int ini = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (ini == i) throw erro("valor inválido");
        return Double.parseDouble(s.substring(ini, i));
    }
}
