package com.starfinplugins.bedwars.economy.generator;

import com.starfinplugins.bedwars.gameplay.GameplaySettings.SplitPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure selection policy; the generator owns the cursor per match and generator. */
public final class GeneratorSplitPlan {
    public final List<Integer> recipientIndexes;
    public final int nextCursor;
    private GeneratorSplitPlan(List<Integer> indexes, int nextCursor) {
        recipientIndexes = Collections.unmodifiableList(indexes);
        this.nextCursor = nextCursor;
    }
    public static GeneratorSplitPlan choose(int eligibleCount, int cursor, int maximum,
                                            SplitPolicy policy) {
        if (eligibleCount < 0 || cursor < 0 || maximum < 1 || policy == null)
            throw new IllegalArgumentException("Plano de gerador invalido");
        List<Integer> indexes = new ArrayList<Integer>();
        if (eligibleCount == 0) return new GeneratorSplitPlan(indexes, cursor);
        if (policy == SplitPolicy.ROUND_ROBIN) {
            indexes.add(cursor % eligibleCount);
            return new GeneratorSplitPlan(indexes, cursor == Integer.MAX_VALUE ? 0 : cursor + 1);
        }
        for (int i = 0; i < Math.min(eligibleCount, maximum); i++) indexes.add(i);
        return new GeneratorSplitPlan(indexes, cursor);
    }
}
