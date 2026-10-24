package com.starfinplugins.bedwars.domain.economy;

import org.bukkit.Material;

public enum ResourceType {
    IRON(Material.IRON_INGOT),
    GOLD(Material.GOLD_INGOT),
    DIAMOND(Material.DIAMOND),
    EMERALD(Material.EMERALD);

    private final Material material;

    ResourceType(Material material) {
        this.material = material;
    }

    public Material getMaterial() {
        return material;
    }
}
