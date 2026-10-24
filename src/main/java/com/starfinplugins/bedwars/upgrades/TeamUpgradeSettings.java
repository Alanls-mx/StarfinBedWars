package com.starfinplugins.bedwars.upgrades;

import com.starfinplugins.bedwars.domain.economy.TeamTrapDefinition;
import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

public final class TeamUpgradeSettings {
    private final Map<TeamUpgradeType, List<Integer>> costs;
    private final Map<TeamTrapType, TeamTrapDefinition> traps;
    private final int maximumQueuedTraps;

    private TeamUpgradeSettings(Map<TeamUpgradeType, List<Integer>> costs,
                                Map<TeamTrapType, TeamTrapDefinition> traps, int maximumQueuedTraps) {
        this.costs = costs;
        this.traps = traps;
        this.maximumQueuedTraps = maximumQueuedTraps;
    }

    public static TeamUpgradeSettings load(Plugin plugin) throws IOException {
        File upgradesFile = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!upgradesFile.exists()) plugin.saveResource("upgrades.yml", false);
        File trapsFile = new File(plugin.getDataFolder(), "traps.yml");
        if (!trapsFile.exists()) plugin.saveResource("traps.yml", false);
        YamlConfiguration upgradesYaml = loadYaml(upgradesFile, "upgrades.yml");
        YamlConfiguration trapsYaml = loadYaml(trapsFile, "traps.yml");
        ConfigurationSection upgradeSection = upgradesYaml.getConfigurationSection("upgrades");
        ConfigurationSection trapSection = trapsYaml.getConfigurationSection("traps");
        int maximumTraps = upgradesYaml.getInt("maximum-queued-traps", -1);
        if (upgradeSection == null || trapSection == null || maximumTraps < 1 || maximumTraps > 10) {
            throw new IOException("Configuracao de upgrades/armadilhas incompleta.");
        }
        Map<TeamUpgradeType, List<Integer>> costs = new EnumMap<TeamUpgradeType, List<Integer>>(
            TeamUpgradeType.class);
        for (TeamUpgradeType type : TeamUpgradeType.values()) {
            List<Integer> values = upgradeSection.getIntegerList(key(type));
            if (values.isEmpty() || values.size() > 10) {
                throw new IOException("Custos ausentes/invalido para melhoria " + type + ".");
            }
            for (Integer value : values) {
                if (value == null || value < 1 || value > 4096) {
                    throw new IOException("Custo invalido para melhoria " + type + ".");
                }
            }
            costs.put(type, Collections.unmodifiableList(new ArrayList<Integer>(values)));
        }
        Map<TeamTrapType, TeamTrapDefinition> traps = new EnumMap<TeamTrapType, TeamTrapDefinition>(
            TeamTrapType.class);
        for (TeamTrapType type : TeamTrapType.values()) {
            ConfigurationSection trap = trapSection.getConfigurationSection(key(type));
            if (trap == null || !trap.isInt("cost") || !trap.isString("effect")
                    || !trap.isInt("duration-ticks") || !trap.isInt("amplifier")) {
                throw new IOException("Configuracao ausente para armadilha " + type + ".");
            }
            String effectName = trap.getString("effect");
            PotionEffectType effect = "NONE".equalsIgnoreCase(effectName)
                ? null : PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
            if (!"NONE".equalsIgnoreCase(effectName) && effect == null) {
                throw new IOException("Efeito Bukkit desconhecido para armadilha " + type + ".");
            }
            try {
                traps.put(type, new TeamTrapDefinition(type, trap.getInt("cost"), effect,
                    trap.getInt("duration-ticks"), trap.getInt("amplifier")));
            } catch (IllegalArgumentException error) {
                throw new IOException("Configuracao invalida para armadilha " + type + ".", error);
            }
        }
        return new TeamUpgradeSettings(Collections.unmodifiableMap(costs),
            Collections.unmodifiableMap(traps), maximumTraps);
    }

    public List<Integer> getCosts(TeamUpgradeType type) {
        List<Integer> values = costs.get(type);
        return values == null ? Collections.<Integer>emptyList() : values;
    }
    public TeamTrapDefinition getTrap(TeamTrapType type) { return traps.get(type); }
    public int getMaximumQueuedTraps() { return maximumQueuedTraps; }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static YamlConfiguration loadYaml(File file, String name) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception error) {
            throw new IOException(name + " invalido: " + error.getMessage(), error);
        }
        return yaml;
    }
}
