package com.starfinplugins.bedwars.shop;

import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.ShopItemDefinition;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ShopSettings {
    private final Map<String, ShopItemDefinition> items;

    private ShopSettings(Map<String, ShopItemDefinition> items) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<String, ShopItemDefinition>(items));
    }

    public static ShopSettings load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception error) {
            throw new IOException("shop.yml invalido: " + error.getMessage(), error);
        }
        addNewCatalogItems(plugin, file, yaml);
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) throw new IOException("shop.yml: secao items ausente.");
        Map<String, ShopItemDefinition> items = new LinkedHashMap<String, ShopItemDefinition>();
        boolean[] usedSlots = new boolean[45];
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null || !item.isString("material") || !item.isString("resource")
                    || !item.isString("name") || !item.isInt("amount") || !item.isInt("cost")
                    || !item.isInt("slot") || !item.isInt("data") || !item.isBoolean("quick-buy")) {
                throw new IOException("Item de loja incompleto: " + id);
            }
            try {
                Material material = Material.valueOf(item.getString("material").toUpperCase(Locale.ROOT));
                // Legacy catalogs listed four armor pieces. A purchase now upgrades one
                // logical armor tier, so expose only its leggings entry for that tier.
                String materialName = material.name();
                if (materialName.endsWith("_HELMET") || materialName.endsWith("_CHESTPLATE")
                        || materialName.endsWith("_BOOTS")
                        || materialName.equals("LEATHER_LEGGINGS")) continue;
                ResourceType resource = ResourceType.valueOf(item.getString("resource").toUpperCase(Locale.ROOT));
                int slot = item.getInt("slot");
                if (slot < 0 || slot >= usedSlots.length || usedSlots[slot]) {
                    throw new IllegalArgumentException("slot invalido ou duplicado: " + slot);
                }
                ShopItemDefinition definition = new ShopItemDefinition(id,
                    item.getString("name"), material, (short) item.getInt("data"),
                    item.getInt("amount"), resource, item.getInt("cost"), slot,
                    item.getBoolean("quick-buy"), item.getString("description", ""));
                usedSlots[slot] = true;
                items.put(id, definition);
            } catch (IllegalArgumentException error) {
                throw new IOException("Item de loja invalido " + id + ": " + error.getMessage(), error);
            }
        }
        if (items.isEmpty()) throw new IOException("shop.yml nao define nenhum item.");
        return new ShopSettings(items);
    }

    public Map<String, ShopItemDefinition> getItems() { return items; }

    private static void addNewCatalogItems(Plugin plugin, File file, YamlConfiguration current)
            throws IOException {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource("shop.yml"),
                StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (Exception error) {
            throw new IOException("Nao foi possivel ler o catalogo padrao.", error);
        }
        ConfigurationSection defaultItems = defaults.getConfigurationSection("items");
        ConfigurationSection savedItems = current.getConfigurationSection("items");
        if (defaultItems == null || savedItems == null) return;
        boolean changed = false;
        for (String id : new String[] {"tnt", "ladder", "obsidian", "ender-pearl",
                "speed-potion", "water-bucket"}) {
            if (savedItems.contains(id)) continue;
            int slot = defaultItems.getInt(id + ".slot", -1);
            boolean occupied = false;
            for (String existing : savedItems.getKeys(false)) {
                if (savedItems.getInt(existing + ".slot", -1) == slot) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) {
                plugin.getLogger().warning("Item " + id + " nao adicionado: slot " + slot
                    + " ja usado no shop.yml personalizado.");
                continue;
            }
            ConfigurationSection template = defaultItems.getConfigurationSection(id);
            for (String key : template.getKeys(false)) {
                savedItems.set(id + "." + key, template.get(key));
            }
            changed = true;
        }
        if (!changed) return;
        byte[] original = Files.readAllBytes(file.toPath());
        File backup = new File(file.getParentFile(), "shop.yml.pre-extended-catalog.bak");
        if (!backup.exists()) Files.write(backup.toPath(), original);
        try { current.save(file); }
        catch (Exception error) {
            Files.write(file.toPath(), original);
            throw new IOException("Falha ao adicionar os itens novos ao shop.yml.", error);
        }
        plugin.getLogger().info("Novos itens adicionados ao shop.yml; precos existentes preservados.");
    }

    public List<ShopItemDefinition> getQuickBuyItems() {
        List<ShopItemDefinition> result = new ArrayList<ShopItemDefinition>();
        for (ShopItemDefinition item : items.values()) {
            if (item.isQuickBuy()) result.add(item);
        }
        return Collections.unmodifiableList(result);
    }
}
