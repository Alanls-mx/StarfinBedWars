package com.starfinplugins.bedwars.match;

import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.BitSet;
import java.util.TreeSet;
import java.util.NavigableSet;
import java.util.Set;
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
    private Bucket lastBucket;
    private final NavigableSet<Bucket> ready = new TreeSet<Bucket>(new Comparator<Bucket>() {
        @Override public int compare(Bucket a, Bucket b) {
            // Compare fractions exactly. Small islands must not disappear before
            // larger islands have received the same proportional damage.
            long left = (long) (a.originalCount - a.remaining) * b.originalCount;
            long right = (long) (b.originalCount - b.remaining) * a.originalCount;
            int progress = Long.compare(left, right);
            return progress != 0 ? progress : Long.compare(chunkKey(a.cx,a.cz), chunkKey(b.cx,b.cz));
        }
    });
    private final Set<Bucket> deferred = new HashSet<Bucket>();

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
        Bucket completed = buckets.get(chunkKey(chunkX, chunkZ));
        if (completed != null && !completed.isEmpty()) ready.add(completed);
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

    private Cell lastConsumed;

    /** Failed chunks become eligible again on the next bounded server tick. */
    public void beginRemovalBatch() {
        ready.addAll(deferred);
        deferred.clear();
    }

    /** Select the least-damaged built region, proportional to its real baseline. */
    public Cell consumeBalanced() {
        if (!finished || ready.isEmpty()) return null;
        // A local blast can leave stale array slots. If cleaning 64 entries
        // does not reach a live one, let other regions advance this tick.
        for (int checked = 0; checked < 8 && !ready.isEmpty(); checked++) {
            Bucket bucket = ready.pollFirst();
            Cell cell = bucket.poll();
            if (cell == null) {
                if (!bucket.isEmpty()) deferred.add(bucket);
                continue;
            }
            consumed++;
            if (!bucket.isEmpty()) ready.add(bucket);
            lastBucket = bucket;
            lastConsumed = cell;
            return cell;
        }
        lastBucket = null;
        lastConsumed = null;
        return null;
    }

    /** Requeue an inaccessible block without inflating the destruction ratio. */
    public void retryLast(Cell cell) {
        if (cell == null || cell != lastConsumed || lastBucket == null) return;
        ready.remove(lastBucket);
        lastBucket.restore(cell);
        deferred.add(lastBucket);
        consumed--;
        lastConsumed = null;
    }

    /** Local dragon/lightning impacts share the global AND regional schedule. */
    public boolean canConsumeAt(int x, int y, int z, long globalTarget) {
        if (!finished || consumed >= globalTarget || totalBlocks == 0 || y < 0 || y > 255) return false;
        Bucket bucket = buckets.get(chunkKey(x >> 4, z >> 4));
        if (bucket == null || deferred.contains(bucket) || !bucket.contains(x,y,z)) return false;
        long regionalTarget = (globalTarget * bucket.originalCount + totalBlocks - 1) / totalBlocks;
        return bucket.originalCount - bucket.remaining < regionalTarget;
    }

    /** Called only after a real removal (or observation that it is already AIR). */
    public void consumeAt(int x, int y, int z) {
        Bucket bucket = buckets.get(chunkKey(x >> 4,z >> 4));
        if (bucket == null || !bucket.contains(x,y,z)) return;
        ready.remove(bucket);
        bucket.consume(x,y,z);
        consumed++;
        if (!bucket.isEmpty() && !deferred.contains(bucket)) ready.add(bucket);
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
        // Packed local X/Z and world Y: at most 256 KiB per full chunk,
        // instead of one object and reference for every solid block.
        private int[] samples = new int[64];
        private final Random random;
        private long sumY;
        private int originalCount;
        private int size;
        private int remaining;
        private final BitSet live = new BitSet();
        private Bucket(int cx, int cz) {
            this.cx=cx; this.cz=cz;
            random = new Random(chunkKey(cx, cz));
        }
        private void append(int x, int y, int z) {
            if (size == samples.length) samples = Arrays.copyOf(samples, size * 2);
            samples[size++] = (y << 8) | ((z & 15) << 4) | (x & 15);
        }
        private void add(int x, int y, int z) {
            append(x, y, z);
            sumY += y;
            originalCount++;
            remaining++;
            live.set(pack(x,y,z));
        }
        private static int pack(int x, int y, int z) {
            return (y << 8) | ((z & 15) << 4) | (x & 15);
        }
        private boolean contains(int x,int y,int z) { return live.get(pack(x,y,z)); }
        private void consume(int x,int y,int z) {
            live.clear(pack(x,y,z));
            remaining--;
        }
        private boolean isEmpty() { return remaining == 0; }
        private Cell poll() {
            // Incremental Fisher-Yates selection: constant work per removal,
            // no whole-map shuffle on the tick that finishes the survey.
            // Local impacts can leave stale array entries. Bound their cleanup too.
            for (int skipped=0; size>0 && skipped<64; skipped++) {
                int index = random.nextInt(size);
                int packed = samples[index];
                samples[index] = samples[--size];
                if (!live.get(packed)) continue;
                live.clear(packed);
                remaining--;
                return new Cell((cx << 4) + (packed & 15), packed >>> 8,
                    (cz << 4) + ((packed >>> 4) & 15));
            }
            return null;
        }
        private void restore(Cell cell) {
            append(cell.x, cell.y, cell.z);
            live.set(pack(cell.x,cell.y,cell.z));
            remaining++;
        }
        private double centerX() { return cx * 16.0D + 8.0D; }
        private double centerZ() { return cz * 16.0D + 8.0D; }
        private double averageY() { return originalCount == 0 ? 80.0D : sumY/(double)originalCount; }
    }
}
