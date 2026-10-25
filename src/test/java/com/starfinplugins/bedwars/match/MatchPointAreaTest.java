package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import org.junit.Test;

public class MatchPointAreaTest {
    @Test public void usesSelectedPos1Pos2InsteadOfTallProtectionCuboid() {
        ArenaDefinition arena = new ArenaDefinition("arena", "world");
        ArenaRegion main = new ArenaRegion("world", -140, 50, -140, 140, 155, 140);
        ArenaRegion protection = new ArenaRegion("world", -140, 0, -140, 140, 255, 140);
        arena.setRegion(ArenaRegionType.MAIN, main);
        arena.setBounds(protection);
        assertSame(main, MatchPointArea.resolve(arena));
    }

    @Test public void supportsLegacyArenaWithoutMainRegion() {
        ArenaDefinition arena = new ArenaDefinition("arena", "world");
        ArenaRegion protection = new ArenaRegion("world", -30, 0, -30, 30, 255, 30);
        arena.setBounds(protection);
        assertSame(protection, MatchPointArea.resolve(arena));
        assertNull(MatchPointArea.resolve(null));
    }
}
