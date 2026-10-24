package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaBedWorldValidatorTest {
    @Test public void detectsMissingPhysicalBedDespiteConfiguredCoordinates() {
        ArenaDefinition arena = arena();
        assertTrue(ArenaBedWorldValidator.validate(arena, world(true, true)).isEmpty());
        assertTrue(ArenaBedWorldValidator.validate(arena, world(true, false)).get(0)
            .contains("blue"));
    }

    @Test public void unavailableChunkCannotBeGeneratedToValidateAnArena() {
        String problem = ArenaBedWorldValidator.validate(arena(), world(false, false)).get(0);
        assertTrue(problem.contains("ausente"));
    }

    private static ArenaDefinition arena() {
        ArenaDefinition arena = new ArenaDefinition("duel", "world");
        TeamDefinition red = new TeamDefinition("red", "Vermelho", "c", 1);
        TeamDefinition blue = new TeamDefinition("blue", "Azul", "9", 1);
        red.setBed(new Position("world", -9.5, 121, 0.5, 0, 0));
        blue.setBed(new Position("world", 10.5, 121, 0.5, 0, 0));
        arena.addTeam(red);
        arena.addTeam(blue);
        return arena;
    }

    private static World world(final boolean loaded, final boolean bothBeds) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isChunkLoaded": return loaded;
                    case "loadChunk": return false;
                    case "getBlockAt":
                        if (!loaded) fail("Unavailable chunk must not be read");
                        final int x = ((Number) args[0]).intValue();
                        return Proxy.newProxyInstance(Block.class.getClassLoader(),
                            new Class<?>[]{Block.class}, (block, read, values) ->
                                "getType".equals(read.getName())
                                    ? x < 0 || bothBeds ? Material.BED_BLOCK : Material.AIR
                                    : null);
                    default: return null;
                }
            });
    }
}
