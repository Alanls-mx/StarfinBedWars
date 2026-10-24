package com.starfinplugins.bedwars.world;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class WorldSettings {
    private final boolean voidGeneratorEnabled;
    private final boolean allowImport;
    private final boolean allowManagedDeletion;
    private final int platformHeight;
    private final int platformRadius;
    private final Material platformMaterial;
    private final long maximumRegionBlocks;

    private WorldSettings(boolean voidGeneratorEnabled, boolean allowImport, boolean allowManagedDeletion,
                          int platformHeight, int platformRadius, Material platformMaterial,
                          long maximumRegionBlocks) {
        this.voidGeneratorEnabled = voidGeneratorEnabled;
        this.allowImport = allowImport;
        this.allowManagedDeletion = allowManagedDeletion;
        this.platformHeight = platformHeight;
        this.platformRadius = platformRadius;
        this.platformMaterial = platformMaterial;
        this.maximumRegionBlocks = maximumRegionBlocks;
    }

    public static WorldSettings load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        if (!file.exists()) plugin.saveResource("worlds.yml", false);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception error) {
            throw new IOException("worlds.yml invalido: " + error.getMessage(), error);
        }
        String materialName = yaml.getString("worlds.platform.material");
        Material material = materialName == null ? null
            : Material.getMaterial(materialName.toUpperCase(Locale.ROOT));
        int height = yaml.getInt("worlds.platform.height", -1);
        int radius = yaml.getInt("worlds.platform.radius", -1);
        long maxRegionBlocks = yaml.getLong("worlds.maximum-region-blocks", -1L);
        if (!yaml.isBoolean("worlds.void-generator.enabled")
                || !yaml.isBoolean("worlds.allow-import")
                || !yaml.isBoolean("worlds.allow-managed-deletion")
                || material == null || material == Material.AIR || !material.isBlock() || !material.isSolid()
                || height < 1 || height > 253 || radius < 1 || radius > 32
                || maxRegionBlocks < 1 || maxRegionBlocks > 100000000L) {
            throw new IOException("Configuracao de mundos ausente ou fora dos limites permitidos.");
        }
        return new WorldSettings(yaml.getBoolean("worlds.void-generator.enabled"),
            yaml.getBoolean("worlds.allow-import"), yaml.getBoolean("worlds.allow-managed-deletion"),
            height, radius, material, maxRegionBlocks);
    }

    public boolean isVoidGeneratorEnabled() { return voidGeneratorEnabled; }
    public boolean isImportAllowed() { return allowImport; }
    public boolean isManagedDeletionAllowed() { return allowManagedDeletion; }
    public int getPlatformHeight() { return platformHeight; }
    public int getPlatformRadius() { return platformRadius; }
    public Material getPlatformMaterial() { return platformMaterial; }
    public long getMaximumRegionBlocks() { return maximumRegionBlocks; }
}
