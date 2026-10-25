package com.starfinplugins.bedwars.network;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit identity prevents two Spigot nodes overwriting each other's advertised arenas. */
public final class NetworkSettings {
    public final boolean enabled;
    public final String serverId;
    public final String lobbyServer;
    public final int refreshTicks;
    public final int poolSize;

    private NetworkSettings(YamlConfiguration yaml) {
        enabled = yaml.getBoolean("enabled", false);
        serverId = yaml.getString("server-id", "").trim();
        lobbyServer = yaml.getString("lobby-server", "auth").trim();
        refreshTicks = yaml.getInt("refresh-ticks", 100);
        poolSize = yaml.getInt("mysql-pool-size", 2);
        if (enabled && (!serverId.matches("[A-Za-z0-9_-]{1,32}")
                || !lobbyServer.matches("[A-Za-z0-9_-]{1,32}")
                || refreshTicks < 20 || refreshTicks > 1200 || poolSize < 1 || poolSize > 10)) {
            throw new IllegalArgumentException("network.yml invalido: defina server-id unico para cada servidor Spigot.");
        }
    }

    public static NetworkSettings load(JavaPlugin plugin) {
        File config = new File(plugin.getDataFolder(), "network.yml");
        if (!config.exists()) plugin.saveResource("network.yml", false);
        return new NetworkSettings(YamlConfiguration.loadConfiguration(config));
    }
}
