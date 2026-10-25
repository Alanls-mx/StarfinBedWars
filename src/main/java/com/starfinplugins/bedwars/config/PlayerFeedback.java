package com.starfinplugins.bedwars.config;

import com.starfinplugins.bedwars.match.MatchMessageTemplates;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Consistent color rendering for configurable player-facing feedback. */
public final class PlayerFeedback {
    private PlayerFeedback() { }

    public static void send(Plugin plugin, Player player, String path,
                            String fallback, String... tokens) {
        String template = plugin.getConfig().getString(path, fallback);
        player.sendMessage(render(template, tokens));
    }

    public static String render(String template, String... tokens) {
        return ChatColor.translateAlternateColorCodes('&',
            MatchMessageTemplates.format(template, tokens));
    }
}
