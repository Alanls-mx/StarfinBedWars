package com.starfinplugins.bedwars.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks only temporary bridge blocks, by match, position and egg UUID.
 * A manually placed replacement or an explosion clears ownership so the
 * expired egg cannot remove somebody else's block at the same position.
 */
public final class BridgeBlockOwnership {
    private final Map<UUID, Map<String, UUID>> byMatch =
        new HashMap<UUID, Map<String, UUID>>();

    public void record(UUID matchId, String locationKey, UUID eggId) {
        if (matchId == null || locationKey == null || eggId == null) return;
        Map<String, UUID> blocks = byMatch.get(matchId);
        if (blocks == null) {
            blocks = new HashMap<String, UUID>();
            byMatch.put(matchId, blocks);
        }
        blocks.put(locationKey, eggId);
    }

    public boolean owns(UUID matchId, String locationKey, UUID eggId) {
        Map<String, UUID> blocks = byMatch.get(matchId);
        return blocks != null && eggId != null && eggId.equals(blocks.get(locationKey));
    }

    public void forget(UUID matchId, String locationKey) {
        Map<String, UUID> blocks = byMatch.get(matchId);
        if (blocks == null) return;
        blocks.remove(locationKey);
        if (blocks.isEmpty()) byMatch.remove(matchId);
    }

    public void clearMatch(UUID matchId) { byMatch.remove(matchId); }
    public void clear() { byMatch.clear(); }
}
