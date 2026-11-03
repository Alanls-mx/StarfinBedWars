package com.starfinplugins.bedwars.tournament;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Authoritative game assignment loaded from MySQL, never inferred from the client. */
public final class TournamentAdmission {
    public final TournamentGame game;
    public final String tournamentId, mode, arenaGroup;
    public final int teamSize;
    public final String tournamentName, teamAName, teamBName;
    public final int round, winsA, winsB;
    public final Map<UUID, String> roster;

    TournamentAdmission(TournamentGame game, String tournamentId, String mode,
            String arenaGroup, int teamSize, String tournamentName,
            String teamAName, String teamBName, int round, int winsA, int winsB,
            Map<UUID, String> roster) {
        this.game = game; this.tournamentId = tournamentId; this.mode = mode;
        this.arenaGroup = arenaGroup; this.teamSize = teamSize;
        this.tournamentName = tournamentName; this.teamAName = teamAName;
        this.teamBName = teamBName; this.round = round;
        this.winsA = winsA; this.winsB = winsB;
        this.roster = Collections.unmodifiableMap(new LinkedHashMap<UUID, String>(roster));
    }
}
