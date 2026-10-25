package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.UUID;
import org.junit.Test;

public class ArenaBoundaryWarningsTest {
    @Test public void throttlesEachBoundaryTypeSeparately() {
        ArenaBoundaryWarnings alerts = new ArenaBoundaryWarnings();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.HEIGHT, 1000L, 4000L));
        assertFalse(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.HEIGHT, 1001L, 4000L));
        assertTrue(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.EDGE, 1002L, 4000L));
        assertFalse(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.EDGE, 4001L, 4000L));
        assertTrue(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.HEIGHT, 5000L, 4000L));
        assertTrue(alerts.shouldWarn(id, ArenaBoundaryWarnings.Type.EDGE, 5002L, 4000L));
    }

    @Test public void clearingPlayerOrServiceResetsCooldown() {
        ArenaBoundaryWarnings alerts = new ArenaBoundaryWarnings();
        UUID player = UUID.randomUUID();
        assertTrue(alerts.shouldWarn(player, ArenaBoundaryWarnings.Type.EDGE, 1000, 4000));
        alerts.forget(player);
        assertTrue(alerts.shouldWarn(player, ArenaBoundaryWarnings.Type.EDGE, 1001, 4000));
        alerts.clear();
        assertTrue(alerts.shouldWarn(player, ArenaBoundaryWarnings.Type.EDGE, 1002, 4000));
    }

    @Test public void warnsNearEdgesIncludingNonIntegerCoordinates() {
        assertTrue(ArenaBoundaryWarnings.nearHorizontalEdge(-9.8, 0, -10, 10, -10, 10, 1.25));
        assertTrue(ArenaBoundaryWarnings.nearHorizontalEdge(10.7, 0, -10, 10, -10, 10, 1.25));
        assertTrue(ArenaBoundaryWarnings.nearHorizontalEdge(0, 10.7, -10, 10, -10, 10, 1.25));
        assertFalse(ArenaBoundaryWarnings.nearHorizontalEdge(0, 0, -10, 10, -10, 10, 1.25));
    }

    @Test public void cooldownsAreIndependentAcrossPlayers() {
        ArenaBoundaryWarnings warnings = new ArenaBoundaryWarnings();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(warnings.shouldWarn(first, ArenaBoundaryWarnings.Type.EDGE, 100, 4000));
        assertTrue(warnings.shouldWarn(second, ArenaBoundaryWarnings.Type.EDGE, 101, 4000));
    }
}
