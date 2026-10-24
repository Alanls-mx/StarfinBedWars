package com.starfinplugins.bedwars.shop;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

/**
 * Wraps item descriptions into short, Minecraft 1.8-friendly lore lines.
 * Color codes do not count against visible width and remain active after wraps.
 * Supports both real newlines and escaped \\n in the item catalog.
 */
public final class ShopLoreFormatter {
    private ShopLoreFormatter() { }

    public static List<String> wrapDescription(String description, int requestedWidth) {
        int width = Math.max(18, Math.min(40, requestedWidth));
        List<String> result = new ArrayList<String>();
        String source = description == null ? "" : description.replace("\\n", "\n");
        source = ChatColor.translateAlternateColorCodes('&', source);
        for (String paragraph : source.split("\n", -1)) {
            if (paragraph.trim().isEmpty()) {
                if (!result.isEmpty()) result.add(" ");
                continue;
            }
            String[] words = paragraph.trim().split("\\s+");
            String current = "";
            for (String word : words) {
                if (word.isEmpty()) continue;
                String piece = word;
                while (visibleLength(piece) > width) {
                    if (!current.isEmpty()) {
                        result.add(ChatColor.GRAY + current);
                        current = ChatColor.getLastColors(current);
                    }
                    int splitAt = visiblePrefixEnd(piece, width);
                    String first = piece.substring(0, splitAt);
                    result.add(ChatColor.GRAY + current + first);
                    current = ChatColor.getLastColors(current + first);
                    piece = piece.substring(splitAt);
                }
                if (piece.isEmpty()) continue;
                String proposed = current.isEmpty() || visibleLength(current) == 0
                    ? current + piece : current + " " + piece;
                if (!current.isEmpty() && visibleLength(proposed) > width) {
                    result.add(ChatColor.GRAY + current);
                    current = ChatColor.getLastColors(current) + piece;
                } else {
                    current = proposed;
                }
            }
            if (visibleLength(current) > 0) result.add(ChatColor.GRAY + current);
        }
        if (result.isEmpty()) result.add(ChatColor.GRAY + "Item para sua partida.");
        return result;
    }

    private static int visibleLength(String colored) {
        return ChatColor.stripColor(colored).length();
    }

    private static int visiblePrefixEnd(String text, int visibleCount) {
        int visible = 0;
        int index = 0;
        while (index < text.length() && visible < visibleCount) {
            if (text.charAt(index) == ChatColor.COLOR_CHAR && index + 1 < text.length()) {
                index += 2;
            } else {
                index++;
                visible++;
            }
        }
        return index;
    }
}
