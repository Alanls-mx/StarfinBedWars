package com.starfinplugins.bedwars.stats;

import java.util.UUID;

/** A read-only, network-wide player profile. */
public final class StatsProfile {
    public final UUID uuid;
    public final String name;
    public final long games, wins, losses, draws, kills, finalKills, deaths, beds, totalXp;

    public StatsProfile(UUID uuid, String name, long games, long wins, long losses, long draws,
                        long kills, long finalKills, long deaths, long beds, long totalXp) {
        this.uuid = uuid;
        this.name = name;
        this.games = games;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.kills = kills;
        this.finalKills = finalKills;
        this.deaths = deaths;
        this.beds = beds;
        this.totalXp = totalXp;
    }

    public static StatsProfile empty(UUID uuid, String name) {
        return new StatsProfile(uuid, name, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
