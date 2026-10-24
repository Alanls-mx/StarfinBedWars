package com.starfinplugins.bedwars.reconnect;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReconnectSettings {
    private final boolean enabled;
    private final boolean automatic;
    private final int graceSeconds;
    private final int combatSeconds;
    private final boolean combatDeath;

    private ReconnectSettings(boolean enabled, boolean automatic, int graceSeconds,
                              int combatSeconds, boolean combatDeath) {
        this.enabled = enabled;
        this.automatic = automatic;
        this.graceSeconds = graceSeconds;
        this.combatSeconds = combatSeconds;
        this.combatDeath = combatDeath;
    }

    public static ReconnectSettings load(JavaPlugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "reconnect.yml");
        if (!file.exists()) plugin.saveResource("reconnect.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return from(config);
    }

    public static ReconnectSettings from(YamlConfiguration config) throws IOException {
        if (!config.isBoolean("reconnect.enabled") || !config.isString("reconnect.mode")
                || !config.isInt("reconnect.grace-seconds")
                || !config.isInt("reconnect.combat.tag-seconds")
                || !config.isBoolean("reconnect.combat.disconnect-counts-as-death")) {
            throw new IOException("reconnect.yml incompleto ou invalido.");
        }
        String mode = config.getString("reconnect.mode");
        int grace = config.getInt("reconnect.grace-seconds");
        int combat = config.getInt("reconnect.combat.tag-seconds");
        if ((!"AUTOMATIC".equals(mode) && !"CONFIRMATION".equals(mode))
                || grace < 1 || grace > 3600 || combat < 0 || combat > 120) {
            throw new IOException("Modo ou prazo de reconnect fora dos limites.");
        }
        return new ReconnectSettings(config.getBoolean("reconnect.enabled"),
            "AUTOMATIC".equals(mode), grace, combat,
            config.getBoolean("reconnect.combat.disconnect-counts-as-death"));
    }

    public boolean isEnabled() { return enabled; }
    public boolean isAutomatic() { return automatic; }
    public int getGraceSeconds() { return graceSeconds; }
    public int getCombatSeconds() { return combatSeconds; }
    public boolean isCombatDeath() { return combatDeath; }
}
