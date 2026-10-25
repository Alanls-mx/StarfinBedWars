package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.util.Locale;

/** Stable public mode identifiers shared by results, rankings and discovery. */
public final class MatchMode {
    private MatchMode() { }

    public static String of(ArenaDefinition arena) {
        int size = -1;
        for (TeamDefinition team : arena.getTeamList()) {
            if (size == -1) size = team.getCapacity();
            else if (size != team.getCapacity()) return "custom";
        }
        switch (size) {
            case 1: return "solo";
            case 2: return "duo";
            case 3: return "trio";
            case 4: return "squad";
            default: return "custom";
        }
    }

    public static String persisted(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String mode = value.toLowerCase(Locale.ROOT);
        if ("mixed".equals(mode)) mode = "custom";
        switch (mode) {
            case "solo": case "duo": case "trio": case "squad": case "custom": return mode;
            default: throw new IllegalArgumentException("Modalidade invalida: " + value);
        }
    }
}
