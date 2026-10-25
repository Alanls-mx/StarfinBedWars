package com.starfinplugins.bedwars.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable main-thread snapshot. JDBC worker never touches Bukkit objects or mutable matches. */
public final class NetworkSnapshot {
    public final String serverId, instanceId;
    public final long sampledAt;
    public final int onlinePlayers, maxServerPlayers;
    public final List<Arena> arenas;
    public final List<Match> matches;
    public final List<Player> players;

    public NetworkSnapshot(String serverId, String instanceId, long sampledAt,
                           int onlinePlayers, int maxServerPlayers,
                           List<Arena> arenas, List<Match> matches, List<Player> players) {
        this.serverId = serverId;
        this.instanceId = instanceId;
        this.sampledAt = sampledAt;
        this.onlinePlayers = onlinePlayers;
        this.maxServerPlayers = maxServerPlayers;
        this.arenas = Collections.unmodifiableList(new ArrayList<Arena>(arenas));
        this.matches = Collections.unmodifiableList(new ArrayList<Match>(matches));
        this.players = Collections.unmodifiableList(new ArrayList<Player>(players));
    }

    public static final class Arena {
        public final String id, name, sourceWorld, yaml, configHash, mode, group, state, matchId;
        public final int teamCount, teamSize, generatorCount, minPlayers, maxPlayers, players, countdownSeconds;
        public final boolean enabled, editing, joinable;

        public Arena(String id, String name, String sourceWorld, String yaml,
                     String mode, int teamCount, int teamSize, int generatorCount, int minPlayers, int maxPlayers,
                     int players, int countdownSeconds, boolean enabled, boolean editing,
                     boolean joinable, String state, String matchId) {
            this(id, name, sourceWorld, yaml, mode, "default", teamCount, teamSize, generatorCount,
                minPlayers, maxPlayers, players, countdownSeconds, enabled, editing, joinable, state, matchId);
        }

        public Arena(String id, String name, String sourceWorld, String yaml,
                     String mode, String group, int teamCount, int teamSize, int generatorCount, int minPlayers, int maxPlayers,
                     int players, int countdownSeconds, boolean enabled, boolean editing,
                     boolean joinable, String state, String matchId) {
            if (yaml == null || yaml.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
                throw new IllegalArgumentException("YAML da arena excede 1 MiB ou esta ausente.");
            }
            this.id = id;
            this.name = name;
            this.sourceWorld = sourceWorld;
            this.yaml = yaml;
            this.configHash = sha256(yaml);
            this.mode = mode;
            this.group = group;
            this.teamCount = teamCount;
            this.teamSize = teamSize;
            this.generatorCount = generatorCount;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
            this.players = players;
            this.countdownSeconds = countdownSeconds;
            this.enabled = enabled;
            this.editing = editing;
            this.joinable = joinable;
            this.state = state;
            this.matchId = matchId;
        }

        private static String sha256(String value) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(64);
                for (byte b : hash) hex.append(String.format("%02x", b & 0xff));
                return hex.toString();
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 indisponivel", impossible);
            }
        }
    }

    public static final class Match {
        public final String id, arenaId, state, winnerTeam, endReason;
        public final int players, connectedPlayers, maxPlayers;
        public final long startedAt, endedAt;

        public Match(String id, String arenaId, String state, String winnerTeam, String endReason,
                     int players, int connectedPlayers, int maxPlayers, long startedAt, long endedAt) {
            this.id = id;
            this.arenaId = arenaId;
            this.state = state;
            this.winnerTeam = winnerTeam;
            this.endReason = endReason;
            this.players = players;
            this.connectedPlayers = connectedPlayers;
            this.maxPlayers = maxPlayers;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
        }
    }

    public static final class Player {
        public final String uuid, name, role, arenaId, matchId, teamId;

        public Player(String uuid, String name, String role, String arenaId,
                      String matchId, String teamId) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
            this.arenaId = arenaId;
            this.matchId = matchId;
            this.teamId = teamId;
        }
    }
}
