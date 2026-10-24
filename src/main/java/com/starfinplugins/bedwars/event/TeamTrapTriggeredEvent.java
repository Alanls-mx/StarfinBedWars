package com.starfinplugins.bedwars.event;

import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class TeamTrapTriggeredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameMatch match;
    private final String teamId;
    private final UUID intruderId;
    private final TeamTrapType trap;

    public TeamTrapTriggeredEvent(GameMatch match, String teamId, UUID intruderId, TeamTrapType trap) {
        this.match = match;
        this.teamId = teamId;
        this.intruderId = intruderId;
        this.trap = trap;
    }
    public GameMatch getMatch() { return match; }
    public String getTeamId() { return teamId; }
    public UUID getIntruderId() { return intruderId; }
    public TeamTrapType getTrap() { return trap; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
