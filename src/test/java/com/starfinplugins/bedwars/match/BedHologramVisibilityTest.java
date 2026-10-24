package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BedHologramVisibilityTest {
    @Test public void ownTeamNeverShowsOpponentBed() {
        assertTrue(BedHologramService.permitted(BedHologramService.Visibility.OWN_TEAM,
            "red", "red", false, false));
        assertFalse(BedHologramService.permitted(BedHologramService.Visibility.OWN_TEAM,
            "blue", "red", false, false));
    }

    @Test public void allTeamsAllowsOtherParticipant() {
        assertTrue(BedHologramService.permitted(BedHologramService.Visibility.ALL_TEAMS,
            "blue", "red", false, false));
    }

    @Test public void disabledHidesEveryBed() {
        assertFalse(BedHologramService.permitted(BedHologramService.Visibility.DISABLED,
            "red", "red", false, true));
        assertFalse(BedHologramService.permitted(BedHologramService.Visibility.DISABLED,
            null, "red", true, true));
    }

    @Test public void spectatorsRequireMatchingGameAndExplicitSetting() {
        assertFalse(BedHologramService.permitted(BedHologramService.Visibility.OWN_TEAM,
            null, "red", true, false));
        assertFalse(BedHologramService.permitted(BedHologramService.Visibility.OWN_TEAM,
            null, "red", false, true));
        assertTrue(BedHologramService.permitted(BedHologramService.Visibility.OWN_TEAM,
            null, "red", true, true));
    }
}
