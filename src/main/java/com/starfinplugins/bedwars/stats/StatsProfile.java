package com.starfinplugins.bedwars.stats;

import java.util.UUID;

/** A read-only, network-wide player profile. */
public final class StatsProfile {
    public final UUID uuid;
    public final String name;
    public final long games, wins, losses, draws, kills, finalKills, deaths, beds, totalXp;
    public final long finalDeaths, firstPlay, lastPlay;

    public StatsProfile(UUID uuid, String name, long games, long wins, long losses, long draws,
                        long kills, long finalKills, long deaths, long beds, long totalXp) {
        this(uuid, name, games, wins, losses, draws, kills, finalKills, deaths, 0,
            beds, totalXp, 0, 0);
    }

    public StatsProfile(UUID uuid, String name, long games, long wins, long losses, long draws,
                        long kills, long finalKills, long deaths, long finalDeaths, long beds,
                        long totalXp, long firstPlay, long lastPlay) {
        this.uuid = uuid;
        this.name = name;
        this.games = games;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.kills = kills;
        this.finalKills = finalKills;
        this.deaths = deaths;
        this.finalDeaths = finalDeaths;
        this.beds = beds;
        this.totalXp = totalXp;
        this.firstPlay = firstPlay;
        this.lastPlay = lastPlay;
    }

    public static StatsProfile empty(UUID uuid, String name) {
        return new StatsProfile(uuid, name, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
