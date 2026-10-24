package com.starfinplugins.bedwars.domain.economy;

import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.List;
import java.util.UUID;

public final class GeneratorInstance {
    private final UUID matchId;
    private final ResourceGeneratorDefinition definition;
    private int level;
    private long nextProductionTick;
    private boolean active = true;
    private int producedAmount;

    public GeneratorInstance(GameMatch match, ResourceGeneratorDefinition definition, long nowTick) {
        this(match, definition, nowTick, true);
    }

    public GeneratorInstance(GameMatch match, ResourceGeneratorDefinition definition, long nowTick,
                             boolean active) {
        if (match == null || definition == null) throw new IllegalArgumentException("Match/gerador ausente.");
        this.matchId = match.getId();
        this.definition = definition;
        this.level = definition.getStartingLevel();
        this.nextProductionTick = nowTick;
        this.active = active;
    }

    public UUID getMatchId() { return matchId; }
    public ResourceGeneratorDefinition getDefinition() { return definition; }
    public int getLevel() { return level; }
    public boolean isActive() { return active; }
    public int getProducedAmount() { return producedAmount; }
    public int getTicksUntilProduction(long nowTick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, nextProductionTick - nowTick));
    }
    public void scheduleFirstProduction(long nowTick, List<GeneratorLevel> levels) {
        if (levels == null || level < 1 || level > levels.size()) {
            throw new IllegalArgumentException("Nivel inicial do gerador invalido.");
        }
        nextProductionTick = nowTick + levels.get(level - 1).getIntervalTicks();
    }
    public boolean isDue(long nowTick) { return active && nowTick >= nextProductionTick; }

    public boolean activate(long nowTick, List<GeneratorLevel> levels) {
        if (active || levels == null || level < 1 || level > levels.size()) return false;
        active = true;
        nextProductionTick = nowTick + levels.get(level - 1).getIntervalTicks();
        return true;
    }

    public boolean advance(long nowTick, List<GeneratorLevel> levels, int currentDropped) {
        if (!active || nowTick < nextProductionTick || level < 1 || level > levels.size()) return false;
        GeneratorLevel settings = levels.get(level - 1);
        nextProductionTick += settings.getIntervalTicks();
        if (nextProductionTick <= nowTick) nextProductionTick = nowTick + settings.getIntervalTicks();
        int maximum = settings.getMaximumDropped();
        if (definition.getTeamId() == null
                && (definition.getResource() == ResourceType.DIAMOND
                    || definition.getResource() == ResourceType.EMERALD)) {
            maximum = Math.min(maximum, 4);
        }
        if (currentDropped + settings.getAmount() > maximum) return false;
        producedAmount += settings.getAmount();
        return true;
    }

    public boolean upgrade(List<GeneratorLevel> levels) {
        if (!active || level >= levels.size()) return false;
        level++;
        return true;
    }

    /** A timed central upgrade starts a fresh production cycle at the new interval. */
    public boolean upgradeAt(long nowTick, List<GeneratorLevel> levels) {
        if (!upgrade(levels)) return false;
        nextProductionTick = nowTick + levels.get(level - 1).getIntervalTicks();
        return true;
    }

    public void stop() { active = false; }

    public int getIntervalTicks(List<GeneratorLevel> levels) {
        return levels.get(level - 1).getIntervalTicks();
    }
}
