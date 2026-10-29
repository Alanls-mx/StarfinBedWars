package com.starfinplugins.bedwars.ranked;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable, validated competitive rules. */
public final class RankedSettings {
    public final boolean enabled, allowDraws, history, diagnostics, kits, abilities;
    public final String context;
    public final int initial, minimum, kFactor, placements, maximumResults;
    public final boolean suspensionsEnabled;
    public final int suspensionThreshold, suspensionWindowMinutes, suspensionMinutes;
    public final Set<String> modes, authorizedPlugins;
    public final Set<String> arenaGroups;
    public final List<Division> divisions;
    private final YamlConfiguration messages;

    public RankedSettings(YamlConfiguration yaml) {
        enabled = yaml.getBoolean("enabled", false);
        context = yaml.getString("context", "preseason-1");
        if (context == null || !context.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("ranked.context invalido");
        initial = yaml.getInt("rating.initial", 1000);
        minimum = yaml.getInt("rating.minimum", 0);
        kFactor = yaml.getInt("rating.k-factor", 24);
        placements = yaml.getInt("placement.required-matches", 5);
        maximumResults = yaml.getInt("queries.maximum-results", 50);
        if (minimum < 0 || initial < minimum || initial > 1000000 || kFactor < 1
                || kFactor > 1000 || placements < 0 || placements > 100
                || maximumResults < 1 || maximumResults > 100)
            throw new IllegalArgumentException("Limites de ranked.yml invalidos");
        allowDraws = yaml.getBoolean("draws.allow-legitimate", true);
        history = yaml.getBoolean("history.enabled", true);
        diagnostics = yaml.getBoolean("diagnostics.enabled", false);
        kits = yaml.getBoolean("advantages.kits", false);
        abilities = yaml.getBoolean("advantages.abilities", false);
        suspensionsEnabled = yaml.getBoolean("penalties.suspensions.enabled", true);
        suspensionThreshold = yaml.getInt("penalties.suspensions.abandons-threshold", 3);
        suspensionWindowMinutes = yaml.getInt("penalties.suspensions.window-minutes", 1440);
        suspensionMinutes = yaml.getInt("penalties.suspensions.duration-minutes", 30);
        if (suspensionThreshold < 1 || suspensionThreshold > 100
                || suspensionWindowMinutes < 1 || suspensionWindowMinutes > 43200
                || suspensionMinutes < 1 || suspensionMinutes > 43200)
            throw new IllegalArgumentException("Limites de suspensao competitiva invalidos");
        if (!"LOSS".equalsIgnoreCase(yaml.getString("abandonment.outcome", "LOSS")))
            throw new IllegalArgumentException("Politica de abandono invalida");
        Set<String> selected = new HashSet<String>();
        for (String mode : MatchMode.STANDARD)
            if (yaml.getBoolean("modes." + mode, !"custom".equals(mode))) selected.add(mode);
        modes = Collections.unmodifiableSet(selected);
        Set<String> groups = new HashSet<String>(yaml.getStringList("arenas.allowed-groups"));
        if (groups.isEmpty()) groups.add("ranked");
        for (String group : groups)
            if (group == null || !group.matches("[A-Za-z0-9_-]{1,32}"))
                throw new IllegalArgumentException("Grupo de arena ranqueada invalido");
        arenaGroups = Collections.unmodifiableSet(groups);
        Set<String> plugins = new HashSet<String>();
        for (String name : yaml.getStringList("authorized-plugins")) {
            if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}"))
                throw new IllegalArgumentException("Plugin autorizado invalido");
            plugins.add(name);
        }
        authorizedPlugins = Collections.unmodifiableSet(plugins);
        ConfigurationSection section = yaml.getConfigurationSection("divisions");
        if (section == null || section.getKeys(false).isEmpty())
            throw new IllegalArgumentException("Divisoes ausentes");
        List<Division> found = new ArrayList<Division>();
        Set<Integer> orders = new HashSet<Integer>();
        for (String id : section.getKeys(false)) {
            String path = "divisions." + id + ".";
            int rating = yaml.getInt(path + "minimum-rating", -1);
            int order = yaml.getInt(path + "order", -1);
            String name = yaml.getString(path + "name", id);
            String color = yaml.getString(path + "color", "&f");
            String icon = yaml.getString(path + "icon", "PAPER");
            if (!id.matches("[a-z0-9_-]{1,32}") || rating < 0 || order < 1
                    || !orders.add(order) || name == null || name.length() > 32
                    || color == null || !color.matches("&[0-9a-fA-F]")
                    || Material.matchMaterial(icon) == null)
                throw new IllegalArgumentException("Divisao invalida: " + id);
            found.add(new Division(id, name, color, icon, rating, order));
        }
        found.sort(Comparator.comparingInt(d -> d.order));
        if (found.get(0).minimum != 0) throw new IllegalArgumentException("Primeira divisao deve iniciar em zero");
        for (int i = 1; i < found.size(); i++)
            if (found.get(i).minimum <= found.get(i - 1).minimum)
                throw new IllegalArgumentException("Divisoes sobrepostas ou fora da ordem");
        divisions = Collections.unmodifiableList(found);
        messages = yaml;
    }

    public Division division(int rating) {
        Division selected = divisions.get(0);
        for (Division division : divisions) if (rating >= division.minimum) selected = division;
        return selected;
    }
    public String message(String key, String fallback) {
        String value = messages.getString("messages." + key, fallback);
        return value == null ? fallback : value;
    }
    public static RankedSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "ranked.yml");
        if (!file.exists()) plugin.saveResource("ranked.yml", false);
        return new RankedSettings(YamlConfiguration.loadConfiguration(file));
    }
    public static final class Division {
        public final String id, name, color, icon;
        public final int minimum, order;
        Division(String id, String name, String color, String icon, int minimum, int order) {
            this.id = id; this.name = name; this.color = color; this.icon = icon;
            this.minimum = minimum; this.order = order;
        }
    }
}
