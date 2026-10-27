package com.starfinplugins.bedwars.domain.match;

import java.util.UUID;

public final class GameParticipant {
    private final UUID playerId;
    private final String matchId;
    private String teamId;
    private ParticipantState state;
    private ParticipantState lifeState;
    private ConnectionState connectionState;
    private final EquipmentProgress equipment = new EquipmentProgress();

    public GameParticipant(UUID playerId, String matchId, String teamId) {
        if (playerId == null || matchId == null || teamId == null) {
            throw new IllegalArgumentException("participant identity ausente");
        }
        this.playerId = playerId;
        this.matchId = matchId;
        this.teamId = teamId;
        this.state = ParticipantState.CONNECTED;
        this.lifeState = ParticipantState.CONNECTED;
        this.connectionState = ConnectionState.CONNECTED;
    }

    public UUID getPlayerId() { return playerId; }
    public String getMatchId() { return matchId; }
    public String getTeamId() { return teamId; }
    void setTeamId(String teamId) {
        if (teamId == null) throw new IllegalArgumentException("team ausente");
        this.teamId = teamId;
    }
    public ParticipantState getState() { return state; }
    public ParticipantState getLifeState() { return lifeState; }
    public ConnectionState getConnectionState() { return connectionState; }
    public EquipmentProgress getEquipment() { return equipment; }
    public void setConnectionState(ConnectionState connectionState) {
        if (connectionState == null) throw new IllegalArgumentException("connection state ausente");
        this.connectionState = connectionState;
    }
    public void setState(ParticipantState state) {
        if (state == null) {
            throw new IllegalArgumentException("participant state ausente");
        }
        this.state = state;
        if (state == ParticipantState.DISCONNECTED) connectionState = ConnectionState.DISCONNECTED;
        else if (state == ParticipantState.ABANDONED) {
            lifeState = state;
            connectionState = ConnectionState.ABANDONED;
        } else {
            lifeState = state;
            connectionState = ConnectionState.CONNECTED;
        }
    }
}
