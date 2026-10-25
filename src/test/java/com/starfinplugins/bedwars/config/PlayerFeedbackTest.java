package com.starfinplugins.bedwars.config;

import static org.junit.Assert.assertEquals;
import org.bukkit.ChatColor;
import org.junit.Test;

public class PlayerFeedbackTest {
    @Test public void restoresConfiguredColorAfterColoredItemName() {
        String text = PlayerFeedback.render("&aComprado: &f{item}&a.",
            "item", "&cEspada");
        assertEquals(ChatColor.GREEN + "Comprado: " + ChatColor.WHITE
            + ChatColor.RED + "Espada" + ChatColor.GREEN + ".", text);
    }
}
