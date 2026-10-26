package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.io.File;
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

/** The twelve official kits. Runtime delivery reads only this validated snapshot. */
public final class KitCatalog {
    public static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(
        "builder", "miner", "lumberjack", "shears", "carpenter", "climber", "blacksmith",
        "archer", "trapper", "explorer", "defender", "pyromancer"));
    public final Map<String, Definition> kits;

    private KitCatalog(YamlConfiguration yaml, PurchaseSettings defaults) {
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        if (root == null || root.getKeys(false).size() != IDS.size())
            throw new IllegalArgumentException("kits.yml exige os 12 IDs oficiais");
        Map<String, Definition> parsed = new LinkedHashMap<String, Definition>();
        for (String id : IDS) parsed.put(id, new Definition(id, root.getConfigurationSection(id), defaults));
        kits = Collections.unmodifiableMap(parsed);
    }
    public static KitCatalog load(JavaPlugin plugin, PurchaseSettings defaults) {
        File file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) plugin.saveResource("kits.yml", false);
        return parse(YamlConfiguration.loadConfiguration(file), defaults);
    }
    public static KitCatalog parse(YamlConfiguration yaml, PurchaseSettings defaults) {
        return new KitCatalog(yaml, defaults);
    }
    public enum Category { COMMON, SPECIAL }
    public enum Delivery { FIRST_SPAWN, EVERY_RESPAWN, ONCE_PER_MATCH }

    public static final class Definition {
        public final String id, name, description, permission;
        public final boolean enabled;
        public final Material icon, material;
        public final Category category;
        public final Delivery delivery;
        public final PurchaseSettings.Scope scope;
        public final int minimumPlayerLevel, fireTicks;
        public final Set<String> modes;
        private final int[] prices = new int[6];
        private final int[] values = new int[6];

        private Definition(String id, ConfigurationSection section, PurchaseSettings defaults) {
            if (section == null) throw new IllegalArgumentException("Kit ausente: " + id);
            this.id = id;
            name = text(section, "name", 64);
            description = text(section, "description", 160);
            permission = text(section, "permission", 80);
            if (!permission.matches("[a-zA-Z0-9_.-]{1,80}"))
                throw new IllegalArgumentException(id + ": permissao invalida");
            icon = material(text(section, "icon", 40));
            String configurableMaterial = section.getString("material", "");
            material = configurableMaterial.isEmpty() ? null : material(configurableMaterial);
            try { category = Category.valueOf(text(section, "category", 12).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(id + ": categoria invalida"); }
            try { delivery = Delivery.valueOf(text(section, "delivery", 20).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(id + ": entrega invalida"); }
            ConfigurationSection purchase = section.getConfigurationSection("purchase");
            scope = purchase == null ? defaults.kitDefault
                : PurchaseSettings.scope(purchase.getString("scope", defaults.kitDefault.name()));
            enabled = section.getBoolean("enabled", true);
            minimumPlayerLevel = section.getInt("minimum-player-level", 1);
            fireTicks = section.getInt("fire-duration-ticks", 40);
            if (minimumPlayerLevel < 1 || minimumPlayerLevel > 10000 || fireTicks < 1 || fireTicks > 100)
                throw new IllegalArgumentException(id + ": requisito ou duracao invalida");
            Set<String> allowed = new LinkedHashSet<String>();
            List<String> configuredModes = section.contains("allowed-modes")
                ? section.getStringList("allowed-modes") : section.getStringList("modes");
            for (String mode : configuredModes) {
                String canonical = MatchMode.persisted(mode);
                if (canonical == null || !defaults.modes.contains(canonical))
                    throw new IllegalArgumentException(id + ": modalidade invalida " + mode);
                allowed.add(canonical);
            }
            if (allowed.isEmpty()) throw new IllegalArgumentException(id + ": modalidades vazias");
            modes = Collections.unmodifiableSet(allowed);
            ConfigurationSection levels = section.getConfigurationSection("levels");
            if (levels == null || levels.getKeys(false).size() != 5)
                throw new IllegalArgumentException(id + ": exatamente cinco niveis obrigatorios");
            for (int level = 1; level <= 5; level++) {
                ConfigurationSection configured = levels.getConfigurationSection(Integer.toString(level));
                if (configured == null || !configured.isInt("price") || !configured.isInt("value"))
                    throw new IllegalArgumentException(id + ": nivel incompleto " + level);
                prices[level] = configured.getInt("price");
                values[level] = configured.getInt("value");
                if (prices[level] < 0 || prices[level] > 100000000 || values[level] < 1
                        || values[level] > 100 || level > 1 && values[level] < values[level - 1]
                            && !id.equals("explorer"))
                    throw new IllegalArgumentException(id + ": valor/preco invalido no nivel " + level);
            }
            if (id.equals("explorer") && !(values[1] > values[2] && values[2] > values[3]
                    && values[3] > values[4] && values[4] > values[5]))
                throw new IllegalArgumentException("explorer: intervalo deve diminuir por nivel");
            if (id.equals("defender") && (material == null || !Arrays.asList(
                    Material.SANDSTONE, Material.WOOL, Material.WOOD, Material.GLASS,
                    Material.OBSIDIAN, Material.HARD_CLAY, Material.ENDER_STONE).contains(material)))
                throw new IllegalArgumentException("defender: bloco defensivo inseguro");
            if (id.equals("pyromancer") && material != Material.BLAZE_POWDER
                    && material != Material.MAGMA_CREAM && material != Material.BLAZE_ROD)
                throw new IllegalArgumentException("pyromancer: item incendiario inseguro");
        }
        public int value(int level) { return values[checked(level)]; }
        public int price(int level) { return prices[checked(level)]; }
        private int checked(int level) {
            if (level < 1 || level > 5) throw new IllegalArgumentException("Nivel invalido: " + level);
            return level;
        }
    }
    private static String text(ConfigurationSection section, String key, int max) {
        String value = section.getString(key);
        if (value == null || value.trim().isEmpty() || value.length() > max
                || value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " invalido");
        return value.trim();
    }
    private static Material material(String value) {
        try {
            Material material = Material.valueOf(value.toUpperCase(Locale.ROOT));
            if (material == Material.AIR) throw new IllegalArgumentException();
            return material;
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Material invalido: " + value); }
    }
}
