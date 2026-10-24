package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class ExplosionBlockPolicyTest {
    private final Set<String> placed =
        new HashSet<String>(Arrays.asList("1:64:1", "2:64:1"));

    @Test public void allowsPlayerPlacedDefensesDuringMatch() {
        assertTrue(ExplosionBlockPolicy.canDestroy(true, placed, "1:64:1"));
    }

    @Test public void protectsOriginalMapBlocks() {
        assertFalse(ExplosionBlockPolicy.canDestroy(true, placed, "100:64:100"));
    }

    @Test public void protectsBlocksBeforeMatchStarts() {
        assertFalse(ExplosionBlockPolicy.canDestroy(false, placed, "1:64:1"));
    }

    @Test public void protectsWhenNoPlacementDataExists() {
        assertFalse(ExplosionBlockPolicy.canDestroy(true, null, "1:64:1"));
        assertFalse(ExplosionBlockPolicy.canDestroy(true, placed, null));
    }

    @Test public void removingAPlacementImmediatelyProtectsThatKey() {
        placed.remove("1:64:1");
        assertFalse(ExplosionBlockPolicy.canDestroy(true, placed, "1:64:1"));
    }
}
