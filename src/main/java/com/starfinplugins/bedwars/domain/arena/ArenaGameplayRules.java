package com.starfinplugins.bedwars.domain.arena;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Null fields inherit global gameplay.yml values; old arenas retain their behavior. */
public final class ArenaGameplayRules {
    public static final ArenaGameplayRules INHERIT = new ArenaGameplayRules(null, null, null, null, null);
    public final Boolean generatorSplit, antiAfk;
    public final Integer respawnSeconds;
    /** Null allows every configured choice; empty intentionally allows none. */
    public final Set<String> kits, abilities;

    public ArenaGameplayRules(Boolean generatorSplit, Boolean antiAfk, Integer respawnSeconds,
                              Set<String> kits, Set<String> abilities) {
        if (respawnSeconds != null && (respawnSeconds < 0 || respawnSeconds > 30))
            throw new IllegalArgumentException("Respawn da arena fora de 0..30 segundos");
        this.generatorSplit = generatorSplit;
        this.antiAfk = antiAfk;
        this.respawnSeconds = respawnSeconds;
        this.kits = validated(kits);
        this.abilities = validated(abilities);
    }
    public boolean split(boolean global) { return generatorSplit == null ? global : generatorSplit; }
    public boolean afk(boolean global) { return antiAfk == null ? global : antiAfk; }
    public int respawn(int global) { return respawnSeconds == null ? global : respawnSeconds; }
    public boolean allowsKit(String id) { return kits == null || kits.contains(id); }
    public boolean allowsAbility(String id) { return abilities == null || abilities.contains(id); }

    private static Set<String> validated(Set<String> ids) {
        if (ids == null) return null;
        if (ids.size() > 32) throw new IllegalArgumentException("Lista de escolhas da arena excede 32 IDs");
        Set<String> copy = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id == null || !id.matches("[a-z0-9_-]{1,32}"))
                throw new IllegalArgumentException("ID invalido em regras de arena");
            copy.add(id);
        }
        return Collections.unmodifiableSet(copy);
    }
}
