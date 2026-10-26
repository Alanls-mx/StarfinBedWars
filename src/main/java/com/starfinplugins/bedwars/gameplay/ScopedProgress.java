package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** One immutable player snapshot shared by menus and match start. */
public final class ScopedProgress {
    public enum Kind { KIT, ABILITY }
    public static final ScopedProgress EMPTY = new ScopedProgress(Collections.emptyMap(),
        Collections.emptyMap());
    public final Map<String, Integer> levels;
    public final Map<String, String> selections;

    public ScopedProgress(Map<String, Integer> levels, Map<String, String> selections) {
        this.levels = Collections.unmodifiableMap(new HashMap<String, Integer>(levels));
        this.selections = Collections.unmodifiableMap(new HashMap<String, String>(selections));
    }
    public int level(Kind kind, String id, String mode) {
        String canonical = MatchMode.persisted(mode);
        return Math.max(levels.getOrDefault(key(kind, id, "GLOBAL"), 0),
            levels.getOrDefault(key(kind, id, canonical), 0));
    }
    public String selection(Kind kind, String mode, String legacy) {
        return selections.getOrDefault(kind.name() + "|" + MatchMode.persisted(mode), legacy);
    }
    public ScopedProgress withLevel(Kind kind, String id, String scope, int level) {
        Map<String, Integer> next = new HashMap<String, Integer>(levels);
        String key = key(kind, id, scope);
        next.put(key, Math.max(level, next.getOrDefault(key, 0)));
        return new ScopedProgress(next, selections);
    }
    public ScopedProgress withSelection(Kind kind, String mode, String id) {
        Map<String, String> next = new HashMap<String, String>(selections);
        next.put(kind.name() + "|" + MatchMode.persisted(mode), id);
        return new ScopedProgress(levels, next);
    }
    public static String key(Kind kind, String id, String scope) {
        if (kind == null || id == null || !id.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("Identificador de aquisicao invalido");
        String normalized = "GLOBAL".equals(scope) ? "GLOBAL" : MatchMode.persisted(scope);
        if (normalized == null) throw new IllegalArgumentException("Escopo invalido");
        return kind.name() + "|" + id + "|" + normalized;
    }
}
