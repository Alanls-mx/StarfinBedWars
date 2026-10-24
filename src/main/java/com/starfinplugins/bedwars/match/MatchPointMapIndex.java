package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Incrementally indexes the ACTUAL disposable arena instance, including
 * central/neutral islands without beds. No player/view-distance dependency.
 *
 * Only blocks present at survey time, inside MAIN, excluding AIR and BEDROCK,
 * form the baseline. Each position is consumed at most once.
 */
public final class MatchPointMapIndex {
    private final ArenaRegion area;
    private final Map<Long, Bucket> buckets = new HashMap<Long, Bucket>();
    private final int firstChunkX, lastChunkX, lastChunkZ;
    private int chunkX, chunkZ, x, z, y;
    private ChunkSnapshot snapshot;
    private int loadingFailures;
    private int inaccessibleChunks;
    private long totalBlocks;
    private long consumed;
    private boolean finished;
    private Bucket preferredBucket;
    private int preferredCount;

    public MatchPointMapIndex(ArenaRegion area) {
        if (area == null) throw new IllegalArgumentException("Area MAIN ausente.");
        this.area = area;
        firstChunkX = area.getMinimumX() >> 4;
        lastChunkX = area.getMaximumX() >> 4;
        lastChunkZ = area.getMaximumZ() >> 4;
        chunkX = firstChunkX;
        chunkZ = area.getMinimumZ() >> 4;
        resetCoordinates();
    }

