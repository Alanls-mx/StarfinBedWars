package com.starfinplugins.bedwars.world;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Difficulty;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Validated environment rules. Arena rules override their source-world rules. */
public final class AlwaysSettings {
    private final Rule defaults;
    private final ConfigurationSection arenaDefaults;
    private final Map<String, ConfigurationSection> worlds;
    private final Map<String, ConfigurationSection> arenas;

    private AlwaysSettings(Rule defaults, ConfigurationSection arenaDefaults,
                           Map<String, ConfigurationSection> worlds,
                           Map<String, ConfigurationSection> arenas) {
        this.defaults = defaults;
        this.arenaDefaults = arenaDefaults;
        this.worlds = worlds;
        this.arenas = arenas;
    }

    public static AlwaysSettings load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "always.yml");
        if (!file.exists()) plugin.saveResource("always.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return parse(yaml);
        } catch (Exception invalid) {
            throw new IOException("always.yml invalido: " + invalid.getMessage(), invalid);
        }
    }

    public static AlwaysSettings parse(YamlConfiguration yaml) {
        if (yaml == null) throw new IllegalArgumentException("always.yml ausente");
        for (String key : yaml.getKeys(false)) {
            if (!"defaults".equals(key) && !"arena-defaults".equals(key)
                    && !"worlds".equals(key) && !"arenas".equals(key))
                throw new IllegalArgumentException("always.yml: secao desconhecida: " + key);
        }
        Rule base = Rule.empty().overlay(section(yaml, "defaults"), "defaults");
        ConfigurationSection arenaBase = section(yaml, "arena-defaults");
        base.overlay(arenaBase, "arena-defaults");
        Map<String, ConfigurationSection> worldRules = entries(section(yaml, "worlds"), "worlds");
        Map<String, ConfigurationSection> arenaRules = entries(section(yaml, "arenas"), "arenas");
        for (Map.Entry<String, ConfigurationSection> entry : worldRules.entrySet())
            base.overlay(entry.getValue(), "worlds." + entry.getKey());
        for (Map.Entry<String, ConfigurationSection> entry : arenaRules.entrySet())
            base.overlay(arenaBase, "arena-defaults").overlay(entry.getValue(), "arenas." + entry.getKey());
        return new AlwaysSettings(base, arenaBase, worldRules, arenaRules);
    }

    public Rule forWorld(String name) {
        return defaults.overlay(worlds.get(key(name)), "worlds." + name);
    }

    public Rule forArena(String arenaId, String sourceWorld) {
        return forWorld(sourceWorld).overlay(arenaDefaults, "arena-defaults")
            .overlay(arenas.get(key(arenaId)), "arenas." + arenaId);
    }

    private static ConfigurationSection section(ConfigurationSection parent, String name) {
        if (parent == null || !parent.contains(name)) return null;
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) throw new IllegalArgumentException(name + " deve ser uma secao YAML");
        return section;
    }

    private static Map<String, ConfigurationSection> entries(ConfigurationSection parent, String path) {
        Map<String, ConfigurationSection> result = new HashMap<String, ConfigurationSection>();
        if (parent == null) return result;
        for (String name : parent.getKeys(false)) {
            String key = key(name);
            if (key.isEmpty() || result.containsKey(key))
                throw new IllegalArgumentException(path + ": nome vazio ou duplicado: " + name);
            result.put(key, section(parent, name));
        }
        return result;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public enum Weather { CLEAR, RAIN, THUNDER }
    public enum TimeMode { DAY, NIGHT, FIXED }

    public static final class TimePolicy {
        public final TimeMode mode;
        public final int tick;
        private TimePolicy(TimeMode mode, int tick) { this.mode = mode; this.tick = tick; }
        @Override public String toString() { return mode == TimeMode.FIXED ? Integer.toString(tick) : mode.name(); }
    }

    public static final class Rule {
        public final boolean enabled;
        public final TimePolicy time;
        public final Difficulty difficulty;
        public final Weather weather;
        public final Boolean mobSpawning, mobGriefing, allowMonsters, allowAnimals;

        private Rule(boolean enabled, TimePolicy time, Difficulty difficulty, Weather weather,
                     Boolean mobSpawning, Boolean mobGriefing, Boolean allowMonsters, Boolean allowAnimals) {
            this.enabled = enabled;
            this.time = time;
            this.difficulty = difficulty;
            this.weather = weather;
            this.mobSpawning = mobSpawning;
            this.mobGriefing = mobGriefing;
            this.allowMonsters = allowMonsters;
            this.allowAnimals = allowAnimals;
        }

        private static Rule empty() { return new Rule(false, null, null, null, null, null, null, null); }

        private Rule overlay(ConfigurationSection section, String path) {
            if (section == null) return this;
            for (String key : section.getKeys(false)) {
                if (!"enabled".equals(key) && !"time".equals(key) && !"difficulty".equals(key)
                        && !"weather".equals(key) && !"mob-spawning".equals(key)
                        && !"mob-griefing".equals(key) && !"allow-monsters".equals(key)
                        && !"allow-animals".equals(key))
                    throw new IllegalArgumentException(path + ": campo desconhecido: " + key);
            }
            boolean nextEnabled = section.contains("enabled")
                ? requiredBoolean(section, "enabled", path) : enabled;
            TimePolicy nextTime = section.contains("time") ? parseTime(section.get("time"), path) : time;
            Difficulty nextDifficulty = section.contains("difficulty")
                ? parseDifficulty(section.get("difficulty"), path) : difficulty;
            Weather nextWeather = section.contains("weather")
                ? parseWeather(section.get("weather"), path) : weather;
            return new Rule(nextEnabled, nextTime, nextDifficulty, nextWeather,
                optionalBoolean(section, "mob-spawning", path, mobSpawning),
                optionalBoolean(section, "mob-griefing", path, mobGriefing),
                optionalBoolean(section, "allow-monsters", path, allowMonsters),
                optionalBoolean(section, "allow-animals", path, allowAnimals));
        }

        public String summary() {
            return "enabled=" + enabled + " time=" + (time == null ? "unchanged" : time)
                + " difficulty=" + (difficulty == null ? "unchanged" : difficulty)
                + " weather=" + (weather == null ? "unchanged" : weather)
                + " mob-spawning=" + value(mobSpawning) + " mob-griefing=" + value(mobGriefing)
                + " monsters=" + value(allowMonsters) + " animals=" + value(allowAnimals);
        }
        private static String value(Boolean value) { return value == null ? "unchanged" : value.toString(); }
    }

    private static boolean requiredBoolean(ConfigurationSection section, String name, String path) {
        if (!section.isBoolean(name)) throw new IllegalArgumentException(path + "." + name + " deve ser true/false");
        return section.getBoolean(name);
    }

    private static Boolean optionalBoolean(ConfigurationSection section, String name, String path, Boolean previous) {
        if (!section.contains(name)) return previous;
        Object value = section.get(name);
        if (off(value)) return null;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(path + "." + name + " deve ser true/false/off");
        return (Boolean) value;
    }

    private static TimePolicy parseTime(Object value, String path) {
        if (off(value) || Boolean.FALSE.equals(value)) return null;
        if (value instanceof Number) {
            int tick = ((Number) value).intValue();
            if (tick < 0 || tick > 23999 || ((Number) value).doubleValue() != tick)
                throw new IllegalArgumentException(path + ".time deve estar entre 0 e 23999");
            return new TimePolicy(TimeMode.FIXED, tick);
        }
        String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if ("DAY".equals(text)) return new TimePolicy(TimeMode.DAY, 1000);
        if ("NIGHT".equals(text)) return new TimePolicy(TimeMode.NIGHT, 13000);
        throw new IllegalArgumentException(path + ".time deve ser DAY, NIGHT, 0..23999 ou off");
    }

    private static Difficulty parseDifficulty(Object value, String path) {
        if (off(value) || Boolean.FALSE.equals(value)) return null;
        String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        switch (text) {
            case "0": case "PEACEFUL": return Difficulty.PEACEFUL;
            case "1": case "EASY": return Difficulty.EASY;
            case "2": case "NORMAL": return Difficulty.NORMAL;
            case "3": case "HARD": return Difficulty.HARD;
            default: throw new IllegalArgumentException(path + ".difficulty deve ser 0..3, nome ou off");
        }
    }

    private static Weather parseWeather(Object value, String path) {
        if (off(value) || Boolean.FALSE.equals(value)) return null;
        try { return Weather.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(path + ".weather deve ser CLEAR, RAIN, THUNDER ou off");
        }
    }

    private static boolean off(Object value) {
        return value == null || "OFF".equalsIgnoreCase(String.valueOf(value).trim())
            || "UNCHANGED".equalsIgnoreCase(String.valueOf(value).trim());
    }
}
