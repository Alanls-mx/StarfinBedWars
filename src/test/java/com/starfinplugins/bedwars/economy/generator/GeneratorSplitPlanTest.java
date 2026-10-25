package com.starfinplugins.bedwars.economy.generator;

import com.starfinplugins.bedwars.gameplay.GameplaySettings.SplitPolicy;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratorSplitPlanTest {
    @Test public void roundRobinConservesOneRecipientAndAdvances() {
        GeneratorSplitPlan first = GeneratorSplitPlan.choose(3, 0, 4, SplitPolicy.ROUND_ROBIN);
        GeneratorSplitPlan second = GeneratorSplitPlan.choose(3, first.nextCursor, 4,
            SplitPolicy.ROUND_ROBIN);
        assertEquals(Collections.singletonList(0), first.recipientIndexes);
        assertEquals(Collections.singletonList(1), second.recipientIndexes);
        assertEquals(2, second.nextCursor);
    }

    @Test public void sharedCapsRecipientsAndEmptyFallsBackToGround() {
        assertEquals(Arrays.asList(0, 1),
            GeneratorSplitPlan.choose(4, 0, 2, SplitPolicy.SHARED).recipientIndexes);
        assertTrue(GeneratorSplitPlan.choose(0, 0, 2, SplitPolicy.SHARED)
            .recipientIndexes.isEmpty());
    }
}
