package com.starfinplugins.bedwars.world;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VoidChunkGeneratorTest {
    @Test
    public void generatesLegacyAirChunkAndEmptySectionArrays() {
        VoidChunkGenerator generator = new VoidChunkGenerator();
        byte[] legacy = generator.generate(null, new Random(1L), 0, 0);
        assertEquals(32768, legacy.length);
        for (byte block : legacy) assertEquals(0, block);
        assertNotNull(generator.generateBlockSections(null, new Random(1L), 0, 0, null));
        assertNotNull(generator.generateExtBlockSections(null, new Random(1L), 0, 0, null));
        assertTrue(generator.getDefaultPopulators(null).isEmpty());
        assertTrue(generator.canSpawn(null, 0, 0));
    }
}
