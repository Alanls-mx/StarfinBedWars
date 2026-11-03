package com.starfinplugins.bedwars.tournament;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Local creation proposal; persisted tournament rules are authoritative. */
public final class TournamentSettings {
    public final boolean enabled, kits, abilities;
    public final ZoneId displayZone;
    public final String id, name, description, mode, arenaGroup;
    public final String championTitle, runnerUpTitle, participantTitle;
    public final int teamSize, minimumTeams, maximumTeams, bestOf, spectatorLimit, checkInGraceMinutes;
    public final long registrationOpens, registrationCloses, startsAt, randomSeed;
    public final boolean publicSpectators, allowSubstitutions;
    public final boolean checkInEnabled;
    public final TournamentBracket.Draw draw;

    public TournamentSettings(YamlConfiguration yaml) {
        enabled = yaml.getBoolean("enabled", false);
        displayZone = ZoneId.of(yaml.getString("display-timezone", "UTC"));
        id = yaml.getString("proposal.id", "cup-01");
        name = yaml.getString("proposal.name", "Copa Starfin");
        description = yaml.getString("proposal.description", "");
        mode = yaml.getString("proposal.mode", "duo").toLowerCase(Locale.ROOT);
        teamSize = yaml.getInt("proposal.team-size", 2);
        minimumTeams = yaml.getInt("proposal.minimum-teams", 4);
        maximumTeams = yaml.getInt("proposal.maximum-teams", 16);
        registrationOpens = Instant.parse(yaml.getString("proposal.registration-opens",
            "2026-12-01T00:00:00Z")).toEpochMilli();
        registrationCloses = Instant.parse(yaml.getString("proposal.registration-closes",
            "2026-12-07T00:00:00Z")).toEpochMilli();
        startsAt = Instant.parse(yaml.getString("proposal.starts-at",
            "2026-12-08T00:00:00Z")).toEpochMilli();
        bestOf = yaml.getInt("proposal.best-of", 3);
        draw = TournamentBracket.Draw.valueOf(yaml.getString("proposal.draw", "RANDOM")
            .toUpperCase(Locale.ROOT));
        randomSeed = yaml.getLong("proposal.random-seed", 71301L);
        arenaGroup = yaml.getString("proposal.arena-group", "tournament");
        spectatorLimit = yaml.getInt("proposal.spectator-limit", 20);
        publicSpectators = yaml.getBoolean("proposal.public-spectators", false);
        allowSubstitutions = yaml.getBoolean("proposal.allow-substitutions", false);
        checkInGraceMinutes = yaml.getInt("proposal.check-in-grace-minutes", 10);
        checkInEnabled = yaml.getBoolean("proposal.check-in-enabled", true);
        championTitle = yaml.getString("proposal.champion-title", "Campeão");
        runnerUpTitle = yaml.getString("proposal.runner-up-title", "Finalista");
        participantTitle = yaml.getString("proposal.participant-title", "Participante");
        kits = yaml.getBoolean("gameplay.kits", false);
        abilities = yaml.getBoolean("gameplay.abilities", false);
        if (!id.matches("[a-z0-9_-]{1,32}") || name == null || name.trim().isEmpty()
                || name.length() > 64 || description == null || description.length() > 256
                || !Arrays.asList("solo", "duo", "trio", "squad", "custom").contains(mode)
                || teamSize < 1 || teamSize > 16
                || (!"custom".equals(mode) && teamSize != modeSize(mode))
                || minimumTeams < 2 || maximumTeams < minimumTeams || maximumTeams > 64
                || registrationOpens <= 0 || registrationCloses <= registrationOpens
                || startsAt < registrationCloses || startsAt - registrationOpens > 366L * 86400000L
                || (bestOf != 1 && bestOf != 3 && bestOf != 5)
                || arenaGroup == null || !arenaGroup.matches("[a-z0-9_-]{1,32}")
                || spectatorLimit < 0 || spectatorLimit > 100
                || checkInGraceMinutes < 1 || checkInGraceMinutes > 1440
                || invalidTitle(championTitle) || invalidTitle(runnerUpTitle)
                || invalidTitle(participantTitle)
                || yaml.getBoolean("proposal.automatic-walkover", false)
                || yaml.getBoolean("statistics.count-as-casual", false)
                || yaml.getBoolean("statistics.affect-ranked-mmr", false))
            throw new IllegalArgumentException("tournaments.yml invalido");
    }
    private static boolean invalidTitle(String text) {
        return text == null || text.length() > 48;
    }
    private static int modeSize(String mode) {
        switch (mode) {
            case "solo": return 1; case "duo": return 2; case "trio": return 3;
            default: return 4;
        }
    }
    public static TournamentSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "tournaments.yml");
        if (!file.exists()) plugin.saveResource("tournaments.yml", false);
        return new TournamentSettings(YamlConfiguration.loadConfiguration(file));
    }
}
