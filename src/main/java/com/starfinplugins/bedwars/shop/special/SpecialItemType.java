package com.starfinplugins.bedwars.shop.special;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

/** Unique item markers prevent regular eggs/pumpkins/milk from activating shop abilities. */
public enum SpecialItemType {
    FIREBALL("fireball", Material.FIREBALL, "Bola de Fogo"),
    BRIDGE_EGG("bridge-egg", Material.EGG, "Ovo de Ponte"),
    BEDBUG("bedbug", Material.SNOW_BALL, "BedBug"),
    DREAM_DEFENDER("dream-defender", Material.PUMPKIN, "Defensor de Sonhos"),
    MAGIC_MILK("magic-milk", Material.MILK_BUCKET, "Leite Magico");

    private static final String MARKER = ChatColor.DARK_GRAY + "StarfinBW:";
    private final String id;
    private final Material material;
    private final String display;

    SpecialItemType(String id, Material material, String display) {
        this.id = id;
        this.material = material;
        this.display = display;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }

    public static SpecialItemType byId(String id) {
        for (SpecialItemType type : values()) if (type.id.equals(id)) return type;
        return null;
    }

    public static ItemStack mark(String id, ItemStack original) {
        SpecialItemType type = byId(id);
        if (type == null || original == null || original.getType() != type.material) return original;
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return original;
        meta.setDisplayName(ChatColor.GOLD + type.display);
        List<String> lore = meta.hasLore()
            ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        lore.add(MARKER + type.id);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static SpecialItemType identify(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;
        for (String line : meta.getLore()) {
            if (line == null || !line.startsWith(MARKER)) continue;
            SpecialItemType type = byId(line.substring(MARKER.length()));
            if (type != null && type.material == stack.getType()) return type;
        }
        return null;
    }
}
