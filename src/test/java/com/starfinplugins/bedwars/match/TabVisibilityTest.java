package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import org.junit.Test;

public class TabVisibilityTest {
    @Test public void isolatesLobbyAndEachMatch() {
        GameMatch first = new GameMatch(new ArenaDefinition("first", "world"));
        GameMatch second = new GameMatch(new ArenaDefinition("second", "world"));
        assertTrue(TabSkinService.shouldShow(null, null));
        assertTrue(TabSkinService.shouldShow(first, first));
        assertFalse(TabSkinService.shouldShow(null, first));
        assertFalse(TabSkinService.shouldShow(first, null));
        assertFalse(TabSkinService.shouldShow(first, second));
    }
}
