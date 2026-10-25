package com.starfinplugins.bedwars.gameplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class AfkActivityTest {
    @Test public void headRotationAndSmallJitterDoNotResetTimeout() {
        AfkActivity activity = new AfkActivity(0L, 0, 64, 0);
        assertFalse(activity.moved(150000L, 0.1, 64, 0.1));
        assertEquals(AfkActivity.Stage.WARNING, activity.stage(150000L, 180, 30));
        assertEquals(AfkActivity.Stage.EXPIRED, activity.stage(180000L, 180, 30));
        assertTrue(activity.moved(180001L, 1, 64, 0));
        assertEquals(AfkActivity.Stage.ACTIVE, activity.stage(180001L, 180, 30));
    }

    @Test public void interactionResetsWarningAndGameTimeout() {
        AfkActivity activity = new AfkActivity(1000L, 0, 0, 0);
        assertEquals(AfkActivity.Stage.WARNING, activity.stage(271000L, 300, 30));
        activity.action(271000L);
        assertEquals(AfkActivity.Stage.ACTIVE, activity.stage(271001L, 300, 30));
    }
}
