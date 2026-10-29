package com.starfinplugins.bedwars.ranked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Verified database allocation, never constructed from a client message. */
public final class RankedAllocation {
    public final UUID id;
    public final String arenaId, mode, context;
    public final long expiresAt;
    public final Map<UUID, String> teams;
    public RankedAllocation(UUID id, String arenaId, String mode, String context,
                            long expiresAt, Map<UUID, String> teams) {
        this.id = id; this.arenaId = arenaId; this.mode = mode;
        this.context = context; this.expiresAt = expiresAt;
        this.teams = Collections.unmodifiableMap(new LinkedHashMap<UUID, String>(teams));
    }
}
