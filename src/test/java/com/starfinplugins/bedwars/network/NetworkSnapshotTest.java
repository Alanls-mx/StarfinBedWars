package com.starfinplugins.bedwars.network;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkSnapshotTest {
    @Test public void hashesAreDeterministicAndDetectChanges() {
        NetworkSnapshot.Arena first = arena("name: Alpha\n");
        NetworkSnapshot.Arena same = arena("name: Alpha\n");
        NetworkSnapshot.Arena changed = arena("name: Beta\n");
        assertEquals(64, first.configHash.length());
        assertEquals(first.configHash, same.configHash);
        assertNotEquals(first.configHash, changed.configHash);
    }

    @Test public void copiesArenaAndMatchCollections() {
        java.util.List<NetworkSnapshot.Arena> input =
            new java.util.ArrayList<NetworkSnapshot.Arena>(Arrays.asList(arena("ok")));
        NetworkSnapshot snapshot = new NetworkSnapshot("bedwars-1", "instance", 100L,
            2, 100, input, Collections.<NetworkSnapshot.Match>emptyList(),
            Collections.<NetworkSnapshot.Player>emptyList());
        input.clear();
        assertEquals(1, snapshot.arenas.size());
        try { snapshot.arenas.clear(); fail("Mutable collection"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static NetworkSnapshot.Arena arena(String yaml) {
        return new NetworkSnapshot.Arena("arena_1", "Arena", "world", yaml, "DUO", 2, 2, 1,
            2, 8, 0, 30, true, false, true, "AVAILABLE", null);
    }
}
