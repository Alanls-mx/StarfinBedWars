package com.starfinplugins.bedwars.tournament;

import java.util.UUID;

/** Immutable authoritative tournament row. */
public final class Tournament {
    public enum State {
        DRAFT, SCHEDULED, REGISTRATION_OPEN, REGISTRATION_CLOSED, READY,
        RUNNING, PAUSED, FINALIZING, FINISHED, CANCELLED
    }
    public final String id, name, description, mode, arenaGroup;
    public final State state;
    public final int teamSize, minimumTeams, maximumTeams, bestOf, spectatorLimit;
    public final long registrationOpens, registrationCloses, startsAt, randomSeed;
    public final TournamentBracket.Draw draw;
    public final boolean publicSpectators, allowSubstitutions;
    public final UUID champion, runnerUp;
    Tournament(String id, String name, String description, String mode, String arenaGroup,
            State state, int teamSize, int minimumTeams, int maximumTeams, int bestOf,
            int spectatorLimit, long registrationOpens, long registrationCloses, long startsAt,
            long randomSeed, TournamentBracket.Draw draw, boolean publicSpectators,
            boolean allowSubstitutions, UUID champion, UUID runnerUp) {
        this.id = id; this.name = name; this.description = description; this.mode = mode;
        this.arenaGroup = arenaGroup; this.state = state; this.teamSize = teamSize;
        this.minimumTeams = minimumTeams; this.maximumTeams = maximumTeams; this.bestOf = bestOf;
        this.spectatorLimit = spectatorLimit; this.registrationOpens = registrationOpens;
        this.registrationCloses = registrationCloses; this.startsAt = startsAt;
        this.randomSeed = randomSeed; this.draw = draw;
        this.publicSpectators = publicSpectators; this.allowSubstitutions = allowSubstitutions;
        this.champion = champion; this.runnerUp = runnerUp;
    }
}
