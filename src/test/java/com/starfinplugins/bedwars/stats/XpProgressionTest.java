package com.starfinplugins.bedwars.stats;

import org.junit.Test;
import static org.junit.Assert.*;

public class XpProgressionTest {
    @Test public void levelsDependOnlyOnTotalXp() {
        XpProgression xp = new XpProgression(1000, 250);
        assertEquals(1, xp.progress(0).level);
        assertEquals(1, xp.progress(999).level);
        assertEquals(2, xp.progress(1000).level);
        assertEquals(0, xp.progress(1000).current);
        assertEquals(1250, xp.progress(1000).required);
        assertEquals(3, xp.progress(2250).level);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBrokenCurve() { new XpProgression(0, 1); }

    @Test public void crossingSeveralLevelsUsesTheOriginalCurve() {
        XpProgression xp = new XpProgression(1000, 250);
        assertEquals(1, xp.totalXpForLevel(2) / 1000);
        assertEquals(3750, xp.totalXpForLevel(4));
        assertEquals(4, xp.progress(3750).level);
        assertEquals(4, xp.progress(3999).level);
        assertEquals(10000, xp.progress(Long.MAX_VALUE).level);
        assertTrue(xp.totalXpForLevel(10000) > xp.totalXpForLevel(5000));
    }
}
