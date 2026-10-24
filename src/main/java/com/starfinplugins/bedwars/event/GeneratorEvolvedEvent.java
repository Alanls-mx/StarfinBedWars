package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.economy.GeneratorInstance;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class GeneratorEvolvedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMatch match;
    private final GeneratorInstance generator;
    private final int previousLevel;
    private final int newLevel;

    public GeneratorEvolvedEvent(GameMatch match, GeneratorInstance generator, int previousLevel, int newLevel) {
        this.match = match;
        this.generator = generator;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    public GameMatch getMatch() { return match; }
    public GeneratorInstance getGenerator() { return generator; }
    public int getPreviousLevel() { return previousLevel; }
    public int getNewLevel() { return newLevel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
