package com.starfinplugins.bedwars.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/** Optional prefix for player-facing chat messages. */
public final class MessagePrefix {
    private MessagePrefix() { }

    public static String value(Plugin plugin) {
        return value(plugin.getConfig());
    }

    static String value(FileConfiguration config) {
        if (!config.getBoolean("messages.prefix.enabled", false)) return "";
        String configured = config.getString("messages.prefix.text", "&b[BedWars] ");
        if (configured == null || configured.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', configured) + ChatColor.RESET;
    }

    public static String apply(Plugin plugin, String message) {
        return value(plugin) + message;
    }
}
