package com.starfinplugins.bedwars.shop.special;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BridgeEggDecayQueueTest {
    @Test public void fadesSectionsInExactConstructionOrder() {
        BridgeEggDecayQueue<String> bridge = new BridgeEggDecayQueue<String>();
        bridge.addSlice(Arrays.asList("start-center", "start-left", "start-right"));
        bridge.addSlice(Arrays.asList("middle-center", "middle-left"));
        bridge.addSlice(Arrays.asList("tip-center", "tip-right"));
        assertEquals(3, bridge.size());
        assertEquals(Arrays.asList("start-center", "start-left", "start-right"),
            bridge.pollOldest());
        assertEquals(Arrays.asList("middle-center", "middle-left"),
            bridge.pollOldest());
        assertEquals(Arrays.asList("tip-center", "tip-right"),
            bridge.pollOldest());
        assertTrue(bridge.isEmpty());
        assertNull(bridge.pollOldest());
    }

    @Test public void peekingUnavailableChunkDoesNotDiscardASection() {
        BridgeEggDecayQueue<Integer> queue = new BridgeEggDecayQueue<Integer>();
        queue.addSlice(Arrays.asList(1, 2, 3));
        queue.addSlice(Arrays.asList(4, 5, 6));
        assertEquals(Arrays.asList(1, 2, 3), queue.peekOldest());
        assertEquals(Arrays.asList(1, 2, 3), queue.peekOldest());
        assertEquals(2, queue.size());
        assertEquals(Arrays.asList(1, 2, 3), queue.pollOldest());
        assertEquals(Arrays.asList(4, 5, 6), queue.pollOldest());
    }

    @Test public void ignoresUnbuiltSectionsAndCopiesInput() {
        BridgeEggDecayQueue<Integer> bridge = new BridgeEggDecayQueue<Integer>();
        bridge.addSlice(null);
        bridge.addSlice(Collections.<Integer>emptyList());
        List<Integer> original = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        bridge.addSlice(original);
        original.clear();
        assertEquals(1, bridge.size());
        assertEquals(Arrays.asList(1, 2, 3), bridge.pollOldest());
        assertFalse(bridge.size() > 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void returnedSectionsCannotBeMutated() {
        BridgeEggDecayQueue<Integer> bridge = new BridgeEggDecayQueue<Integer>();
        bridge.addSlice(Arrays.asList(1, 2));
        bridge.pollOldest().clear();
    }
}
