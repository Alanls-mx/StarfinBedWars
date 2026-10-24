package com.starfinplugins.bedwars.domain.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpawnStructureDefinition {
    private final int blockTypeId;
    private final byte blockData;
    private final List<BlockPosition> blocks;

    public SpawnStructureDefinition(int blockTypeId, int blockData, List<BlockPosition> blocks) {
        if (blockTypeId < 1 || blockTypeId > 4095 || blockData < 0 || blockData > 255
                || blocks == null || blocks.size() > 128) {
            throw new IllegalArgumentException("Definicao da estrutura de spawn invalida.");
        }
        this.blockTypeId = blockTypeId;
        this.blockData = (byte) blockData;
        List<BlockPosition> copy = new ArrayList<BlockPosition>();
        for (BlockPosition block : blocks) {
            if (block == null || block.getY() < 0 || block.getY() > 255
                    || Math.abs((long) block.getX()) > 30000000L
                    || Math.abs((long) block.getZ()) > 30000000L) {
                throw new IllegalArgumentException("Coordenada da estrutura de spawn invalida.");
            }
            copy.add(block);
        }
        this.blocks = Collections.unmodifiableList(copy);
    }

    public int getBlockTypeId() { return blockTypeId; }
    public byte getBlockData() { return blockData; }
    public List<BlockPosition> getBlocks() { return blocks; }

    public static final class BlockPosition {
        private final int x;
        private final int y;
        private final int z;

        public BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
    }
}
