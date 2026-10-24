package com.starfinplugins.bedwars.domain.economy;

import org.bukkit.potion.PotionEffectType;

public final class TeamTrapDefinition {
    private final TeamTrapType type;
    private final int cost;
    private final PotionEffectType effect;
    private final int durationTicks;
    private final int amplifier;

    public TeamTrapDefinition(TeamTrapType type, int cost, PotionEffectType effect,
                              int durationTicks, int amplifier) {
        if (type == null || cost < 1 || durationTicks < 0 || durationTicks > 72000
                || amplifier < 0 || amplifier > 10 || (effect == null && durationTicks != 0)
                || (effect != null && durationTicks < 1)) {
            throw new IllegalArgumentException("Configuracao de armadilha invalida.");
        }
        this.type = type;
        this.cost = cost;
        this.effect = effect;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    public TeamTrapType getType() { return type; }
    public int getCost() { return cost; }
    public PotionEffectType getEffect() { return effect; }
    public int getDurationTicks() { return durationTicks; }
    public int getAmplifier() { return amplifier; }
}
