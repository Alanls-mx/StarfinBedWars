package com.starfinplugins.bedwars.economy.generator;

import com.starfinplugins.bedwars.domain.economy.GeneratorLevel;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class GeneratorSettings {
    private final Map<ResourceType, List<GeneratorLevel>> levels =
        new EnumMap<ResourceType, List<GeneratorLevel>>(ResourceType.class);
    private final Map<ResourceType, Boolean> enabled = new EnumMap<ResourceType, Boolean>(ResourceType.class);

    public static GeneratorSettings load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "generators.yml");
        if (!file.exists()) plugin.saveResource("generators.yml", false);
        if (GeneratorConfigMigrator.migrate(file)) {
            plugin.getLogger().info("Intervalos iniciais dos geradores de ilha atualizados; backup preservado.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception error) {
            throw new IOException("generators.yml invalido: " + error.getMessage(), error);
        }
        GeneratorSettings result = new GeneratorSettings();
        ConfigurationSection types = yaml.getConfigurationSection("resources");
        if (types == null) throw new IOException("generators.yml: secao resources ausente.");
        for (ResourceType resource : ResourceType.values()) {
            ConfigurationSection config = types.getConfigurationSection(resource.name().toLowerCase(java.util.Locale.ROOT));
            if (config == null || !config.isBoolean("enabled")) {
                plugin.getLogger().warning("Gerador " + resource + " desativado: configuracao ausente/invalida.");
                result.enabled.put(resource, false);
                continue;
            }
            result.enabled.put(resource, config.getBoolean("enabled"));
            List<GeneratorLevel> parsed = new ArrayList<GeneratorLevel>();
            ConfigurationSection levels = config.getConfigurationSection("levels");
            if (levels == null) {
                if (config.isInt("interval-ticks") && config.isInt("amount") && config.isInt("max-dropped")) {
                    try {
                        parsed.add(new GeneratorLevel(config.getInt("interval-ticks"),
                            config.getInt("amount"), config.getInt("max-dropped")));
                    } catch (IllegalArgumentException error) {
                        plugin.getLogger().warning("Gerador " + resource + " desativado: " + error.getMessage());
                    }
                }
            } else {
                TreeSet<Integer> orderedLevels = new TreeSet<Integer>();
                boolean validKeys = true;
                for (String levelKey : levels.getKeys(false)) {
                    try {
                        int number = Integer.parseInt(levelKey);
                        if (number < 1 || !orderedLevels.add(number)) validKeys = false;
                    } catch (NumberFormatException error) {
                        validKeys = false;
                    }
                }
                if (!validKeys || orderedLevels.size() > 10) {
                    plugin.getLogger().warning("Niveis do gerador " + resource
                        + " devem ser numericos, unicos e entre 1 e 10.");
                    orderedLevels.clear();
                }
                int expected = 1;
                for (Integer number : orderedLevels) {
                    if (number != expected++) {
                        validKeys = false;
                        break;
                    }
                }
                if (!validKeys) orderedLevels.clear();
                for (Integer levelNumber : orderedLevels) {
                    String levelKey = Integer.toString(levelNumber);
                    ConfigurationSection level = levels.getConfigurationSection(levelKey);
                    if (level == null || !level.isInt("interval-ticks") || !level.isInt("amount")
                            || !level.isInt("max-dropped")) {
                        plugin.getLogger().warning("Nivel " + levelKey + " invalido para gerador " + resource + ".");
                        parsed.clear();
                        break;
                    }
                    try {
                        parsed.add(new GeneratorLevel(level.getInt("interval-ticks"), level.getInt("amount"),
                            level.getInt("max-dropped")));
                    } catch (IllegalArgumentException error) {
                        plugin.getLogger().warning("Gerador " + resource + " desativado: " + error.getMessage());
                        parsed.clear();
                        break;
                    }
                }
            }
            if (result.enabled.get(resource) && parsed.isEmpty()) {
                plugin.getLogger().warning("Gerador " + resource + " desativado por nao ter niveis validos.");
                result.enabled.put(resource, false);
            }
            result.levels.put(resource, Collections.unmodifiableList(parsed));
        }
        return result;
    }

    public boolean isEnabled(ResourceType resource) {
        return Boolean.TRUE.equals(enabled.get(resource));
    }

    public List<GeneratorLevel> getLevels(ResourceType resource) {
        List<GeneratorLevel> result = levels.get(resource);
        return result == null ? Collections.<GeneratorLevel>emptyList() : result;
    }
}
