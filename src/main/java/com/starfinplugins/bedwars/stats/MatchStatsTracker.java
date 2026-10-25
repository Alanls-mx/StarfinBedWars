package com.starfinplugins.bedwars.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread-only accumulator: no Bukkit objects or SQL access. */
public final class MatchStatsTracker {
    private final UUID matchId;
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private final Awards awards;

    public MatchStatsTracker(UUID matchId, Awards awards) {
        if (matchId == null || awards == null) throw new IllegalArgumentException("Partida ausente");
        this.matchId = matchId;
        this.awards = awards;
    }

    public void addPlayer(UUID uuid, String team, String name) {
        if (uuid == null || team == null) throw new IllegalArgumentException("Participante invalido");
        if (!entries.containsKey(uuid)) entries.put(uuid, new Entry(team, name));
    }

    public void markAbandoned(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry != null) entry.abandoned = true;
    }

    public void death(UUID victim, UUID killer, boolean finalKill) {
        Entry dead = entries.get(victim);
        if (dead == null) return;
        dead.deaths++;
        if (finalKill) dead.finalDeaths++;
        Entry attacker = entries.get(killer);
        if (attacker != null && attacker != dead && !attacker.team.equals(dead.team)) {
            attacker.kills++;
            if (finalKill) attacker.finalKills++;
        }
    }

    public void bed(UUID player) {
        Entry entry = entries.get(player);
        if (entry != null) entry.beds++;
    }

    public List<MatchResult> finish(String winnerTeam, boolean draw) {
        List<MatchResult> results = new ArrayList<MatchResult>();
        for (Map.Entry<UUID, Entry> value : entries.entrySet()) {
            Entry e = value.getValue();
            MatchResult.Outcome outcome = draw ? MatchResult.Outcome.DRAW
                : !e.abandoned && winnerTeam != null && winnerTeam.equals(e.team)
                    ? MatchResult.Outcome.WIN : MatchResult.Outcome.LOSS;
            long xp = awards.participation + e.kills * awards.kill
                + e.finalKills * awards.finalKill + e.beds * awards.bed
                + (outcome == MatchResult.Outcome.WIN ? awards.victory : 0L);
            results.add(new MatchResult(matchId, value.getKey(), e.name, e.kills,
                e.finalKills, e.deaths, e.finalDeaths, e.beds, outcome, xp));
        }
        return results;
    }

    private static final class Entry {
        final String team;
        final String name;
        int kills, finalKills, deaths, finalDeaths, beds;
        boolean abandoned;
        Entry(String team, String name) { this.team = team; this.name = name; }
    }

    public static final class Awards {
        public final long participation, victory, kill, finalKill, bed;
        public Awards(long participation, long victory, long kill, long finalKill, long bed) {
            if (participation < 0 || victory < 0 || kill < 0 || finalKill < 0 || bed < 0
                    || participation > 1000000 || victory > 1000000 || kill > 1000000
                    || finalKill > 1000000 || bed > 1000000) {
                throw new IllegalArgumentException("Recompensas de XP fora do limite 0..1000000");
            }
            this.participation = participation;
            this.victory = victory;
            this.kill = kill;
            this.finalKill = finalKill;
            this.bed = bed;
        }
    }
}
