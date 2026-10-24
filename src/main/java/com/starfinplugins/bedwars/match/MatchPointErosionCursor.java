package com.starfinplugins.bedwars.match;

/**
 * Visits EVERY position in a bounded 1.8.8 arena exactly once, chunk by chunk.
 * Does not depend on players, dragon location, or loaded chunk view distance.
 *
 * The caller controls the number of examined positions/changed blocks per tick,
 * so even very large arenas can erode incrementally without one giant loop.
 */
public final class MatchPointErosionCursor {
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int firstChunkX, lastChunkX, lastChunkZ;
    private int chunkX, chunkZ, x, y, z;
    private final long total;
    private long visited;
    private boolean finished;

    public MatchPointErosionCursor(int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ || minY < 0 || maxY > 255) {
            throw new IllegalArgumentException("Limites de erosao da arena invalidos.");
        }
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.firstChunkX = minX >> 4;
        this.lastChunkX = maxX >> 4;
        this.lastChunkZ = maxZ >> 4;
        this.chunkX = firstChunkX;
        this.chunkZ = minZ >> 4;
        this.x = Math.max(minX, chunkX << 4);
        this.z = Math.max(minZ, chunkZ << 4);
        this.y = maxY;
        this.total = ((long) maxX - minX + 1L)
            * ((long) maxY - minY + 1L) * ((long) maxZ - minZ + 1L);
    }

    public boolean hasNext() { return !finished; }

    /** Examine this position only after its chunk is available. */
    public Cell peek() {
        return finished ? null : new Cell(x, y, z);
    }

    public void advance() {
        if (finished) return;
        visited++;
        if (--y >= minY) return;
        y = maxY;
        if (++x <= Math.min(maxX, ((chunkX + 1) << 4) - 1)) return;
        x = Math.max(minX, chunkX << 4);
        if (++z <= Math.min(maxZ, ((chunkZ + 1) << 4) - 1)) return;
        z = Math.max(minZ, chunkZ << 4);
        if (++chunkX > lastChunkX) {
            chunkX = firstChunkX;
            chunkZ++;
            if (chunkZ > lastChunkZ) {
                finished = true;
                return;
            }
        }
        x = Math.max(minX, chunkX << 4);
        z = Math.max(minZ, chunkZ << 4);
    }

    public long getVisited() { return visited; }
    public long getTotal() { return total; }
    public int getProgressPercent() { return (int) (100L * visited / total); }

    public static final class Cell {
        public final int x, y, z;
        private Cell(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }
        public int chunkX() { return x >> 4; }
        public int chunkZ() { return z >> 4; }
    }
}
