package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TntPlacementPolicyTest {
    @Test public void primesValidLivePlacements() {
        assertTrue(TntPlacementPolicy.shouldPrime(true, true, true, true, true));
    }

    @Test public void doesNotPrimeInWaitingOrEndingMatches() {
        assertFalse(TntPlacementPolicy.shouldPrime(false, true, true, true, true));
    }

    @Test public void doesNotPrimeOtherMatches() {
        assertFalse(TntPlacementPolicy.shouldPrime(true, false, true, true, true));
    }

    @Test public void doesNotPrimeDisconnectedPlayersOrSpectators() {
        assertFalse(TntPlacementPolicy.shouldPrime(true, true, false, true, true));
        assertFalse(TntPlacementPolicy.shouldPrime(true, true, true, false, true));
    }

    @Test public void respectsProtectedOrDisallowedPlacement() {
        assertFalse(TntPlacementPolicy.shouldPrime(true, true, true, true, false));
    }
}
