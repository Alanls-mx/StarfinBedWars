package com.starfinplugins.bedwars.match;

/** Pure, version-independent token renderer for configurable match messages. */
public final class MatchMessageTemplates {
    private MatchMessageTemplates() { }

    public static String format(String template, String... tokens) {
        if (template == null) return "";
        if (tokens == null || tokens.length % 2 != 0) {
            throw new IllegalArgumentException("Tokens devem ser pares de chave e valor.");
        }
        String text = template;
        for (int i = 0; i < tokens.length; i += 2) {
            String value = tokens[i + 1] == null ? "" : tokens[i + 1];
            text = text.replace("{" + tokens[i] + "}", value);
        }
        return text;
    }

    public static String deathKey(String reason, boolean killer, boolean finalKill) {
        String type = reason == null ? "generic" : reason;
        if (!"void".equals(type) && !"fall".equals(type) && !"fire".equals(type)
                && !"lava".equals(type) && !"drowning".equals(type)
                && !"explosion".equals(type) && !"projectile".equals(type)
                && !"combat".equals(type)) type = "generic";
        String result = "messages.death." + type + (killer ? "-killer" : "");
        return result + (finalKill ? "-final" : "");
    }
}
