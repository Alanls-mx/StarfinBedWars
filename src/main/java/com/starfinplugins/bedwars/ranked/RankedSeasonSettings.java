package com.starfinplugins.bedwars.ranked;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Validated local proposal; MySQL remains authoritative after creation. */
public final class RankedSeasonSettings {
    public final boolean enabled;
    public final ZoneId displayZone;
    public final String id, name, description, resetType, sourceSeason;
    public final long startsAt, endsAt;
    public final Set<String> modes;
    public final int placementRequired, rewardMinimumGames;
    public final int rankingCacheSeconds, closingGraceMinutes;
    public final double resetFactor;
    public final boolean rewardsEnabled;
    public final List<Title> titles;

    public RankedSeasonSettings(YamlConfiguration yaml) {
        enabled = yaml.getBoolean("enabled", false);
        displayZone = ZoneId.of(yaml.getString("display-timezone", "UTC"));
        id = yaml.getString("current.id", "season-01");
        name = yaml.getString("current.name", "Temporada 1");
        description = yaml.getString("current.description", "");
        startsAt = Instant.parse(yaml.getString("current.starts-at", "2026-11-01T00:00:00Z")).toEpochMilli();
        endsAt = Instant.parse(yaml.getString("current.ends-at", "2027-01-01T00:00:00Z")).toEpochMilli();
        placementRequired = yaml.getInt("current.placement-required", 5);
        resetType = yaml.getString("current.reset.type", "FRESH_START").toUpperCase(java.util.Locale.ROOT);
        sourceSeason = yaml.getString("current.reset.source-season", "");
        resetFactor = yaml.getDouble("current.reset.factor", 0.25D);
        rewardsEnabled = yaml.getBoolean("current.rewards.enabled", false);
        rewardMinimumGames = yaml.getInt("current.rewards.minimum-games", 10);
        rankingCacheSeconds = yaml.getInt("ranking-cache-seconds", 20);
        closingGraceMinutes = yaml.getInt("closing-grace-minutes", 60);
        List<Title> configuredTitles = new ArrayList<Title>();
        Set<String> titleIds = new HashSet<String>();
        for (Map<?, ?> item : yaml.getMapList("current.rewards.titles")) {
            Title title = new Title(item);
            if (!titleIds.add(title.id)) throw new IllegalArgumentException("Titulo sazonal duplicado");
            configuredTitles.add(title);
        }
        titles = Collections.unmodifiableList(configuredTitles);
        Set<String> enabledModes = new HashSet<String>();
        for (String mode : new String[] {"solo", "duo", "trio", "squad", "custom"})
            if (yaml.getBoolean("current.modes." + mode, !"custom".equals(mode))) enabledModes.add(mode);
        modes = Collections.unmodifiableSet(enabledModes);
        if (id == null || !id.matches("[a-z0-9_-]{1,32}") || name == null || name.trim().isEmpty()
                || name.length() > 64 || description == null || description.length() > 256
                || startsAt <= 0 || endsAt <= startsAt || endsAt - startsAt > 366L * 86400000L
                || placementRequired < 0 || placementRequired > 100 || modes.isEmpty()
                || rewardMinimumGames < 0 || rewardMinimumGames > 100000
                || rankingCacheSeconds < 5 || rankingCacheSeconds > 300
                || closingGraceMinutes < 1 || closingGraceMinutes > 10080
                || !Double.isFinite(resetFactor) || resetFactor < 0D || resetFactor > 1D
                || !("FRESH_START".equals(resetType) || "SOFT_RESET".equals(resetType))
                || ("SOFT_RESET".equals(resetType)
                    && (sourceSeason == null || !sourceSeason.matches("[a-z0-9_-]{1,32}"))))
            throw new IllegalArgumentException("seasons.yml contem temporada invalida");
    }
    public static RankedSeasonSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "seasons.yml");
        if (!file.exists()) plugin.saveResource("seasons.yml", false);
        return new RankedSeasonSettings(YamlConfiguration.loadConfiguration(file));
    }
    public static final class Title {
        public final String id, mode, pt, en, es, sourceRule;
        public final int minimumRating, maximumPosition, minimumGames;
        Title(Map<?, ?> data) {
            id = value(data, "id", ""); mode = value(data, "mode", "");
            pt = value(data, "pt_BR", ""); en = value(data, "en_US", pt);
            es = value(data, "es_ES", pt);
            sourceRule = value(data, "source", "FINAL").toUpperCase(java.util.Locale.ROOT);
            minimumRating = integer(data, "minimum-rating", 0);
            maximumPosition = integer(data, "maximum-position", 0);
            minimumGames = integer(data, "minimum-games", 0);
            if (!id.matches("[a-z0-9_-]{1,32}") || (!mode.isEmpty()
                    && !java.util.Arrays.asList("solo", "duo", "trio", "squad", "custom").contains(mode))
                    || pt.isEmpty() || pt.length() > 24 || en.isEmpty() || en.length() > 24
                    || es.isEmpty() || es.length() > 24
                    || !java.util.Arrays.asList("FINAL", "PEAK").contains(sourceRule)
                    || minimumRating < 0 || maximumPosition < 0 || minimumGames < 0)
                throw new IllegalArgumentException("Recompensa sazonal invalida: " + id);
        }
        private static String value(Map<?, ?> data, String key, String fallback) {
            Object found = data.get(key); return found == null ? fallback : found.toString();
        }
        private static int integer(Map<?, ?> data, String key, int fallback) {
            Object found = data.get(key);
            if (found == null) return fallback;
            try { return Integer.parseInt(found.toString()); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Numero invalido: " + key); }
        }
    }
}
