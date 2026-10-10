package com.app.accounting.importer.monthly;

import java.text.Normalizer;
import java.util.*;

/**
 * Empareja nombres escritos de distintas formas en las hojas del informe
 * ("LAURA MARTINEZ DE LOPEZ" ↔ "LAURA ELISA MARTINEZ DE LOPEZ").
 * Regla: se comparan palabras significativas (sin tildes ni conectores); gana el candidato con más
 * palabras en común, con un mínimo de dos, y sin empates.
 */
public final class NameMatcher {

    private static final Set<String> STOPWORDS = Set.of("DE", "DEL", "LA", "LAS", "LOS", "Y", "E", "SR", "SRA", "VDA");

    private NameMatcher() {
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toUpperCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    public static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : normalize(s).split(" ")) {
            if (t.length() > 1 && !STOPWORDS.contains(t)) out.add(t);
        }
        return out;
    }

    /** Palabras de {@code a} presentes en {@code b} (igual, o a una letra de distancia si tienen 5+ letras). */
    public static int commonTokens(String a, String b) {
        List<String> tb = new ArrayList<>(tokens(b));
        int count = 0;
        for (String t : tokens(a)) {
            for (Iterator<String> it = tb.iterator(); it.hasNext(); ) {
                String u = it.next();
                if (t.equals(u) || (t.length() >= 5 && u.length() >= 5 && distance(t, u) <= 1)) {
                    count++;
                    it.remove();
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Mejor candidato para el nombre o {@code null} si no hay coincidencia suficiente o hay empate.
     * Coincidencia exacta normalizada siempre gana.
     */
    public static <T> T best(String name, Collection<T> candidates, java.util.function.Function<T, String> nameOf) {
        String norm = normalize(name);
        for (T c : candidates) {
            if (normalize(nameOf.apply(c)).equals(norm)) return c;
        }
        // con una sola palabra significativa el nombre es ambiguo: solo vale la coincidencia exacta
        int needed = 2;
        if (tokens(name).size() < needed) return null;
        T best = null;
        int bestScore = 0;
        int bestExtra = Integer.MAX_VALUE;
        boolean tie = false;
        for (T c : candidates) {
            String other = nameOf.apply(c);
            int score = commonTokens(name, other);
            if (score < needed) continue;
            int extra = tokens(other).size() - score; // a igualdad de aciertos, el nombre con menos palabras sobrantes
            if (score > bestScore || (score == bestScore && extra < bestExtra)) {
                best = c;
                bestScore = score;
                bestExtra = extra;
                tie = false;
            } else if (score == bestScore && extra == bestExtra) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
