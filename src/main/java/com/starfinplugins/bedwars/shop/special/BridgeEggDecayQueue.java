package com.starfinplugins.bedwars.shop.special;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Construction-order slices. Each slice corresponds to one consecutive
 * cross-section of bridge blocks, so the bridge vanishes from its start
 * toward its tip, rather than removing random individual blocks.
 *
 * The scheduler lives in SpecialItemService; this queue is independent
 * of Bukkit for deterministic unit tests.
 */
public final class BridgeEggDecayQueue<T> {
    private final Deque<List<T>> slices = new ArrayDeque<List<T>>();

    public void addSlice(List<T> builtInOrder) {
        if (builtInOrder == null || builtInOrder.isEmpty()) return;
        slices.addLast(Collections.unmodifiableList(new ArrayList<T>(builtInOrder)));
    }

    /** Inspect the next section without consuming it (e.g. unloaded chunk). */
    public List<T> peekOldest() { return slices.peekFirst(); }

    /** First built slice disappears first. */
    public List<T> pollOldest() {
        return slices.pollFirst();
    }

    public boolean isEmpty() { return slices.isEmpty(); }
    public int size() { return slices.size(); }
}
