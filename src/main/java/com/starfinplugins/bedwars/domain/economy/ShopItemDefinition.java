package com.starfinplugins.bedwars.domain.economy;

import org.bukkit.Material;

public final class ShopItemDefinition {
    private final String id;
    private final String displayName;
    private final Material material;
    private final short data;
    private final int amount;
    private final ResourceType costType;
    private final int cost;
    private final int slot;
    private final boolean quickBuy;
    private final String description;

    public ShopItemDefinition(String id, String displayName, Material material, short data, int amount,
                              ResourceType costType, int cost, int slot, boolean quickBuy) {
        this(id, displayName, material, data, amount, costType, cost, slot, quickBuy, "");
    }

    public ShopItemDefinition(String id, String displayName, Material material, short data, int amount,
                              ResourceType costType, int cost, int slot, boolean quickBuy,
                              String description) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}") || displayName == null
                || displayName.trim().isEmpty() || material == null || amount < 1 || amount > 64
                || material == Material.AIR || data < 0
                || costType == null || cost < 1 || cost > 4096 || slot < 0 || slot > 44) {
            throw new IllegalArgumentException("Definicao de item da loja invalida.");
        }
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.data = data;
        this.amount = amount;
        this.costType = costType;
        this.cost = cost;
        this.slot = slot;
        this.quickBuy = quickBuy;
        this.description = description == null ? "" : description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public short getData() { return data; }
    public int getAmount() { return amount; }
    public ResourceType getCostType() { return costType; }
    public int getCost() { return cost; }
    public int getSlot() { return slot; }
    public boolean isQuickBuy() { return quickBuy; }
    public String getDescription() { return description; }
}