    /**
     * Bounded server-thread snapshot scan. At most one previously unloaded
     * chunk is loaded per tick and no new chunks are generated.
     */
    public boolean survey(World world, int maxInspections) {
        if (finished) return true;
        if (world == null) return false;
        int inspections = 0, loaded = 0;
        while (!finished && inspections < Math.max(1, maxInspections)) {
            if (snapshot == null) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    if (loaded >= 1) break;
                    loaded++;
                    boolean ok;
                    try {
                        ok = world.loadChunk(chunkX, chunkZ, false);
                    } catch (RuntimeException failure) {
                        ok = false;
                    }
                    if (!ok) {
                        // Retry briefly; do not silently promise 100% of a
                        // chunk that could not be loaded from the world copy.
                        if (++loadingFailures < 20) break;
                        inaccessibleChunks++;
                        loadingFailures = 0;
                        nextChunk();
                        continue;
                    }
                }
                snapshot = world.getChunkAt(chunkX, chunkZ)
                    .getChunkSnapshot(false, false, false);
                loadingFailures = 0;
            }
            if (snapshot.isSectionEmpty(y >> 4)) {
                y = (y & ~15) - 1;
                inspections++;
                if (y < area.getMinimumY()) advanceColumn();
                continue;
            }
            int id = snapshot.getBlockTypeId(x & 15, y, z & 15);
            if (id != 0 && id != Material.BEDROCK.getId()) {
                final long key = chunkKey(chunkX, chunkZ);
                Bucket bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new Bucket(chunkX, chunkZ);
                    buckets.put(key, bucket);
                }
                bucket.add(x, y, z);
                totalBlocks++;
            }
            inspections++;
            if (--y < area.getMinimumY()) advanceColumn();
        }
        if (finished) for (Bucket bucket : buckets.values()) bucket.shuffle();
        return finished;
    }

    private void resetCoordinates() {
        x = Math.max(area.getMinimumX(), chunkX << 4);
        z = Math.max(area.getMinimumZ(), chunkZ << 4);
        y = area.getMaximumY();
    }
    private void advanceColumn() {
        y = area.getMaximumY();
        if (++x <= Math.min(area.getMaximumX(), (chunkX << 4) + 15)) return;
        x = Math.max(area.getMinimumX(), chunkX << 4);
        if (++z <= Math.min(area.getMaximumZ(), (chunkZ << 4) + 15)) return;
        nextChunk();
    }
    private void nextChunk() {
        snapshot = null;
        if (++chunkX > lastChunkX) {
            chunkX = firstChunkX;
            chunkZ++;
        }
        if (chunkZ > lastChunkZ) {
            finished = true;
        } else {
            resetCoordinates();
        }
    }

    public boolean isFinished() { return finished; }
    public int getInaccessibleChunks() { return inaccessibleChunks; }
    public long getTotalBlocks() { return totalBlocks; }
    public long getConsumed() { return consumed; }

    /**
     * Batch up to 64 blocks from the nearest occupied chunk before searching
     * again. This avoids an O(number of chunks) search for EVERY removed block.
     */
    private Cell lastConsumed;

    public Cell consumeNearest(double dragonX, double dragonZ) {
        if (!finished) return null;
        if (preferredBucket == null || preferredBucket.isEmpty() || preferredCount >= 64) {
            preferredBucket = null;
            preferredCount = 0;
            double best = Double.POSITIVE_INFINITY;
            for (Bucket bucket : buckets.values()) {
                if (bucket.isEmpty()) continue;
                double dx = bucket.centerX() - dragonX;
                double dz = bucket.centerZ() - dragonZ;
                double distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    preferredBucket = bucket;
                }
            }
        }
        if (preferredBucket == null) return null;
        preferredCount++;
        consumed++;
        lastConsumed = preferredBucket.poll();
        return lastConsumed;
    }

    /** Requeue an inaccessible block instead of counting it as destroyed. */
    public void retryLast(Cell cell) {
        if (cell == null || cell != lastConsumed || preferredBucket == null) return;
        preferredBucket.rewind();
        preferredCount--;
        consumed--;
        lastConsumed = null;
    }

    /**
     * Expose representative built areas (including diamond/emerald and
     * unowned islands) so the dragon route covers more than bed spawns.
     */
    public List<Position> structuralHotspots(int maxPoints) {
        if (!finished || maxPoints < 1) return Collections.emptyList();
        List<Bucket> candidates = new ArrayList<Bucket>(buckets.values());
        Collections.sort(candidates, new Comparator<Bucket>() {
            @Override public int compare(Bucket a, Bucket b) {
                return Integer.compare(b.originalCount, a.originalCount);
            }
        });
        List<Bucket> chosen = new ArrayList<Bucket>();
        for (Bucket bucket : candidates) {
            if (chosen.size() >= maxPoints) break;
            boolean nearby = false;
            for (Bucket other : chosen) {
                if (Math.abs(other.cx - bucket.cx) <= 2
                        && Math.abs(other.cz - bucket.cz) <= 2) {
                    nearby = true;
                    break;
                }
            }
            if (!nearby && bucket.originalCount > 0) chosen.add(bucket);
        }
        List<Position> result = new ArrayList<Position>();
        for (Bucket bucket : chosen) {
            result.add(new Position(area.getWorld(),
                bucket.centerX(), bucket.averageY(), bucket.centerZ(), 0, 0));
        }
        return result;
    }

    public static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    public static final class Cell {
        public final int x, y, z;
        private Cell(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    }

    private static final class Bucket {
        private final int cx, cz;
        private final List<Cell> samples = new ArrayList<Cell>();
        private long sumY;
        private int originalCount;
        private int cursor;
        private Bucket(int cx, int cz) { this.cx=cx; this.cz=cz; }
        private void add(int x, int y, int z) {
            samples.add(new Cell(x,y,z));
            sumY += y;
            originalCount++;
        }
        private void shuffle() {
            Collections.shuffle(samples, new Random(chunkKey(cx,cz)));
        }
        private boolean isEmpty() { return cursor == samples.size(); }
        private Cell poll() { return samples.get(cursor++); }
        private void rewind() { if (cursor > 0) cursor--; }
        private double centerX() { return cx * 16.0D + 8.0D; }
        private double centerZ() { return cz * 16.0D + 8.0D; }
        private double averageY() { return originalCount == 0 ? 80.0D : sumY/(double)originalCount; }
    }
}
