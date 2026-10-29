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
    private final Map<UUID, TeamHold> teamHolds = new LinkedHashMap<UUID, TeamHold>();
    private final Map<UUID, TeamHold> admittedHolds = new LinkedHashMap<UUID, TeamHold>();
    private final Map<UUID, UUID> participantGroups = new LinkedHashMap<UUID, UUID>();
    private final Map<UUID, Integer> groupSizes = new LinkedHashMap<UUID, Integer>();
    private final Map<UUID, UUID> groupLeaders = new LinkedHashMap<UUID, UUID>();
    private MatchState state = MatchState.WAITING;
    private MatchEndReason endReason;
    private String winnerTeamId;
    private Instant startedAt;
    private Instant endedAt;
    private MatchType type = MatchType.CASUAL;
    private String competitiveContext;

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
    public MatchType getType() { return type; }
    public String getCompetitiveContext() { return competitiveContext; }
    /** Called only by the authorized ranked admission service before the opening starts. */
    void authorizeRanked(String context) {
        if ((state != MatchState.WAITING && state != MatchState.STARTING)
                || startedAt != null || type != MatchType.CASUAL
                || context == null || !context.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalStateException("Contexto competitivo invalido ou tardio");
        type = MatchType.RANKED;
        competitiveContext = context;
    }
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
        return addParticipant(player, null, null, 1, 0L);
    }

    /** A network reservation names a team; unreserved players retain normal balancing. */
    public boolean addParticipant(UUID player, String teamId, UUID groupId,
                                  int groupSize, long groupExpiresAt) {
        return addParticipant(player, teamId, groupId, groupSize, groupExpiresAt, null);
    }

    public boolean addParticipant(UUID player, String teamId, UUID groupId,
                                  int groupSize, long groupExpiresAt, UUID leaderId) {
        if (type == MatchType.RANKED
                || (state != MatchState.WAITING && state != MatchState.STARTING) || participants.containsKey(player)
                || participants.size() >= arena.getMaximumPlayers()) {
            return false;
        }
        expireTeamHolds(System.currentTimeMillis());
        GameTeam selected = null;
        if (teamId != null) {
            selected = teams.get(teamId);
            if (selected == null || selected.getMembers().size() >= selected.getDefinition().getCapacity())
                return false;
            if (groupId != null && groupSize > 1 && selected.getDefinition().getCapacity() > 1) {
                if (!holdGroup(groupId, teamId, groupSize, groupExpiresAt)) return false;
                TeamHold hold = teamHolds.get(groupId);
                if (!hold.teamId.equals(teamId) || hold.remaining < 1) return false;
            } else if (freeSlots(selected) < 1) return false;
        } else {
            for (GameTeam team : teams.values()) {
                if (freeSlots(team) > 0 && (selected == null
                        || team.getMembers().size() + heldSlots(team.getDefinition().getId())
                            < selected.getMembers().size() + heldSlots(selected.getDefinition().getId())))
                    selected = team;
            }
        }
        if (selected == null || !selected.add(player)) {
            return false;
        }
        TeamHold hold = groupId == null ? null : teamHolds.get(groupId);
        if (hold != null) {
            admittedHolds.put(player, hold);
            if (--hold.remaining == 0) teamHolds.remove(groupId);
        }
        participants.put(player, new GameParticipant(player, id.toString(), selected.getDefinition().getId()));
        if (groupId != null && groupSize > 1 && selected.getDefinition().getCapacity() > 1) {
            participantGroups.put(player, groupId);
            groupSizes.put(groupId, groupSize);
            if (leaderId != null) groupLeaders.put(groupId, leaderId);
        }
        return true;
    }

    public enum TeamChoice { MOVED, ALREADY, FULL, WAIT_FOR_PARTY, LEADER_ONLY, UNAVAILABLE }

    public int availableTeamSlots(String teamId) {
        GameTeam team = teams.get(teamId);
        if (team == null) return 0;
        expireTeamHolds(System.currentTimeMillis());
        return Math.max(0, freeSlots(team));
    }

    /** Move every admitted member of a Party together, while still in the lobby. */
    public TeamChoice chooseTeam(UUID player, String targetId) {
        if (type == MatchType.RANKED
                || (state != MatchState.WAITING && state != MatchState.STARTING)
                || !participants.containsKey(player)) return TeamChoice.UNAVAILABLE;
        GameTeam target = teams.get(targetId);
        if (target == null) return TeamChoice.UNAVAILABLE;
        GameParticipant requester = participants.get(player);
        if (requester.getTeamId().equals(targetId)) return TeamChoice.ALREADY;
        UUID groupId = participantGroups.get(player);
        java.util.List<UUID> moving = new java.util.ArrayList<UUID>();
        if (groupId != null) {
            UUID leader = groupLeaders.get(groupId);
            if (leader != null && !leader.equals(player)) return TeamChoice.LEADER_ONLY;
            for (Map.Entry<UUID, UUID> entry : participantGroups.entrySet())
                if (groupId.equals(entry.getValue())) moving.add(entry.getKey());
            if (moving.size() != groupSizes.get(groupId) || teamHolds.containsKey(groupId))
                return TeamChoice.WAIT_FOR_PARTY;
        } else moving.add(player);
        if (availableTeamSlots(targetId) < moving.size()) return TeamChoice.FULL;
        GameTeam previous = teams.get(requester.getTeamId());
        for (UUID member : moving) {
            GameParticipant participant = participants.get(member);
            if (participant == null || !participant.getTeamId().equals(requester.getTeamId()))
                return TeamChoice.UNAVAILABLE;
        }
        for (UUID member : moving) previous.remove(member);
        for (UUID member : moving) {
            target.add(member);
            participants.get(member).setTeamId(targetId);
        }
        if (groupId != null) {
            TeamHold hold = admittedHolds.get(player);
            if (hold != null) hold.teamId = targetId;
        }
        return TeamChoice.MOVED;
    }

    public boolean hasPendingGroup() {
        expireTeamHolds(System.currentTimeMillis());
        return !teamHolds.isEmpty();
    }

    public boolean holdGroup(UUID groupId, String teamId, int size, long expiresAt) {
        if (groupId == null || teamId == null || size < 2) return false;
        expireTeamHolds(System.currentTimeMillis());
        TeamHold existing = teamHolds.get(groupId);
        if (existing != null) return existing.teamId.equals(teamId);
        GameTeam team = teams.get(teamId);
        if (team == null || team.getDefinition().getCapacity() < size
                || freeSlots(team) < size || expiresAt <= System.currentTimeMillis()) return false;
        teamHolds.put(groupId, new TeamHold(groupId, teamId, size, expiresAt));
        return true;
    }

    private int freeSlots(GameTeam team) {
        return team.getDefinition().getCapacity() - team.getMembers().size()
            - heldSlots(team.getDefinition().getId());
    }

    private int heldSlots(String teamId) {
        int count = 0;
        for (TeamHold hold : teamHolds.values()) if (hold.teamId.equals(teamId)) count += hold.remaining;
        return count;
    }

    private void expireTeamHolds(long now) {
        teamHolds.values().removeIf(hold -> hold.expiresAt <= now);
    }

    private static final class TeamHold {
        final UUID groupId;
        String teamId;
        int remaining;
        final long expiresAt;
        TeamHold(UUID groupId, String teamId, int remaining, long expiresAt) {
            this.groupId = groupId; this.teamId = teamId;
            this.remaining = remaining; this.expiresAt = expiresAt;
        }
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
        TeamHold hold = admittedHolds.remove(player);
        UUID groupId = participantGroups.remove(player);
        if (groupId != null && !participantGroups.containsValue(groupId)) {
            groupSizes.remove(groupId);
            groupLeaders.remove(groupId);
        }
        if (hold != null && hold.expiresAt > System.currentTimeMillis()) {
            hold.remaining++;
            teamHolds.put(hold.groupId, hold);
        }
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
