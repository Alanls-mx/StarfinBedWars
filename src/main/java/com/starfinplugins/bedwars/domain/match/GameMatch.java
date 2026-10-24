package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameMatch {
    private final UUID id = UUID.randomUUID();
    private final ArenaDefinition arena;
    private final Map<String, GameTeam> teams = new LinkedHashMap<String, GameTeam>();
    private final Map<UUID, GameParticipant> participants = new LinkedHashMap<UUID, GameParticipant>();
    private MatchState state = MatchState.WAITING;
    private MatchEndReason endReason;
    private String winnerTeamId;
    private Instant startedAt;
    private Instant endedAt;

    public GameMatch(ArenaDefinition arena) {
        if (arena == null) {
            throw new IllegalArgumentException("arena ausente");
        }
        this.arena = arena;
        for (TeamDefinition definition : arena.getTeamList()) {
            teams.put(definition.getId(), new GameTeam(definition));
        }
    }

    public UUID getId() { return id; }
    public ArenaDefinition getArena() { return arena; }
    public MatchState getState() { return state; }
    public MatchEndReason getEndReason() { return endReason; }
    public String getWinnerTeamId() { return winnerTeamId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Map<String, GameTeam> getTeams() { return Collections.unmodifiableMap(teams); }
    public Map<UUID, GameParticipant> getParticipants() { return Collections.unmodifiableMap(participants); }
    public List<GameParticipant> getParticipantList() {
        return Collections.unmodifiableList(new ArrayList<GameParticipant>(participants.values()));
    }
    public GameParticipant getParticipant(UUID player) { return participants.get(player); }
    public GameTeam getTeam(String id) { return teams.get(id); }
    public int getConnectedCount() {
        int count = 0;
        for (GameParticipant participant : participants.values()) {
            if (participant.getConnectionState() == ConnectionState.CONNECTED
                    && participant.getState() != ParticipantState.ELIMINATED
                    && participant.getState() != ParticipantState.ABANDONED) {
                count++;
            }
        }
        return count;
    }
    public boolean addParticipant(UUID player) {
        if ((state != MatchState.WAITING && state != MatchState.STARTING) || participants.containsKey(player)
                || participants.size() >= arena.getMaximumPlayers()) {
            return false;
        }
        GameTeam selected = null;
        for (GameTeam team : teams.values()) {
            if (team.getMembers().size() < team.getDefinition().getCapacity()
                    && (selected == null || team.getMembers().size() < selected.getMembers().size())) {
                selected = team;
            }
        }
        if (selected == null || !selected.add(player)) {
            return false;
        }
        participants.put(player, new GameParticipant(player, id.toString(), selected.getDefinition().getId()));
        return true;
    }
    public boolean removeWaitingParticipant(UUID player) {
        if (state != MatchState.WAITING && state != MatchState.STARTING) {
            return false;
        }
        GameParticipant participant = participants.remove(player);
        if (participant == null) {
            return false;
        }
        teams.get(participant.getTeamId()).remove(player);
        return true;
    }
    public boolean setParticipantState(UUID player, ParticipantState next) {
        GameParticipant participant = participants.get(player);
        if (participant == null || state != MatchState.IN_GAME
                || participant.getState() == ParticipantState.ELIMINATED
                || participant.getState() == ParticipantState.ABANDONED) {
            return false;
        }
        participant.setState(next);
        return true;
    }
    public boolean destroyBed(String teamId) {
        GameTeam team = teams.get(teamId);
        if (state != MatchState.IN_GAME || team == null || !team.isBedAlive()) {
            return false;
        }
        team.destroyBed();
        return true;
    }
    public void transition(MatchState next) {
        if (next == null || !allowed(state, next)) {
            throw new IllegalStateException("transicao invalida: " + state + " -> " + next);
        }
        state = next;
        if (next == MatchState.IN_GAME) {
            startedAt = Instant.now();
        }
    }
    private boolean allowed(MatchState from, MatchState to) {
        if (to == MatchState.ERROR) {
            return from != MatchState.ERROR;
        }
        switch (from) {
            case WAITING: return to == MatchState.STARTING || to == MatchState.ENDING;
            case STARTING: return to == MatchState.WAITING || to == MatchState.IN_GAME || to == MatchState.ENDING;
            case IN_GAME: return to == MatchState.ENDING;
            case ENDING: return to == MatchState.RESETTING;
            case RESETTING: return to == MatchState.WAITING;
            default: return false;
        }
    }
    public List<GameTeam> getEligibleTeams() {
        List<GameTeam> eligible = new ArrayList<GameTeam>();
        for (GameTeam team : teams.values()) {
            if (team.isEligible(this)) {
                eligible.add(team);
            }
        }
        return Collections.unmodifiableList(eligible);
    }
    public boolean finish(MatchEndReason reason, String winner) {
        if (state == MatchState.ENDING || state == MatchState.RESETTING
                || state == MatchState.ERROR || reason == null) {
            return false;
        }
        transition(MatchState.ENDING);
        endReason = reason;
        winnerTeamId = winner;
        endedAt = Instant.now();
        return true;
    }
}
