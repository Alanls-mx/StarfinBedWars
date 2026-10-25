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
}
