package com.starfinplugins.bedwars.world;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

public final class VoidChunkGenerator extends ChunkGenerator {
    private static final int LEGACY_CHUNK_BYTES = 32768;

    @Override
    public byte[] generate(World world, Random random, int chunkX, int chunkZ) {
        return new byte[LEGACY_CHUNK_BYTES];
    }

    @Override
    public byte[][] generateBlockSections(World world, Random random, int chunkX, int chunkZ,
                                          BiomeGrid biomes) {
        return new byte[16][];
    }

    @Override
    public short[][] generateExtBlockSections(World world, Random random, int chunkX, int chunkZ,
                                               BiomeGrid biomes) {
        return new short[16][];
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 100.0, 0.5);
    }
}
