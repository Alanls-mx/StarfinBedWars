package com.starfinplugins.bedwars.shop;

import java.util.UUID;

/**
 * Validated identification of a shop NPC, never inferred from the clicked
 * entity's appearance. Team-specific NPCs cannot be used by other teams.
 */
public final class ShopNpcRoute {
    private final UUID matchId;
    private final String kind;
    private final String teamId;

    private ShopNpcRoute(UUID matchId, String kind, String teamId) {
        this.matchId = matchId;
        this.kind = kind;
        this.teamId = teamId;
    }

    public static ShopNpcRoute parse(String metadata) {
        if (metadata == null) return null;
        String[] parts = metadata.split("\\|", -1);
        if (parts.length != 2) return null;
        UUID matchId;
        try {
            matchId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        String[] role = parts[1].split(":", -1);
        if (role.length < 1 || role.length > 2) return null;
        if (!"items".equals(role[0]) && !"upgrades".equals(role[0])) return null;
        if (role.length == 2 && role[1].isEmpty()) return null;
        return new ShopNpcRoute(matchId, role[0], role.length == 2 ? role[1] : null);
    }

    public UUID getMatchId() { return matchId; }
    public boolean isUpgrades() { return "upgrades".equals(kind); }

    public boolean canAccess(UUID activeMatchId, String playerTeamId) {
        return matchId.equals(activeMatchId)
            && (teamId == null || teamId.equals(playerTeamId));
    }
}
