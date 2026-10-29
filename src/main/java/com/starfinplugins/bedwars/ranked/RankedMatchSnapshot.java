package com.starfinplugins.bedwars.ranked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-derived, immutable final match view; never built from a client packet. */
public final class RankedMatchSnapshot {
    public final UUID matchId;
    public final String mode, arenaId, serverId, instanceId, context, winnerTeam;
    public final long startedAt, endedAt;
    public final boolean draw;
    public final int initialRating, minimumRating, kFactor;
    public final List<Member> members;

    public RankedMatchSnapshot(UUID matchId, String mode, String arenaId, String serverId,
                               String instanceId, String context, String winnerTeam,
                               long startedAt, long endedAt, boolean draw, List<Member> members,
                               int initialRating, int minimumRating, int kFactor) {
        if (matchId == null || mode == null || arenaId == null || serverId == null
                || instanceId == null || context == null || startedAt <= 0
                || endedAt < startedAt || draw == (winnerTeam != null)
                || members == null || members.size() < 2 || minimumRating < 0
                || initialRating < minimumRating || kFactor < 1)
            throw new IllegalArgumentException("Snapshot competitivo invalido");
        Set<UUID> players = new HashSet<UUID>();
        Set<String> teams = new HashSet<String>();
        for (Member member : members) {
            if (member == null || !players.add(member.playerId))
                throw new IllegalArgumentException("Participante duplicado");
            teams.add(member.teamId);
        }
        if (teams.size() < 2 || (!draw && !teams.contains(winnerTeam)))
            throw new IllegalArgumentException("Equipes competitivas invalidas");
        this.matchId = matchId; this.mode = mode; this.arenaId = arenaId;
        this.serverId = serverId; this.instanceId = instanceId; this.context = context;
        this.winnerTeam = winnerTeam; this.startedAt = startedAt;
        this.endedAt = endedAt; this.draw = draw;
        this.initialRating = initialRating; this.minimumRating = minimumRating;
        this.kFactor = kFactor;
        this.members = Collections.unmodifiableList(new ArrayList<Member>(members));
    }

    public static final class Member {
        public final UUID playerId;
        public final String name, teamId;
        public final boolean abandoned;
        public Member(UUID playerId, String name, String teamId, boolean abandoned) {
            if (playerId == null || teamId == null || !teamId.matches("[A-Za-z0-9_-]{1,40}"))
                throw new IllegalArgumentException("Membro competitivo invalido");
            this.playerId = playerId;
            this.name = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
            this.teamId = teamId;
            this.abandoned = abandoned;
        }
    }
}
