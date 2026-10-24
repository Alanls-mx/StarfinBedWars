package com.starfinplugins.bedwars.domain;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.economy.GeneratorInstance;
import com.starfinplugins.bedwars.domain.economy.GeneratorLevel;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeneratorInstanceTest {
    @Test
    public void firstProductionWaitsFullIntervalAfterSpawnOpens() {
        GameMatch match = new GameMatch(new ArenaDefinition("test", "world"));
        ResourceGeneratorDefinition definition = new ResourceGeneratorDefinition("iron_1",
            ResourceType.IRON, new Position("world", 0, 64, 0, 0, 0), "red", 1);
        GeneratorInstance generator = new GeneratorInstance(match, definition, 100);
        java.util.List<GeneratorLevel> levels = Arrays.asList(new GeneratorLevel(80, 1, 48));
        generator.scheduleFirstProduction(100, levels);
        assertEquals(80, generator.getTicksUntilProduction(100));
        assertFalse(generator.isDue(179));
        assertTrue(generator.isDue(180));
    }

    @Test
    public void respectsIntervalsCapsUpgradeAndStop() {
        GameMatch match = new GameMatch(new ArenaDefinition("test", "world"));
        ResourceGeneratorDefinition definition = new ResourceGeneratorDefinition("iron_1",
            ResourceType.IRON, new Position("world", 0, 64, 0, 0, 0), "red", 1);
        GeneratorInstance generator = new GeneratorInstance(match, definition, 0);
        GeneratorLevel first = new GeneratorLevel(10, 1, 3);
        GeneratorLevel second = new GeneratorLevel(5, 2, 4);
        java.util.List<GeneratorLevel> levels = Arrays.asList(first, second);

        assertEquals(0, generator.getTicksUntilProduction(0));
        assertTrue(generator.advance(0, levels, 0));
        assertEquals(7, generator.getTicksUntilProduction(3));
        assertFalse(generator.advance(9, levels, 0));
        assertEquals(0, generator.getTicksUntilProduction(12));
        assertEquals(1, generator.getProducedAmount());
        assertFalse(generator.advance(10, levels, 3));
        assertTrue(generator.advance(20, levels, 0));
        assertTrue(generator.upgrade(levels));
        assertEquals(5, generator.getIntervalTicks(levels));
        assertFalse(generator.upgrade(levels));

        generator.stop();
        assertFalse(generator.advance(30, levels, 0));
        assertFalse(generator.upgrade(levels));
    }

    @Test
    public void inactiveTeamEmeraldStartsOnlyAfterActivationDelay() {
        GameMatch match = new GameMatch(new ArenaDefinition("test", "world"));
        ResourceGeneratorDefinition definition = new ResourceGeneratorDefinition("emerald_red",
            ResourceType.EMERALD, new Position("world", 0, 64, 0, 0, 0), "red", 1);
        GeneratorInstance generator = new GeneratorInstance(match, definition, 50, false);
        java.util.List<GeneratorLevel> levels = Arrays.asList(new GeneratorLevel(200, 1, 4));

        assertFalse(generator.isDue(1000));
        assertTrue(generator.activate(1000, levels));
        assertEquals(200, generator.getTicksUntilProduction(1000));
        assertFalse(generator.isDue(1199));
        assertTrue(generator.isDue(1200));
        assertTrue(generator.advance(1200, levels, 0));
        assertEquals(1, generator.getProducedAmount());
        assertFalse(generator.activate(1200, levels));
    }

    @Test
    public void centralGeneratorKeepsFourUnitCapAcrossLevelsWithoutBacklog() {
        GameMatch match = new GameMatch(new ArenaDefinition("test", "world"));
        ResourceGeneratorDefinition definition = new ResourceGeneratorDefinition("diamond_1",
            ResourceType.DIAMOND, new Position("world", 0, 64, 0, 0, 0), null, 1);
        GeneratorInstance generator = new GeneratorInstance(match, definition, 0);
        java.util.List<GeneratorLevel> levels = Arrays.asList(
            new GeneratorLevel(10, 1, 16), new GeneratorLevel(5, 2, 32));

        assertTrue(generator.advance(0, levels, 0));
        assertFalse(generator.advance(10, levels, 4));
        assertEquals(10, generator.getTicksUntilProduction(10));
        assertTrue(generator.upgrade(levels));
        assertFalse(generator.advance(20, levels, 3));
        assertTrue(generator.advance(25, levels, 2));
        assertEquals(3, generator.getProducedAmount());
    }
}
