package com.starfinplugins.bedwars.setup;

import java.util.Locale;
import org.bukkit.ChatColor;

public enum SetupTeamColor {
    WHITE("f", 0, "Branco"),
    ORANGE("6", 1, "Laranja"),
    MAGENTA("d", 2, "Magenta"),
    LIGHT_BLUE("b", 3, "Azul-claro"),
    YELLOW("e", 4, "Amarelo"),
    LIME("a", 5, "Lima"),
    PINK("d", 6, "Rosa"),
    GRAY("8", 7, "Cinza"),
    LIGHT_GRAY("7", 8, "Cinza-claro"),
    CYAN("3", 9, "Ciano"),
    PURPLE("5", 10, "Roxo"),
    BLUE("9", 11, "Azul"),
    BROWN("6", 12, "Marrom"),
    GREEN("2", 13, "Verde"),
    RED("c", 14, "Vermelho"),
    BLACK("0", 15, "Preto");

    private final String chatCode;
    private final byte blockData;
    private final String displayName;

    SetupTeamColor(String chatCode, int blockData, String displayName) {
        this.chatCode = chatCode;
        this.blockData = (byte) blockData;
        this.displayName = displayName;
    }

    public String getChatCode() { return chatCode; }
    public byte getBlockData() { return blockData; }
    public String getDisplayName() { return displayName; }
    public ChatColor getChatColor() { return ChatColor.getByChar(chatCode.charAt(0)); }

    public static SetupTeamColor parse(String value) {
        if (value == null) throw new IllegalArgumentException("Cor ausente.");
        String normalized = value.trim().replace("&", "").replace("§", "")
            .replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.length() == 1) {
            for (SetupTeamColor color : values()) {
                if (color.chatCode.equalsIgnoreCase(normalized)) return color;
            }
        }
        if ("LIGHTGRAY".equals(normalized) || "SILVER".equals(normalized)) normalized = "LIGHT_GRAY";
        if ("AQUA".equals(normalized)) normalized = "CYAN";
        if ("PINK".equals(normalized)) return PINK;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Cor desconhecida. Use uma cor Minecraft ou codigo &0-&f.");
        }
    }

    public static SetupTeamColor fromChatCode(String code) {
        if (code == null) return null;
        switch (code.toLowerCase(Locale.ROOT)) {
            case "f": return WHITE;
            case "6": return ORANGE;
            case "d": return MAGENTA;
            case "b": return LIGHT_BLUE;
            case "e": return YELLOW;
            case "a": return LIME;
            case "8": return GRAY;
            case "7": return LIGHT_GRAY;
            case "3": return CYAN;
            case "5": return PURPLE;
            case "9": return BLUE;
            case "2": return GREEN;
            case "c": return RED;
            case "0": return BLACK;
            default: break;
        }
        return null;
    }
}
