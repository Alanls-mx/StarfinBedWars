package com.starfinplugins.bedwars.stats;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Only title unlocks are executable in phase 2; unknown reward types fail closed. */
public final class RewardsCatalog {
    private final List<TitleReward> titles;
    private final Map<String, TitleReward> byId;

    private RewardsCatalog(List<TitleReward> titles) {
        this.titles = Collections.unmodifiableList(new ArrayList<TitleReward>(titles));
        Map<String, TitleReward> lookup = new LinkedHashMap<String, TitleReward>();
        for (TitleReward title : titles) {
            if (lookup.put(title.id, title) != null)
                throw new IllegalArgumentException("ID de recompensa duplicado: " + title.id);
        }
        byId = Collections.unmodifiableMap(lookup);
    }

    public static RewardsCatalog empty() { return new RewardsCatalog(Collections.emptyList()); }

    public static RewardsCatalog load(JavaPlugin plugin, XpProgression progression) {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) plugin.saveResource("rewards.yml", false);
        return parse(YamlConfiguration.loadConfiguration(file), progression);
    }

    public static RewardsCatalog parse(YamlConfiguration yaml, XpProgression progression) {
        ConfigurationSection levels = yaml.getConfigurationSection("levels");
        if (levels == null) throw new IllegalArgumentException("rewards.yml: levels ausente");
        List<TitleReward> entries = new ArrayList<TitleReward>();
        for (String levelKey : levels.getKeys(false)) {
            int level;
            try { level = Integer.parseInt(levelKey); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Nivel invalido: " + levelKey); }
            if (level < 2 || level > 10000) throw new IllegalArgumentException("Nivel invalido: " + level);
            ConfigurationSection section = levels.getConfigurationSection(levelKey);
            if (section == null) throw new IllegalArgumentException("Recompensa de nivel invalida");
            List<Map<?, ?>> rewards = section.getMapList("rewards");
            if (rewards.isEmpty()) throw new IllegalArgumentException("Nivel sem recompensas: " + level);
            for (Map<?, ?> reward : rewards) {
                if (!"TITLE_UNLOCK".equals(String.valueOf(reward.get("type"))))
                    throw new IllegalArgumentException("Tipo de recompensa nao suportado no nivel " + level);
                Object rawId = reward.get("id");
                String id = rawId == null ? "" : rawId.toString();
                if (!id.matches("[a-z0-9_-]{1,32}"))
                    throw new IllegalArgumentException("ID de titulo invalido: " + id);
                Map<String, String> names = new LinkedHashMap<String, String>();
                Object configured = reward.get("titles");
                if (configured instanceof Map) {
                    Map<?, ?> translations = (Map<?, ?>) configured;
                    for (String language : new String[] {"pt_BR", "en_US", "es_ES"})
                        if (translations.get(language) != null)
                            names.put(language, cleanTitle(translations.get(language).toString()));
                }
                String defaultTitle = section.getString("title", id);
                names.putIfAbsent("pt_BR", cleanTitle(defaultTitle));
                names.putIfAbsent("en_US", names.get("pt_BR"));
                names.putIfAbsent("es_ES", names.get("pt_BR"));
                entries.add(new TitleReward(id, level, progression.totalXpForLevel(level), names));
            }
        }
        entries.sort((left, right) -> Integer.compare(left.level, right.level));
        return new RewardsCatalog(entries);
    }

    private static String cleanTitle(String text) {
        String result = text == null ? "" : text.trim();
        if (result.isEmpty() || result.length() > 24 || result.contains("\n") || result.contains("\r"))
            throw new IllegalArgumentException("Nome de titulo invalido");
        return result;
    }

    public List<TitleReward> titles() { return titles; }
    public TitleReward title(String id) { return byId.get(id); }

    public static final class TitleReward {
        public final String id;
        public final int level;
        public final long requiredTotalXp;
        private final Map<String, String> names;

        private TitleReward(String id, int level, long requiredTotalXp, Map<String, String> names) {
            this.id = id;
            this.level = level;
            this.requiredTotalXp = requiredTotalXp;
            this.names = Collections.unmodifiableMap(new LinkedHashMap<String, String>(names));
        }

        public String name(String language) {
            return names.getOrDefault(PlayerPreferences.languageOrDefault(language), names.get("pt_BR"));
        }
    }
}
