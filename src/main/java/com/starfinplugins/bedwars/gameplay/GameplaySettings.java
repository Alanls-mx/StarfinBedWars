package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Validated, immutable gameplay catalog. No configured command is ever executed. */
public final class GameplaySettings {
    public enum AbilityType { PASSIVE, ACTIVE, EVENT }
    public enum AbilityEffect { FALL_REDUCTION, SPEED_ON_KILL, RESISTANCE_ON_RESPAWN, SPEED_COMMAND }
    public enum SplitPolicy { ROUND_ROBIN, SHARED }

    public final Map<String, Kit> kits;
    public final Map<String, Ability> abilities;
    public final boolean splitEnabled, antiAfkEnabled;
    public final SplitPolicy splitPolicy;
    public final double splitRadius;
    public final int splitMaximumRecipients;
    public final int waitingTimeoutSeconds, gameTimeoutSeconds, warningSeconds;
    public final String afkExemptPermission;
    public final int maximumTeamSize, maximumTotalPlayers;

    private GameplaySettings(YamlConfiguration yaml) {
        ConfigurationSection kitSection = yaml.getConfigurationSection("kits");
        ConfigurationSection abilitySection = yaml.getConfigurationSection("abilities");
        if (kitSection == null || abilitySection == null) throw new IllegalArgumentException("kits/abilities ausentes");
        Map<String, Kit> parsedKits = new LinkedHashMap<String, Kit>();
        for (String id : kitSection.getKeys(false)) {
            validateId(id);
            parsedKits.put(id, new Kit(id, kitSection.getConfigurationSection(id)));
        }
        if (!parsedKits.containsKey("default") || !parsedKits.get("default").enabled
                || parsedKits.get("default").level != 1 || !parsedKits.get("default").permission.isEmpty())
            throw new IllegalArgumentException("Kit default gratuito e habilitado obrigatorio");
        Map<String, Ability> parsedAbilities = new LinkedHashMap<String, Ability>();
        for (String id : abilitySection.getKeys(false)) {
            validateId(id);
            if ("none".equals(id)) throw new IllegalArgumentException("none e reservado para nenhuma habilidade");
            parsedAbilities.put(id, new Ability(id, abilitySection.getConfigurationSection(id)));
        }
        kits = Collections.unmodifiableMap(parsedKits);
        abilities = Collections.unmodifiableMap(parsedAbilities);
        splitEnabled = yaml.getBoolean("generator-split.enabled", false);
        splitPolicy = SplitPolicy.valueOf(yaml.getString("generator-split.policy", "ROUND_ROBIN"));
        splitRadius = yaml.getDouble("generator-split.radius", 3.0);
        splitMaximumRecipients = yaml.getInt("generator-split.maximum-recipients", 4);
        antiAfkEnabled = yaml.getBoolean("anti-afk.enabled", false);
        waitingTimeoutSeconds = yaml.getInt("anti-afk.waiting-timeout-seconds", 180);
        gameTimeoutSeconds = yaml.getInt("anti-afk.game-timeout-seconds", 300);
        warningSeconds = yaml.getInt("anti-afk.warning-seconds", 30);
        afkExemptPermission = yaml.getString("anti-afk.exempt-permission", "starfinbedwars.afk.exempt");
        maximumTeamSize = yaml.getInt("custom-teams.maximum-team-size", 8);
        maximumTotalPlayers = yaml.getInt("custom-teams.maximum-total-players", 64);
        if (splitRadius < 0.5 || splitRadius > 8 || splitMaximumRecipients < 1
                || splitMaximumRecipients > 16 || waitingTimeoutSeconds < 30
                || gameTimeoutSeconds < 60 || warningSeconds < 5
                || warningSeconds >= Math.min(waitingTimeoutSeconds, gameTimeoutSeconds)
                || maximumTeamSize < 4 || maximumTeamSize > 16
                || maximumTotalPlayers < 8 || maximumTotalPlayers > 256
                || afkExemptPermission == null || !afkExemptPermission.matches("[a-zA-Z0-9_.-]{1,80}"))
            throw new IllegalArgumentException("Limites de gameplay.yml invalidos");
    }

