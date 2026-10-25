package com.starfinplugins.bedwars.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A result accepted by MySQL, never a predicted in-memory award. */
public final class ProgressionUpdate {
    public final UUID playerId;
    public final long earnedXp, totalXp;
    public final int previousLevel, currentLevel;
    public final List<String> unlockedTitles;

    public ProgressionUpdate(UUID playerId, long earnedXp, long totalXp,
                             int previousLevel, int currentLevel, List<String> unlockedTitles) {
        this.playerId = playerId;
        this.earnedXp = earnedXp;
        this.totalXp = totalXp;
        this.previousLevel = previousLevel;
        this.currentLevel = currentLevel;
        this.unlockedTitles = Collections.unmodifiableList(new ArrayList<String>(unlockedTitles));
    }
}
