package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class VoidDepthPolicyTest {
    @Test public void usesLowestBlockAndNeverMovesDeathUpward() {
        assertEquals(18, VoidDepthPolicy.deathHeight(66, 48, 30));
        assertEquals(30, VoidDepthPolicy.deathHeight(90, 48, 30));
    }

    @Test public void clampsToWorldBottomAndFallsBackWhenNoBlockIsFound() {
        assertEquals(0, VoidDepthPolicy.deathHeight(34, 48, 30));
        assertEquals(0, VoidDepthPolicy.deathHeight(-1, 48, 30));
    }
}
