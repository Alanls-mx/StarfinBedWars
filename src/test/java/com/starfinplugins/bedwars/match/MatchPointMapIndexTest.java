package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.Position;
import java.util.List;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.junit.Test;

/** Proves real blocks on a central island WITHOUT beds are in the index. */
public class MatchPointMapIndexTest {
    @Test public void includesBothTeamIslandsAndNeutralCenter() {
        final Map<String,Integer> original = new HashMap<String,Integer>();
        original.put(key(1,65,1), 1);  // island A
        original.put(key(50,65,1), 1); // island B
        original.put(key(25,65,25), 20); // center island GLASS
        original.put(key(26,65,25), 35); // wool
        original.put(key(25,65,26), 7); // BEDROCK excluded
        ArenaRegion area = new ArenaRegion("map", 0,60,0,63,70,31);
        MatchPointMapIndex index = new MatchPointMapIndex(area);
        World world = proxyWorld(original);
        for (int tick=0; tick < 500 && !index.isFinished(); tick++) {
            index.survey(world, 300);
        }
        assertTrue(index.isFinished());
        assertEquals(4, index.getTotalBlocks());
        assertEquals(0, index.getInaccessibleChunks());
        List<Position> landmarks = index.structuralHotspots(8);
        assertTrue("Survey must discover neutral island without bed",
            landmarks.stream().anyMatch(p -> Math.abs(p.getX() - 24) < 16
                && Math.abs(p.getZ() - 24) < 16));
        int seen = 0;
        boolean center = false;
        for (int i=0;i<4;i++) {
            MatchPointMapIndex.Cell cell = index.consumeNearest(25,25);
            assertNotNull(cell);
            if (cell.x >= 24 && cell.x <= 26 && cell.z >=24) center = true;
            seen++;
        }
        assertTrue("Neutral center structures must be eligible",center);
        assertEquals(seen,index.getConsumed());
        assertNull(index.consumeNearest(25,25));
    }

    @Test public void inaccessibleBlockCanBeRetriedWithoutInflatingPercent() {
        Map<String,Integer> blocks = new HashMap<String,Integer>();
        blocks.put(key(0,70,0), 1);
        MatchPointMapIndex index = new MatchPointMapIndex(
            new ArenaRegion("map",0,68,0,0,72,0));
        for (int i=0;i<5 && !index.isFinished();i++) index.survey(proxyWorld(blocks),100);
        MatchPointMapIndex.Cell cell = index.consumeNearest(0,0);
        assertNotNull(cell);
        assertEquals(1L,index.getConsumed());
        index.retryLast(cell);
        assertEquals(0L,index.getConsumed());
        assertNull(index.consumeNearest(0,0));
        index.beginRemovalBatch();
        assertNotNull(index.consumeNearest(0,0));
        assertEquals(1L,index.getConsumed());
    }

    @Test public void inaccessibleChunkDoesNotStarveOtherChunks() {
        Map<String,Integer> blocks = new HashMap<String,Integer>();
        blocks.put(key(-16,70,-16), 1);
        blocks.put(key(16,70,16), 1);
        MatchPointMapIndex index = new MatchPointMapIndex(
            new ArenaRegion("map",-16,70,-16,16,70,16));
        while (!index.survey(proxyWorld(blocks), 100)) { }
        index.beginRemovalBatch();
        MatchPointMapIndex.Cell blocked = index.consumeNearest(-16,-16);
        assertEquals(-16, blocked.x);
        assertEquals(-16, blocked.z);
        index.retryLast(blocked);
        index.retryLast(blocked);
        assertEquals(0L, index.getConsumed());
        MatchPointMapIndex.Cell accessible = index.consumeNearest(-16,-16);
        assertEquals(16, accessible.x);
        assertEquals(70, accessible.y);
        assertEquals(1L, index.getConsumed());
        assertNull(index.consumeNearest(-16,-16));
        index.beginRemovalBatch();
        assertEquals(-16, index.consumeNearest(0,0).x);
        assertEquals(2L, index.getConsumed());
        assertNull(index.consumeNearest(0,0));
    }

    @Test public void denseChunkConsumesEveryCoordinateExactlyOnce() {
        Map<String,Integer> blocks = new HashMap<String,Integer>();
        for (int x=-16; x<0; x++) for (int z=-16; z<0; z++) {
            blocks.put(key(x,0,z),1);
            blocks.put(key(x,255,z),1);
        }
        MatchPointMapIndex index = new MatchPointMapIndex(
            new ArenaRegion("map",-16,0,-16,-1,255,-1));
        while (!index.survey(proxyWorld(blocks), 300)) { }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        MatchPointMapIndex.Cell cell;
        while ((cell=index.consumeNearest(0,0)) != null) {
            assertTrue(seen.add(key(cell.x,cell.y,cell.z)));
        }
        assertEquals(blocks.keySet(),seen);
        assertEquals(512L,index.getConsumed());
    }

    private static String key(int x,int y,int z) { return x+":"+y+":"+z; }

    private static World proxyWorld(final Map<String,Integer> ids) {
        final Map<String,Chunk> chunks = new HashMap<String,Chunk>();
        return (World)Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, new InvocationHandler() {
                @Override public Object invoke(Object obj, Method method, Object[] args) {
                    String name = method.getName();
                    if ("isChunkLoaded".equals(name)) return true;
                    if ("getChunkAt".equals(name)) {
                        String key = args[0]+":"+args[1];
                        Chunk chunk = chunks.get(key);
                        if (chunk == null) {
                            chunk = chunkProxy(((Number)args[0]).intValue(),
                                ((Number)args[1]).intValue(), ids);
                            chunks.put(key,chunk);
                        }
                        return chunk;
                    }
                    return defaultValue(method.getReturnType());
                }
            });
    }

    private static Chunk chunkProxy(final int cx, final int cz,
                                    final Map<String,Integer> ids) {
        return (Chunk)Proxy.newProxyInstance(Chunk.class.getClassLoader(),
            new Class<?>[]{Chunk.class}, new InvocationHandler() {
                @Override public Object invoke(Object obj,Method method,Object[] args) {
                    if ("getChunkSnapshot".equals(method.getName())) {
                        return Proxy.newProxyInstance(ChunkSnapshot.class.getClassLoader(),
                            new Class<?>[]{ChunkSnapshot.class}, new InvocationHandler() {
                                @Override public Object invoke(Object o,Method m,Object[] values) {
                                    if ("isSectionEmpty".equals(m.getName())) return false;
                                    if ("getBlockTypeId".equals(m.getName())) {
                                        int x=(cx << 4)+((Number)values[0]).intValue();
                                        int y=((Number)values[1]).intValue();
                                        int z=(cz << 4)+((Number)values[2]).intValue();
                                        Integer type=ids.get(key(x,y,z));
                                        return type==null?0:type;
                                    }
                                    return defaultValue(m.getReturnType());
                                }
                            });
                    }
                    return defaultValue(method.getReturnType());
                }
            });
    }
    private static Object defaultValue(Class<?> result) {
        if (result==boolean.class)return false;
        if (result==int.class)return 0;
        if (result==long.class)return 0L;
        if (result==float.class)return 0F;
        if (result==double.class)return 0D;
        return null;
    }
}
