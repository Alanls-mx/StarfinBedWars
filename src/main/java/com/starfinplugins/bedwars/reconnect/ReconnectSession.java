package com.starfinplugins.bedwars.reconnect;

import com.starfinplugins.bedwars.domain.match.ParticipantState;
import java.util.UUID;

public final class ReconnectSession {
    public enum State { DISCONNECTED, RESTORING, COMPLETED, EXPIRED, INVALIDATED }
    private final UUID playerId;
    private final UUID matchId;
    private final String arenaId;
    private final String teamId;
    private final long disconnectedAt;
    private final long expiresAt;
    private final ParticipantState lifeState;
    private final PlayerStateSnapshot snapshot;
    private State state = State.DISCONNECTED;

    public ReconnectSession(UUID playerId, UUID matchId, String arenaId, String teamId,
                            long disconnectedAt, long expiresAt, ParticipantState lifeState,
                            PlayerStateSnapshot snapshot) {
        if (playerId == null || matchId == null || arenaId == null || teamId == null
                || lifeState == null || snapshot == null || expiresAt <= disconnectedAt) {
            throw new IllegalArgumentException("Sessao de reconnect invalida.");
        }
        this.playerId = playerId;
        this.matchId = matchId;
        this.arenaId = arenaId;
        this.teamId = teamId;
        this.disconnectedAt = disconnectedAt;
        this.expiresAt = expiresAt;
        this.lifeState = lifeState;
        this.snapshot = snapshot;
    }
    public UUID getPlayerId() { return playerId; }
    public UUID getMatchId() { return matchId; }
    public String getArenaId() { return arenaId; }
    public String getTeamId() { return teamId; }
    public long getDisconnectedAt() { return disconnectedAt; }
    public long getExpiresAt() { return expiresAt; }
    public ParticipantState getLifeState() { return lifeState; }
    public PlayerStateSnapshot getSnapshot() { return snapshot; }
    public State getState() { return state; }
    public boolean isWithinDeadline(long now) { return now <= expiresAt; }
    public boolean begin(long now) {
        if (state != State.DISCONNECTED || !isWithinDeadline(now)) return false;
        state = State.RESTORING;
        return true;
    }
    public void complete() { transition(State.RESTORING, State.COMPLETED); }
    public void retry() { transition(State.RESTORING, State.DISCONNECTED); }
    public void expire() { transition(State.DISCONNECTED, State.EXPIRED); }
    public void invalidate() {
        if (state == State.DISCONNECTED || state == State.RESTORING) state = State.INVALIDATED;
    }
    private void transition(State from, State to) {
        if (state != from) throw new IllegalStateException("Transicao de reconnect invalida.");
        state = to;
    }
}
