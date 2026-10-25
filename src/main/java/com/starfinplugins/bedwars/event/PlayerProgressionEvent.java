package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.stats.ProgressionUpdate;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the Bukkit thread only after MySQL commits the match result. */
public final class PlayerProgressionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ProgressionUpdate update;

    public PlayerProgressionEvent(ProgressionUpdate update) { this.update = update; }
    public ProgressionUpdate getUpdate() { return update; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
