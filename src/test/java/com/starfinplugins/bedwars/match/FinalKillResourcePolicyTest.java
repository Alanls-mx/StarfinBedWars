package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FinalKillResourcePolicyTest {
    @Test public void matchWinningFinalKillGivesNoMaterials() {
        assertFalse(FinalKillResourcePolicy.shouldTransfer(false, 1, 2));
    }

    @Test public void normalDeathStillTransfersMaterials() {
        assertTrue(FinalKillResourcePolicy.shouldTransfer(true, 1, 2));
        assertTrue(FinalKillResourcePolicy.shouldTransfer(true, 4, 2));
    }

    @Test public void finalKillBeforeTheLastTwoTeamsStillTransfers() {
        assertTrue(FinalKillResourcePolicy.shouldTransfer(false, 1, 3));
        assertTrue(FinalKillResourcePolicy.shouldTransfer(false, 2, 2));
    }
}
