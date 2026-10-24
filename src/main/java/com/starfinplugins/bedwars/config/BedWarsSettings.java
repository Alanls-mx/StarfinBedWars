package com.starfinplugins.bedwars.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedWarsSettings {
    private final boolean debug;
    private final int respawnSeconds;
    private final int spawnOpeningSeconds;
    private final int automaticBoundsWidth;
    private final int automaticBoundsDepth;
    private final int automaticBoundsUpHeight;
    private final int automaticBoundsDownDepth;
    private final int automaticBoundsMaximumDimension;

    private BedWarsSettings(boolean debug, int respawnSeconds, int spawnOpeningSeconds,
                            int automaticBoundsWidth, int automaticBoundsDepth,
                            int automaticBoundsUpHeight, int automaticBoundsDownDepth,
                            int automaticBoundsMaximumDimension) {
        this.debug = debug;
        this.respawnSeconds = respawnSeconds;
        this.spawnOpeningSeconds = spawnOpeningSeconds;
        this.automaticBoundsWidth = automaticBoundsWidth;
        this.automaticBoundsDepth = automaticBoundsDepth;
        this.automaticBoundsUpHeight = automaticBoundsUpHeight;
        this.automaticBoundsDownDepth = automaticBoundsDownDepth;
        this.automaticBoundsMaximumDimension = automaticBoundsMaximumDimension;
    }

    public static BedWarsSettings load(JavaPlugin plugin) throws IOException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta de dados do plugin.");
        }

        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
        }
        if (!file.isFile()) {
            throw new IOException("config.yml nao e um arquivo regular.");
        }

        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (Exception error) {
            throw new IOException("Nao foi possivel ler config.yml: " + error.getMessage(), error);
        }
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(reader);
            config.setDefaults(defaults);
        } catch (Exception error) {
            throw new IOException("Nao foi possivel carregar os valores padrao de config.yml.", error);
        }
        return from(config);
    }

    public static BedWarsSettings from(FileConfiguration config) throws IOException {
        if (!config.isInt("config-version") || config.getInt("config-version") != 1) {
            throw new IOException("config-version ausente ou nao suportada; esperado: 1.");
        }
        requireBoolean(config, "general.debug");
        int respawnDelay = readInteger(config, "match.respawn-seconds", 3, 0, 30);
        int spawnOpeningDelay = readInteger(config, "match.spawn-opening-seconds", 10, 1, 60);
        int width = readInteger(config, "arena-bounds.default-width", 280, 1, 30000);
        int depth = readInteger(config, "arena-bounds.default-depth", 280, 1, 30000);
        int upHeight = readInteger(config, "arena-bounds.default-up-height", 50, 0, 255);
        int downDepth = readInteger(config, "arena-bounds.default-down-depth", 50, 0, 255);
        int maximumDimension = readInteger(config, "arena-bounds.maximum-dimension", 1000, 1, 30000);
        if (width > maximumDimension || depth > maximumDimension) {
            throw new IOException("As dimensoes padrao de arena nao podem exceder "
                + "arena-bounds.maximum-dimension.");
        }

        return new BedWarsSettings(config.getBoolean("general.debug"),
            respawnDelay, spawnOpeningDelay, width, depth, upHeight, downDepth,
            maximumDimension);
    }

    private static void requireBoolean(FileConfiguration config, String path) throws IOException {
        if (!config.isBoolean(path)) {
            throw new IOException(path + " ausente ou invalido; esperado: true ou false.");
        }
    }

    private static int readInteger(FileConfiguration config, String path, int defaultValue,
                                   int minimum, int maximum) throws IOException {
        if (!config.contains(path)) {
            return defaultValue;
        }
        if (!config.isInt(path) || config.getInt(path) < minimum || config.getInt(path) > maximum) {
            throw new IOException(path + " ausente ou invalido; esperado inteiro entre "
                + minimum + " e " + maximum + ".");
        }
        return config.getInt(path);
    }

    public boolean isDebugEnabled() {
        return debug;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }
    public int getSpawnOpeningSeconds() { return spawnOpeningSeconds; }

    public int getAutomaticBoundsWidth() { return automaticBoundsWidth; }
    public int getAutomaticBoundsDepth() { return automaticBoundsDepth; }
    public int getAutomaticBoundsUpHeight() { return automaticBoundsUpHeight; }
    public int getAutomaticBoundsDownDepth() { return automaticBoundsDownDepth; }
    public int getAutomaticBoundsMaximumDimension() { return automaticBoundsMaximumDimension; }
}
