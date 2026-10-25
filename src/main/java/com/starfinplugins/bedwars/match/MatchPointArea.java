package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;

/** The actual /sbw setarena pos1/pos2 selection is MAIN, not the full-height protection cuboid. */
public final class MatchPointArea {
    private MatchPointArea() { }

    public static ArenaRegion resolve(ArenaDefinition arena) {
        if (arena == null) return null;
        ArenaRegion selected = arena.getRegion(ArenaRegionType.MAIN);
        return selected != null ? selected : arena.getBounds();
    }
}
