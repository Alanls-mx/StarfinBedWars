package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stable public mode identifiers shared by results, rankings and discovery. */
public final class MatchMode {
    public static final List<String> STANDARD = Collections.unmodifiableList(Arrays.asList(
        "solo", "duo", "trio", "squad", "custom"));
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
        if ("doubles".equals(mode)) mode = "duo";
        if ("trios".equals(mode)) mode = "trio";
        if ("quads".equals(mode)) mode = "squad";
        if ("teams".equals(mode)) mode = "custom";
        switch (mode) {
            case "solo": case "duo": case "trio": case "squad": case "custom": return mode;
            default: throw new IllegalArgumentException("Modalidade invalida: " + value);
        }
    }
}
