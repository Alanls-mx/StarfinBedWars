package com.starfinplugins.bedwars.reconnect;

import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReconnectSettingsTest {
    private YamlConfiguration valid() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("reconnect.enabled", true);
        config.set("reconnect.mode", "CONFIRMATION");
        config.set("reconnect.grace-seconds", 120);
        config.set("reconnect.combat.tag-seconds", 15);
        config.set("reconnect.combat.disconnect-counts-as-death", true);
        return config;
    }

    @Test public void readsConfiguredPolicy() throws Exception {
        ReconnectSettings settings = ReconnectSettings.from(valid());
        assertTrue(settings.isEnabled());
        assertFalse(settings.isAutomatic());
        assertEquals(120, settings.getGraceSeconds());
        assertEquals(15, settings.getCombatSeconds());
        assertTrue(settings.isCombatDeath());
    }

    @Test public void rejectsNegativeAndUnboundedDeadlines() throws Exception {
        YamlConfiguration config = valid();
        config.set("reconnect.grace-seconds", -1);
        assertInvalid(config);
        config.set("reconnect.grace-seconds", 3601);
        assertInvalid(config);
        config.set("reconnect.grace-seconds", 120);
        config.set("reconnect.mode", "UNKNOWN");
        assertInvalid(config);
    }

    private void assertInvalid(YamlConfiguration config) throws Exception {
        try { ReconnectSettings.from(config); fail("Invalid policy accepted"); }
        catch (IOException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
}
