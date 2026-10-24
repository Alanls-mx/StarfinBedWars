package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class TeamUpgradePurchasedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMatch match;
    private final UUID playerId;
    private final String teamId;
    private final TeamUpgradeType upgrade;
    private final TeamTrapType trap;
    private final int level;

    public TeamUpgradePurchasedEvent(GameMatch match, UUID playerId, String teamId,
                                     TeamUpgradeType upgrade, TeamTrapType trap, int level) {
        this.match = match;
        this.playerId = playerId;
        this.teamId = teamId;
        this.upgrade = upgrade;
        this.trap = trap;
        this.level = level;
    }
    public GameMatch getMatch() { return match; }
    public UUID getPlayerId() { return playerId; }
    public String getTeamId() { return teamId; }
    public TeamUpgradeType getUpgrade() { return upgrade; }
    public TeamTrapType getTrap() { return trap; }
    public int getLevel() { return level; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
