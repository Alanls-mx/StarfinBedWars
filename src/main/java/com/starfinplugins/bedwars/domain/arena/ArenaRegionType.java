package com.starfinplugins.bedwars.domain.arena;

import java.util.Locale;

public enum ArenaRegionType {
    MAIN,
    PROTECTION,
    WAITING;

    public static ArenaRegionType parse(String value) {
        if (value == null) throw new IllegalArgumentException("Tipo de regiao ausente.");
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Tipo de regiao invalido: " + value, error);
        }
    }
}
