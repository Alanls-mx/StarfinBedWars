package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ShopPurchaseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMatch match;
    private final UUID playerId;
    private final ShopItemDefinition item;

    public ShopPurchaseEvent(GameMatch match, UUID playerId, ShopItemDefinition item) {
        this.match = match;
        this.playerId = playerId;
        this.item = item;
    }

    public GameMatch getMatch() { return match; }
    public UUID getPlayerId() { return playerId; }
    public ShopItemDefinition getItem() { return item; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
