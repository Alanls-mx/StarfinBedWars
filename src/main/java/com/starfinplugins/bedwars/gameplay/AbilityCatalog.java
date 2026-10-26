package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable definitions for the eighteen evolved abilities. Gameplay never reads YAML on a combat event. */
public final class AbilityCatalog {
    public static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(
        "respawn_boost", "knockback_resist", "fall_resistance", "fire_resistance",
        "projectile_resistance", "miner", "momentum", "sentinel", "collector", "archer",
        "builder", "merchant", "aegis", "vampire", "frenzy", "bodyguard", "forge_master",
        "last_stand"));
    public final Map<String, Definition> abilities;
    public final int minimumRespawnTicks;
    public final double maximumDamageReduction;

    private AbilityCatalog(YamlConfiguration yaml, PurchaseSettings.Scope defaultScope) {
        ConfigurationSection root = required(yaml, "abilities");
        Map<String, Definition> result = new LinkedHashMap<String, Definition>();
        for (String id : IDS) result.put(id, new Definition(id, required(root, id), defaultScope));
        if (root.getKeys(false).size() != IDS.size())
            throw new IllegalArgumentException("abilities.yml deve conter somente os 18 IDs documentados");
        minimumRespawnTicks = yaml.getInt("limits.minimum-respawn-ticks", 20);
        maximumDamageReduction = yaml.getDouble("limits.maximum-damage-reduction", 0.65);
        if (minimumRespawnTicks < 1 || minimumRespawnTicks > 200
                || maximumDamageReduction < 0.1 || maximumDamageReduction > 0.9)
            throw new IllegalArgumentException("Limites globais de abilities.yml invalidos");
        abilities = Collections.unmodifiableMap(result);
    }

    public static AbilityCatalog load(JavaPlugin plugin) {
        return load(plugin, PurchaseSettings.Scope.PER_MODE);
    }
    public static AbilityCatalog load(JavaPlugin plugin, PurchaseSettings.Scope defaultScope) {
        File file = new File(plugin.getDataFolder(), "abilities.yml");
        if (!file.exists()) plugin.saveResource("abilities.yml", false);
        return parse(YamlConfiguration.loadConfiguration(file), defaultScope);
    }
    public static AbilityCatalog parse(YamlConfiguration yaml) {
        return parse(yaml, PurchaseSettings.Scope.PER_MODE);
    }
    public static AbilityCatalog parse(YamlConfiguration yaml, PurchaseSettings.Scope defaultScope) {
        return new AbilityCatalog(yaml, defaultScope);
    }

    public enum Category { COMMON, SPECIAL }

    public static final class Definition {
        public final String id, name, description, permission;
        public final Material icon;
        public final Category category;
        public final PurchaseSettings.Scope scope;
        public final boolean enabled;
        public final int maxLevel, minimumPlayerLevel, unlockPrice, cooldownSeconds, activationLimit;
        public final Set<String> modes;
        private final Map<Integer, Integer> prices;
        private final Map<Integer, Map<String, Double>> levels;
        private final Map<String, Double> parameters;

        private Definition(String id, ConfigurationSection section,
                           PurchaseSettings.Scope defaultScope) {
            this.id = id;
            name = text(section, "name", 64);
            description = text(section, "description", 160);
            permission = text(section, "permission", 80);
            if (!permission.matches("[a-zA-Z0-9_.-]{1,80}"))
                throw new IllegalArgumentException(id + ": permissao obrigatoria invalida");
            try { icon = Material.valueOf(text(section, "icon", 40).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(id + ": icone invalido"); }
            if (icon == Material.AIR) throw new IllegalArgumentException(id + ": icone AIR");
            try { category = Category.valueOf(text(section, "category", 12).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(id + ": categoria invalida"); }
            enabled = section.getBoolean("enabled", true);
            ConfigurationSection purchase = section.getConfigurationSection("purchase");
            scope = purchase == null ? defaultScope
                : PurchaseSettings.scope(purchase.getString("scope", defaultScope.name()));
            maxLevel = section.getInt("max-level", 5);
            minimumPlayerLevel = section.getInt("minimum-player-level", 1);
            cooldownSeconds = section.getInt("cooldown-seconds", 0);
            activationLimit = section.getInt("activation-limit", 0);
            if (maxLevel < 1 || maxLevel > 5 || minimumPlayerLevel < 1 || minimumPlayerLevel > 10000
                    || cooldownSeconds < 0 || cooldownSeconds > 3600
                    || activationLimit < 0 || activationLimit > 10000)
                throw new IllegalArgumentException(id + ": nivel ou limite invalido");
            Set<String> modesSet = new LinkedHashSet<String>();
            List<String> configuredModes = section.contains("allowed-modes")
                ? section.getStringList("allowed-modes") : section.getStringList("modes");
            for (String mode : configuredModes) {
                String normalized = MatchMode.persisted(mode);
                if (normalized == null) throw new IllegalArgumentException(id + ": modo invalido");
                modesSet.add(normalized);
            }
            if (modesSet.isEmpty()) throw new IllegalArgumentException(id + ": modalidades ausentes");
            modes = Collections.unmodifiableSet(modesSet);
            ConfigurationSection priceSection = required(section, "prices");
            unlockPrice = price(priceSection, "unlock");
            Map<Integer, Integer> costs = new LinkedHashMap<Integer, Integer>();
            for (int level = 2; level <= 5; level++) costs.put(level, price(priceSection, "level-" + level));
            prices = Collections.unmodifiableMap(costs);
            ConfigurationSection levelSection = required(section, "levels");
            Map<Integer, Map<String, Double>> configuredLevels = new LinkedHashMap<Integer, Map<String, Double>>();
            for (int level = 1; level <= 5; level++) {
                ConfigurationSection values = required(levelSection, Integer.toString(level));
                configuredLevels.put(level, numbers(id, values, allowedLevelKeys(id)));
            }
            if (levelSection.getKeys(false).size() != 5)
                throw new IllegalArgumentException(id + ": exatamente cinco niveis necessarios");
            levels = Collections.unmodifiableMap(configuredLevels);
            ConfigurationSection parametersSection = section.getConfigurationSection("parameters");
            parameters = parametersSection == null ? Collections.emptyMap()
                : numbers(id, parametersSection, allowedParameterKeys(id));
        }
        public int priceFor(int nextLevel) { return prices.getOrDefault(nextLevel, -1); }
        public double value(int level, String key) {
            Map<String, Double> values = levels.get(Math.max(1, Math.min(5, level)));
            Double value = values.get(key);
            if (value == null) throw new IllegalArgumentException(id + ": valor ausente " + key);
            return value;
        }
        public double parameter(String key, double fallback) {
            Double value = parameters.get(key);
            return value == null ? fallback : value;
        }
        public Map<String, Double> values(int level) { return levels.get(Math.max(1, Math.min(5, level))); }
    }

    private static ConfigurationSection required(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("Secao ausente: " + path);
        return section;
    }
    private static String text(ConfigurationSection section, String key, int maximum) {
        String value = section.getString(key);
        if (value == null || value.trim().isEmpty() || value.length() > maximum
                || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " invalido");
        return value.trim();
    }
    private static int price(ConfigurationSection section, String key) {
        if (!section.isInt(key) || section.getInt(key) < 0 || section.getInt(key) > 100000000)
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " invalido");
        return section.getInt(key);
    }
    private static Map<String, Double> numbers(String id, ConfigurationSection section, Set<String> allowed) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        if (!section.getKeys(false).equals(allowed))
            throw new IllegalArgumentException(id + ": campos invalidos em " + section.getCurrentPath()
                + "; esperado " + allowed);
        for (String key : allowed) {
            if (!section.isDouble(key) && !section.isInt(key))
                throw new IllegalArgumentException(id + ": numero invalido em " + key);
            double value = section.getDouble(key);
            double maximum = key.contains("percent") || key.contains("chance") ? 50
                : key.equals("blocks-required") ? 128 : key.equals("health-threshold") ? 20
                : key.equals("duration-seconds") ? 30 : key.equals("max-bonus-per-minute") ? 60
                : key.equals("minimum-price") ? 1000 : 20;
            if (!Double.isFinite(value) || value < 0 || value > maximum
                    || (key.equals("blocks-required") && value < 2)
                    || (key.equals("minimum-price") && value < 1))
                throw new IllegalArgumentException(id + ": valor inseguro em " + key);
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }
    private static Set<String> allowedLevelKeys(String id) {
        switch (id) {
            case "respawn_boost": case "fall_resistance": case "projectile_resistance":
            case "bodyguard": case "last_stand": return keys("reduction-percent");
            case "knockback_resist": return keys("knockback-reduction");
            case "fire_resistance": return keys("duration-reduction-percent");
            case "miner": case "momentum": return keys("duration-seconds");
            case "sentinel": return keys("radius");
            case "collector": return keys("extra-radius");
            case "archer": case "forge_master": return keys("chance-percent");
            case "builder": return keys("blocks-required", "duration-seconds");
            case "merchant": return keys("discount-percent");
            case "aegis": return keys("absorption-hearts", "duration-seconds");
            case "vampire": return keys("heal-hearts");
            case "frenzy": return keys("damage-percent", "duration-seconds");
            default: throw new IllegalArgumentException("ID de habilidade desconhecido: " + id);
        }
    }
    private static Set<String> allowedParameterKeys(String id) {
        switch (id) {
            case "miner": return keys("enemy-bed-radius");
            case "merchant": return keys("minimum-price");
            case "bodyguard": return keys("max-distance");
            case "forge_master": return keys("max-bonus-per-minute");
            case "last_stand": return keys("health-threshold");
            default: return Collections.emptySet();
        }
    }
    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }
}
