package com.starfinplugins.bedwars.tournament;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TournamentTeam {
    public final UUID id, captain;
    public final String tournamentId, name, state;
    public final int seed;
    public final List<UUID> members;
    TournamentTeam(UUID id, UUID captain, String tournamentId, String name,
            String state, int seed, List<UUID> members) {
        this.id = id; this.captain = captain; this.tournamentId = tournamentId;
        this.name = name; this.state = state; this.seed = seed;
        this.members = Collections.unmodifiableList(members);
    }
}