    public static GameplaySettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gameplay.yml");
        if (!file.exists()) plugin.saveResource("gameplay.yml", false);
        return parse(YamlConfiguration.loadConfiguration(file));
    }
    public static GameplaySettings parse(YamlConfiguration yaml) { return new GameplaySettings(yaml); }

    public static final class Grant {
        public final Material material;
        public final int amount;
        public final short data;
        Grant(Material material, int amount, int data) {
            if (material == null || material == Material.AIR || amount < 1 || amount > 64
                    || data < 0 || data > 32767 || !allowedGrant(material))
                throw new IllegalArgumentException("Item de kit invalido: " + material);
            this.material = material; this.amount = amount; this.data = (short) data;
        }
        private static boolean allowedGrant(Material material) {
            switch (material) {
                case WOOL: case GOLDEN_APPLE: case POTION: case COMPASS: case LADDER:
                case IRON_INGOT: case GOLD_INGOT: case ARROW: case BOW: case SHEARS:
                case WOOD: case ENDER_PEARL: return true;
                default: return false;
            }
        }
    }

    public abstract static class Choice {
        public final String id, permission;
        public final boolean enabled;
        public final int level;
        public final Material icon;
        public final Set<String> modes;
        private final Map<String, String> names, descriptions;

        Choice(String id, ConfigurationSection section) {
            if (section == null) throw new IllegalArgumentException("Definicao ausente: " + id);
            this.id = id;
            enabled = section.getBoolean("enabled", false);
            level = section.getInt("level", 1);
            permission = section.getString("permission", "");
            icon = material(section.getString("icon"));
            if (level < 1 || level > 10000 || permission == null
                    || !permission.matches("[a-zA-Z0-9_.-]{0,80}") || icon == Material.AIR)
                throw new IllegalArgumentException("Requisitos invalidos: " + id);
            names = translations(section.getConfigurationSection("names"), id);
            descriptions = translations(section.getConfigurationSection("descriptions"), id);
            Set<String> allowed = new LinkedHashSet<String>();
            for (String mode : section.getStringList("modes")) {
                String normalized = MatchMode.persisted(mode);
                if (normalized == null) throw new IllegalArgumentException("Modalidade vazia: " + id);
                allowed.add(normalized);
            }
            if (allowed.isEmpty()) throw new IllegalArgumentException("Modalidades ausentes: " + id);
            modes = Collections.unmodifiableSet(allowed);
        }
        public String name(String language) { return names.getOrDefault(language, names.get("pt_BR")); }
        public String description(String language) {
            return descriptions.getOrDefault(language, descriptions.get("pt_BR"));
        }
    }
    public static final class Kit extends Choice {
        public final List<Grant> startItems, respawnItems;
        public final Material permanentTool;
        Kit(String id, ConfigurationSection section) {
            super(id, section);
            startItems = grants(section.getMapList("start-items"));
            respawnItems = grants(section.getMapList("respawn-items"));
            String tool = section.getString("permanent-tool", "");
            permanentTool = tool == null || tool.isEmpty() ? null : material(tool);
            if (permanentTool != null && !permanentTool.name().endsWith("_PICKAXE")
                    && !permanentTool.name().endsWith("_AXE"))
                throw new IllegalArgumentException("Ferramenta permanente invalida: " + id);
            if (startItems.isEmpty() && respawnItems.isEmpty() && permanentTool == null)
                throw new IllegalArgumentException("Kit sem efeito real: " + id);
        }
    }
    public static final class Ability extends Choice {
        public final AbilityType type;
        public final AbilityEffect effect;
        public final int durationSeconds, cooldownSeconds;
        public final double potency;
        Ability(String id, ConfigurationSection section) {
            super(id, section);
            type = AbilityType.valueOf(section.getString("type", ""));
            effect = AbilityEffect.valueOf(section.getString("effect", ""));
            durationSeconds = section.getInt("duration-seconds", 0);
            cooldownSeconds = section.getInt("cooldown-seconds", 0);
            potency = section.getDouble("potency", 1.0);
            if (durationSeconds < 0 || durationSeconds > 30 || cooldownSeconds < 0
                    || cooldownSeconds > 3600 || potency <= 0 || potency > 2
                    || (effect == AbilityEffect.FALL_REDUCTION && potency > 1.0)
                    || (effect == AbilityEffect.FALL_REDUCTION) != (type == AbilityType.PASSIVE)
                    || (effect == AbilityEffect.SPEED_COMMAND) != (type == AbilityType.ACTIVE)
                    || (type == AbilityType.EVENT && effect != AbilityEffect.SPEED_ON_KILL
                        && effect != AbilityEffect.RESISTANCE_ON_RESPAWN))
                throw new IllegalArgumentException("Habilidade invalida: " + id);
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("ID invalido: " + id);
    }
    private static Material material(String text) {
        try { return Material.valueOf(text == null ? "" : text.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Material invalido: " + text); }
    }
    private static Map<String, String> translations(ConfigurationSection section, String id) {
        if (section == null) throw new IllegalArgumentException("Traducoes ausentes: " + id);
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String language : new String[] {"pt_BR", "en_US", "es_ES"}) {
            String text = section.getString(language);
            if (text == null || text.trim().isEmpty() || text.length() > 96
                    || text.contains("\n") || text.contains("\r"))
                throw new IllegalArgumentException("Texto invalido: " + id + "/" + language);
            values.put(language, text.trim());
        }
        return Collections.unmodifiableMap(values);
    }
    private static List<Grant> grants(List<Map<?, ?>> configured) {
        List<Grant> parsed = new ArrayList<Grant>();
        if (configured.size() > 9) throw new IllegalArgumentException("Itens demais no kit");
        for (Map<?, ?> item : configured) {
            Material material = material(String.valueOf(item.get("material")));
            int amount = integer(item.get("amount"), 1);
            int data = integer(item.get("data"), 0);
            parsed.add(new Grant(material, amount, data));
        }
        return Collections.unmodifiableList(parsed);
    }
    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Numero invalido: " + value); }
    }
}
