package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.UUID;
import org.junit.Test;

public class BridgeBlockOwnershipTest {
    private final UUID matchA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID matchB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID egg1 = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private final UUID egg2 = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Test public void requiresMatchingEggMatchAndPosition() {
        BridgeBlockOwnership owners = new BridgeBlockOwnership();
        owners.record(matchA, "0:64:1", egg1);
        assertTrue(owners.owns(matchA, "0:64:1", egg1));
        assertFalse(owners.owns(matchA, "0:64:1", egg2));
        assertFalse(owners.owns(matchB, "0:64:1", egg1));
        assertFalse(owners.owns(matchA, "0:64:2", egg1));
    }

    @Test public void breakOrManualReplacementPreservesNewConstruction() {
        BridgeBlockOwnership owners = new BridgeBlockOwnership();
        owners.record(matchA, "0:64:1", egg1);
        owners.forget(matchA, "0:64:1");
        assertFalse(owners.owns(matchA, "0:64:1", egg1));
    }

    @Test public void overlappingBridgesNeverDeleteOtherEggsOwnership() {
        BridgeBlockOwnership owners = new BridgeBlockOwnership();
        owners.record(matchA, "0:64:1", egg1);
        owners.record(matchA, "0:64:1", egg2);
        assertFalse(owners.owns(matchA, "0:64:1", egg1));
        assertTrue(owners.owns(matchA, "0:64:1", egg2));
    }

    @Test public void finishingOneMatchDoesNotLoseOtherMatchBlocks() {
        BridgeBlockOwnership owners = new BridgeBlockOwnership();
        owners.record(matchA, "0:64:1", egg1);
        owners.record(matchB, "0:64:1", egg2);
        owners.clearMatch(matchA);
        assertFalse(owners.owns(matchA, "0:64:1", egg1));
        assertTrue(owners.owns(matchB, "0:64:1", egg2));
        owners.clear();
        assertFalse(owners.owns(matchB, "0:64:1", egg2));
    }
}
