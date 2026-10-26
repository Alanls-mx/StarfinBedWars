package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable purchase defaults. Existing purchases remain in their original scope on reload. */
public final class PurchaseSettings {
    public enum Scope { GLOBAL, PER_MODE }
    public final Scope kitDefault, abilityDefault;
    public final Set<String> modes;

    private PurchaseSettings(YamlConfiguration yaml) {
        kitDefault = scope(yaml.getString("purchase-settings.kits.default-scope", "PER_MODE"));
        abilityDefault = scope(yaml.getString("purchase-settings.abilities.default-scope", "PER_MODE"));
        Set<String> configured = new LinkedHashSet<String>();
        for (String mode : yaml.getStringList("purchase-settings.modes")) {
            String canonical = MatchMode.persisted(mode);
            if (canonical == null || !configured.add(canonical))
                throw new IllegalArgumentException("Modalidade duplicada ou vazia: " + mode);
        }
        if (configured.isEmpty()) throw new IllegalArgumentException("purchase-settings.modes vazio");
        modes = Collections.unmodifiableSet(configured);
    }

    public static PurchaseSettings load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "purchase-settings.yml");
        if (!file.exists()) plugin.saveResource("purchase-settings.yml", false);
        return parse(YamlConfiguration.loadConfiguration(file));
    }
    public static PurchaseSettings parse(YamlConfiguration yaml) { return new PurchaseSettings(yaml); }
    public static Scope scope(String value) {
        try { return Scope.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Escopo invalido: " + value); }
    }
    public static String purchaseKey(Scope scope, String mode) {
        String normalized = MatchMode.persisted(mode);
        if (normalized == null) throw new IllegalArgumentException("Modalidade ausente");
        return scope == Scope.GLOBAL ? "GLOBAL" : normalized;
    }
}
