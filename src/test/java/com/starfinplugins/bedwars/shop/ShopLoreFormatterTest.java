package com.starfinplugins.bedwars.shop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.bukkit.ChatColor;
import org.junit.Test;

public class ShopLoreFormatterTest {
    @Test public void wrapsLongBedbugDescriptionWithin28VisibleCharacters() {
        String original = "Arremesse uma bola de neve para invocar um Silverfish aliado.";
        List<String> lines = ShopLoreFormatter.wrapDescription(original, 28);
        assertTrue(lines.size() >= 2);
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            String plain = ChatColor.stripColor(line);
            assertTrue("Lore oversized: " + plain, plain.length() <= 28);
            if (joined.length() > 0) joined.append(" ");
            joined.append(plain);
        }
        assertEquals(original, joined.toString());
    }

    @Test public void preservesColorsAfterLineWraps() {
        List<String> lines = ShopLoreFormatter.wrapDescription(
            "&aEste item aumenta bastante sua velocidade em combate.", 22);
        assertTrue(lines.size() >= 2);
        for (String line : lines) {
            assertTrue(ChatColor.stripColor(line).length() <= 22);
            assertTrue(line.startsWith(ChatColor.GRAY.toString()));
        }
        assertTrue(lines.get(1).contains(ChatColor.GREEN.toString()));
    }

    @Test public void supportsExplicitNewlinesAndLongIndividualWords() {
        List<String> lines = ShopLoreFormatter.wrapDescription(
            "Um exemplo\\nSupercalifragilisticexpialidocious", 18);
        assertTrue(lines.size() >= 3);
        for (String line : lines) {
            assertTrue(ChatColor.stripColor(line).length() <= 18);
        }
    }

    @Test public void clampsSettingsAndProvidesFallback() {
        assertEquals("Item para sua partida.",
            ChatColor.stripColor(ShopLoreFormatter.wrapDescription("", 30).get(0)));
        List<String> lines = ShopLoreFormatter.wrapDescription(
            "Um texto muito longo usado para testar o tamanho minimo.", 4);
        for (String line : lines) assertTrue(ChatColor.stripColor(line).length() <= 18);
    }
}
