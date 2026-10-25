package com.starfinplugins.bedwars.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable preferences shared across all BedWars Spigot nodes for one UUID. */
public final class PlayerPreferences {
    public final String language;
    /** Null uses shop.yml default Quick Buy; empty means an intentionally empty Quick Buy. */
    private final List<String> quickBuy;
    public final String selectedTitle;

    public PlayerPreferences(String language, List<String> quickBuy) {
        this(language, quickBuy, null);
    }

    public PlayerPreferences(String language, List<String> quickBuy, String selectedTitle) {
        this.language = languageOrDefault(language);
        this.quickBuy = quickBuy == null ? null : Collections.unmodifiableList(
            new ArrayList<String>(normalizeItems(quickBuy)));
        this.selectedTitle = selectedTitle;
    }

    public List<String> getQuickBuy() { return quickBuy; }
    public PlayerPreferences withLanguage(String next) { return new PlayerPreferences(next, quickBuy, selectedTitle); }
    public PlayerPreferences withQuickBuy(List<String> next) { return new PlayerPreferences(language, next, selectedTitle); }
    public PlayerPreferences withSelectedTitle(String next) {
        return new PlayerPreferences(language, quickBuy, next);
    }

    public static String languageOrDefault(String value) {
        if (value == null) return "pt_BR";
        if ("en_US".equalsIgnoreCase(value)) return "en_US";
        if ("es_ES".equalsIgnoreCase(value)) return "es_ES";
        return "pt_BR";
    }

    public static boolean supportedLanguage(String value) {
        return "pt_BR".equalsIgnoreCase(value) || "en_US".equalsIgnoreCase(value)
            || "es_ES".equalsIgnoreCase(value);
    }

    public static List<String> normalizeItems(Iterable<String> ids) {
        Set<String> unique = new LinkedHashSet<String>();
        for (String id : ids) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) continue;
            unique.add(id);
            if (unique.size() == 28) break;
        }
        return new ArrayList<String>(unique);
    }

    public static String encode(List<String> items) {
        if (items == null) return null;
        return String.join(",", normalizeItems(items));
    }

    public static List<String> decode(String value) {
        if (value == null) return null;
        if (value.isEmpty()) return Collections.emptyList();
        if (value.length() > 2048) throw new IllegalArgumentException("Quick Buy excedeu o limite.");
        return normalizeItems(java.util.Arrays.asList(value.split(",", -1)));
    }
}
