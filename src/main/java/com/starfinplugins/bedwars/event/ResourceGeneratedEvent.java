package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.economy.GeneratorInstance;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ResourceGeneratedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMatch match;
    private final GeneratorInstance generator;
    private final int amount;

    public ResourceGeneratedEvent(GameMatch match, GeneratorInstance generator, int amount) {
        this.match = match;
        this.generator = generator;
        this.amount = amount;
    }

    public GameMatch getMatch() { return match; }
    public GeneratorInstance getGenerator() { return generator; }
    public int getAmount() { return amount; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
