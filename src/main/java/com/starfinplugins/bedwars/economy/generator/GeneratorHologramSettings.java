package com.starfinplugins.bedwars.economy.generator;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Validated visual settings. Economy values remain in generators.yml. */
public final class GeneratorHologramSettings {
    public final boolean enabled, text, level, resource, countdown, block, rotate, floatBlock;
    public final boolean orbit, oscillate, pauseWithoutViewers;
    public final int textInterval, orbitInterval, iconCount, maxIcons, floatPeriod, activationDistance;
    public final double blockSpeed, floatAmplitude, orbitRadius, orbitHeight, orbitSpeed, orbitAmplitude;
    public final Material diamondBlock, diamondIcon, emeraldBlock, emeraldIcon;

    public GeneratorHologramSettings(FileConfiguration config) {
        String p = "generator-holograms.";
        enabled = config.getBoolean(p + "enabled", true);
        text = config.getBoolean(p + "text.enabled", true);
        level = config.getBoolean(p + "text.show-level", true);
        resource = config.getBoolean(p + "text.show-resource", true);
        countdown = config.getBoolean(p + "text.show-countdown", true);
        block = config.getBoolean(p + "central-block.enabled", true);
        rotate = config.getBoolean(p + "central-block.rotate", true);
        floatBlock = config.getBoolean(p + "central-block.floating.enabled", true);
        orbit = config.getBoolean(p + "orbit.enabled", true);
        oscillate = config.getBoolean(p + "orbit.vertical-oscillation.enabled", false);
        pauseWithoutViewers = config.getBoolean(p + "performance.pause-when-no-viewers", true);
        textInterval = integer(config, p + "text.update-interval-ticks", 20, 1, 1200);
        orbitInterval = integer(config, p + "orbit.update-interval-ticks", 2, 1, 100);
        maxIcons = integer(config, p + "performance.max-icons-per-generator", 8, 1, 8);
        iconCount = integer(config, p + "orbit.icon-count", 4, 0, maxIcons);
        if (orbit && iconCount == 0) throw new IllegalArgumentException(p + "orbit.icon-count deve ser >= 1 quando habilitado.");
        floatPeriod = integer(config, p + "central-block.floating.period-ticks", 60, 1, 1200);
        activationDistance = integer(config, p + "performance.activation-distance", 48, 1, 256);
        blockSpeed = number(config, p + "central-block.rotation-speed", 4.0D, 0.0D, 45.0D);
        floatAmplitude = number(config, p + "central-block.floating.amplitude", 0.12D, 0.0D, 0.5D);
        orbitRadius = number(config, p + "orbit.radius", 0.75D, 0.0D, 2.0D);
        orbitHeight = number(config, p + "orbit.height-offset", 0.15D, -0.5D, 0.5D);
        orbitSpeed = number(config, p + "orbit.rotation-speed", 6.0D, 0.0D, 45.0D);
        orbitAmplitude = number(config, p + "orbit.vertical-oscillation.amplitude", 0.05D, 0.0D, 0.25D);
        diamondBlock = material(config, p + "resources.diamond.central-material", "DIAMOND_BLOCK", true);
        diamondIcon = material(config, p + "resources.diamond.orbit-material", "DIAMOND", false);
        emeraldBlock = material(config, p + "resources.emerald.central-material", "EMERALD_BLOCK", true);
        emeraldIcon = material(config, p + "resources.emerald.orbit-material", "EMERALD", false);
    }

    private static int integer(FileConfiguration config, String key, int fallback, int min, int max) {
        if (config.contains(key) && !config.isInt(key)) throw new IllegalArgumentException(key + " deve ser inteiro.");
        int value = config.getInt(key, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(key + " fora de " + min + ".." + max + ".");
        return value;
    }

    private static double number(FileConfiguration config, String key, double fallback, double min, double max) {
        if (config.contains(key) && !config.isDouble(key) && !config.isInt(key))
            throw new IllegalArgumentException(key + " deve ser numerico.");
        double value = config.getDouble(key, fallback);
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " fora de " + min + ".." + max + ".");
        return value;
    }

    private static Material material(FileConfiguration config, String key, String fallback, boolean solid) {
        String name = config.getString(key, fallback);
        Material value = name == null ? null : Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (value == null || value == Material.AIR || (solid && !value.isBlock()))
            throw new IllegalArgumentException(key + " possui material 1.8 invalido: " + name);
        return value;
    }
}
