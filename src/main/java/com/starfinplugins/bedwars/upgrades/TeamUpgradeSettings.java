package com.starfinplugins.bedwars.upgrades;

import com.starfinplugins.bedwars.domain.economy.TeamTrapDefinition;
import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private final Map<TeamTrapType, Integer> secondTrapCosts;
    private final Map<TeamTrapType, Integer> secondTrapDurations;
    private final int maximumQueuedTraps;

    private TeamUpgradeSettings(Map<TeamUpgradeType, List<Integer>> costs,
                                Map<TeamTrapType, TeamTrapDefinition> traps,
                                Map<TeamTrapType, Integer> secondTrapCosts,
                                Map<TeamTrapType, Integer> secondTrapDurations,
                                int maximumQueuedTraps) {
        this.costs = costs;
        this.traps = traps;
        this.secondTrapCosts = secondTrapCosts;
        this.secondTrapDurations = secondTrapDurations;
        this.maximumQueuedTraps = maximumQueuedTraps;
    }

    public static TeamUpgradeSettings load(Plugin plugin) throws IOException {
        File upgradesFile = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!upgradesFile.exists()) plugin.saveResource("upgrades.yml", false);
        File trapsFile = new File(plugin.getDataFolder(), "traps.yml");
        if (!trapsFile.exists()) plugin.saveResource("traps.yml", false);
        YamlConfiguration upgradesYaml = loadYaml(upgradesFile, "upgrades.yml");
        YamlConfiguration trapsYaml = loadYaml(trapsFile, "traps.yml");
        migrateReferenceDefaults(upgradesFile, upgradesYaml, trapsFile, trapsYaml);
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
        Map<TeamTrapType, Integer> secondCosts = new EnumMap<TeamTrapType, Integer>(TeamTrapType.class);
        Map<TeamTrapType, Integer> secondDurations = new EnumMap<TeamTrapType, Integer>(TeamTrapType.class);
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
                if (trap.isConfigurationSection("level-2")) {
                    ConfigurationSection second = trap.getConfigurationSection("level-2");
                    int cost = second.getInt("cost", -1);
                    int duration = second.getInt("duration-ticks", -1);
                    if (cost < 1 || cost > 4096 || duration < 1 || duration > 72000) {
                        throw new IllegalArgumentException("nivel 2 invalido");
                    }
                    secondCosts.put(type, cost);
                    secondDurations.put(type, duration);
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Configuracao invalida para armadilha " + type + ".", error);
            }
        }
        return new TeamUpgradeSettings(Collections.unmodifiableMap(costs),
            Collections.unmodifiableMap(traps), Collections.unmodifiableMap(secondCosts),
            Collections.unmodifiableMap(secondDurations), maximumTraps);
    }

    public List<Integer> getCosts(TeamUpgradeType type) {
        List<Integer> values = costs.get(type);
        return values == null ? Collections.<Integer>emptyList() : values;
    }
    public TeamTrapDefinition getTrap(TeamTrapType type) { return traps.get(type); }
    public int getTrapMaxLevel(TeamTrapType type) { return secondTrapCosts.containsKey(type) ? 2 : 1; }
    public int getTrapCost(TeamTrapType type, int level) {
        return level == 2 && secondTrapCosts.containsKey(type)
            ? secondTrapCosts.get(type) : traps.get(type).getCost();
    }
    public int getTrapDurationTicks(TeamTrapType type, int level) {
        return level == 2 && secondTrapDurations.containsKey(type)
            ? secondTrapDurations.get(type) : traps.get(type).getDurationTicks();
    }
    public int getMaximumQueuedTraps() { return maximumQueuedTraps; }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static void migrateReferenceDefaults(File upgradesFile, YamlConfiguration upgrades,
                                                  File trapsFile, YamlConfiguration traps)
            throws IOException {
        boolean upgradeChanged = false;
        if (upgrades.getIntegerList("upgrades.sharpness").equals(
                Collections.singletonList(4))) {
            upgrades.set("upgrades.sharpness", java.util.Arrays.asList(4, 16));
            upgradeChanged = true;
        }
        if (!upgrades.contains("upgrades.haste")) {
            upgrades.set("upgrades.haste", java.util.Arrays.asList(2, 4));
            upgradeChanged = true;
        }
        if (upgrades.getIntegerList("upgrades.heal-pool").equals(
                Collections.singletonList(3))) {
            upgrades.set("upgrades.heal-pool", Collections.singletonList(1));
            upgradeChanged = true;
        }
        if (upgradeChanged) saveMigration(upgradesFile, upgrades);
        boolean trapChanged = false;
        String alarm = "traps.alarm";
        if ("NONE".equalsIgnoreCase(traps.getString(alarm + ".effect"))
                && traps.getInt(alarm + ".duration-ticks") == 0) {
            traps.set(alarm + ".effect", "BLINDNESS");
            traps.set(alarm + ".duration-ticks", 160);
            trapChanged = true;
        }
        String fatigue = "traps.mining-fatigue";
        if (!traps.contains(fatigue + ".level-2")) {
            if (traps.getInt(fatigue + ".cost") == 2
                    && traps.getInt(fatigue + ".duration-ticks") == 200) {
                traps.set(fatigue + ".cost", 1);
                traps.set(fatigue + ".duration-ticks", 140);
            }
            traps.set(fatigue + ".level-2.cost", 2);
            traps.set(fatigue + ".level-2.duration-ticks", 200);
            trapChanged = true;
        }
        if (trapChanged) saveMigration(trapsFile, traps);
    }

    private static void saveMigration(File file, YamlConfiguration yaml) throws IOException {
        byte[] original = Files.readAllBytes(file.toPath());
        File backup = new File(file.getParentFile(), file.getName() + ".pre-reference-shop.bak");
        if (!backup.exists()) Files.write(backup.toPath(), original);
        try { yaml.save(file); }
        catch (Exception error) {
            Files.write(file.toPath(), original);
            throw new IOException("Nao foi possivel migrar " + file.getName(), error);
        }
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
