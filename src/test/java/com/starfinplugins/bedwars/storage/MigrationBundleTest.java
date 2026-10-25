package com.starfinplugins.bedwars.storage;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class MigrationBundleTest {
    @Test public void fileHashesAreStable() {
        MigrationBundle.Entry a = new MigrationBundle.Entry("version: 7\n");
        MigrationBundle.Entry b = new MigrationBundle.Entry("version: 7\n");
        MigrationBundle.Entry c = new MigrationBundle.Entry("version: 6\n");
        assertEquals(a.sha256, b.sha256);
        assertNotEquals(a.sha256, c.sha256);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsafeServerIds() {
        new MigrationBundle("../other", Collections.<String, MigrationBundle.Entry>emptyMap(),
            Collections.<String, MigrationBundle.Entry>emptyMap());
    }

    @Test public void bundleIsImmutable() {
        java.util.Map<String, MigrationBundle.Entry> originals =
            new java.util.HashMap<String, MigrationBundle.Entry>();
        originals.put("alpha", new MigrationBundle.Entry("id: alpha"));
        MigrationBundle bundle = new MigrationBundle("bedwars-1", originals,
            Collections.<String, MigrationBundle.Entry>emptyMap());
        originals.clear();
        assertEquals(1, bundle.arenas.size());
        try { bundle.arenas.clear(); fail("Mutable bundle"); }
        catch (UnsupportedOperationException expected) { }
    }
}
