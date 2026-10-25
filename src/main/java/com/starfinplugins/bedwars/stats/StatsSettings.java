package com.starfinplugins.bedwars.stats;

import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatsSettings {
    public final boolean enabled;
    public final String host, schema, user, password, sslMode;
    public final int port, poolSize, timeoutMs;
    public final XpProgression progression;
    public final MatchStatsTracker.Awards awards;
    public final boolean rankingsEnabled, rankingsUpdateAfterMatch;
    public final int rankingsCacheSeconds, rankingsDefaultPageSize, rankingsMaximumPageSize;

    StatsSettings(FileConfiguration yml) {
        enabled = yml.getBoolean("enabled", false);
        host = yml.getString("mysql.host", "127.0.0.1").trim();
        port = yml.getInt("mysql.port", 3306);
        schema = yml.getString("mysql.database", "starfin_network").trim();
        user = yml.getString("mysql.username", "starfinbedwars").trim();
        password = yml.getString("mysql.password", "");
        sslMode = yml.getString("mysql.ssl-mode", "REQUIRED").toUpperCase(java.util.Locale.ROOT);
        poolSize = yml.getInt("mysql.pool-size", 5);
        timeoutMs = yml.getInt("mysql.connection-timeout-ms", 10000);
        progression = new XpProgression(yml.getLong("levels.base-xp", 1000),
            yml.getLong("levels.increase-per-level", 250));
        awards = new MatchStatsTracker.Awards(yml.getLong("xp.participation", 25),
            yml.getLong("xp.victory", 100), yml.getLong("xp.kill", 10),
            yml.getLong("xp.final-kill", 20), yml.getLong("xp.bed", 40));
        rankingsEnabled = yml.getBoolean("rankings.enabled", true);
        rankingsCacheSeconds = yml.getInt("rankings.cache-seconds", 60);
        rankingsDefaultPageSize = yml.getInt("rankings.default-page-size", 10);
        rankingsMaximumPageSize = yml.getInt("rankings.maximum-page-size", 50);
        rankingsUpdateAfterMatch = yml.getBoolean("rankings.update-after-match", true);
        if (rankingsCacheSeconds < 5 || rankingsCacheSeconds > 3600
                || rankingsDefaultPageSize < 1 || rankingsMaximumPageSize > 50
                || rankingsMaximumPageSize < rankingsDefaultPageSize)
            throw new IllegalArgumentException("Configuracao rankings invalida");
        if (enabled) {
            if (!host.matches("[A-Za-z0-9.\\-_:]+") || !schema.matches("[A-Za-z0-9_]+")
                    || user.isEmpty() || port < 1 || port > 65535 || poolSize < 1 || poolSize > 20
                    || timeoutMs < 1000 || timeoutMs > 60000
                    || !("REQUIRED".equals(sslMode) || "DISABLED".equals(sslMode)
                        || "VERIFY_CA".equals(sslMode) || "VERIFY_IDENTITY".equals(sslMode))) {
                throw new IllegalArgumentException("Config stats.yml/mysql invalida");
            }
        }
    }

    public static StatsSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "stats.yml");
        if (!file.exists()) plugin.saveResource("stats.yml", false);
        return new StatsSettings(YamlConfiguration.loadConfiguration(file));
    }
}
