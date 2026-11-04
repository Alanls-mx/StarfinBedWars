package com.starfinplugins.bedwars.config;

import static org.junit.Assert.assertEquals;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class MessagePrefixTest {
    @Test public void defaultsToNoPrefixWithoutSuppressingMessage() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals("", MessagePrefix.value(config));
    }

    @Test public void customPrefixResetsColorBeforeMessage() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix.enabled", true);
        config.set("messages.prefix.text", "&d[MeuServidor] ");
        assertEquals(ChatColor.LIGHT_PURPLE + "[MeuServidor] " + ChatColor.RESET,
            MessagePrefix.value(config));
    }

    @Test public void disabledPrefixOverridesConfiguredText() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix.enabled", false);
        config.set("messages.prefix.text", "&c[Custom] ");
        assertEquals("", MessagePrefix.value(config));
    }
}
