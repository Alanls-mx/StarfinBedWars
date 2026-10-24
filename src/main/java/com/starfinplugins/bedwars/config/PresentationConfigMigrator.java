package com.starfinplugins.bedwars.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds presentation options to older config.yml files without changing existing values. */
public final class PresentationConfigMigrator {
    private static final String[] PATHS = {
        "tab.enabled", "tab.header", "tab.footer", "tab.team-prefixes",
        "tab.team-prefix", "tab.show-spectators", "tab.spectator-prefix",
        "scoreboard.enabled", "scoreboard.preset", "scoreboard.title",
        "scoreboard.footer", "scoreboard.eliminated-display", "scoreboard.own-marker",
        "scoreboard.team-line", "scoreboard.layouts.lobby",
        "scoreboard.layouts.in-game", "scoreboard.layouts.ending",
        "shop-npc.fallback-to-team-spawn", "shop-hologram.line-spacing",
        "bed-hologram.enabled", "bed-hologram.height", "bed-hologram.intact",
        "bed-hologram.destroyed", "purchase-sounds.shop", "purchase-sounds.upgrade",
        "purchase-sounds.volume", "purchase-sounds.pitch",
        "shop-npc.items-skin", "shop-npc.upgrades-skin"
    };

    private PresentationConfigMigrator() { }

    public static void ensure(JavaPlugin plugin) throws IOException {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (Exception error) {
            throw new IOException("Nao foi possivel carregar defaults de apresentacao.", error);
        }
        if (mergeFile(new File(plugin.getDataFolder(), "config.yml"), defaults)) {
            plugin.reloadConfig();
            plugin.getLogger().info("Opcoes de scoreboard/TAB adicionadas ao config.yml; backup preservado.");
        }
    }

    static boolean mergeFile(File file, YamlConfiguration defaults) throws IOException {
        YamlConfiguration current = new YamlConfiguration();
        try { current.load(file); }
        catch (Exception error) { throw new IOException("config.yml invalido para migracao.", error); }
        boolean changed = false;
        for (String path : PATHS) {
            if (current.contains(path)) continue;
            current.set(path, defaults.get(path));
            changed = true;
        }
        if ("&eStarfinPlugins".equals(current.getString("tab.footer"))) {
            current.set("tab.footer", "{footer}");
            changed = true;
        }
        if ("&b&lSTARFIN BEDWARS\\n&fstarfinplugins.com.br"
                .equals(current.getString("tab.header"))) {
            current.set("tab.header", "&b&lSTARFIN BEDWARS\\n{footer}");
            changed = true;
        }
        if ("{team_color}{team_initial} {team_name} {status}{own_marker}"
                .equals(current.getString("scoreboard.team-line"))) {
            current.set("scoreboard.team-line", defaults.getString("scoreboard.team-line"));
            changed = true;
        }
        if ("&eLOJA DE ITENS".equals(current.getString("shop-hologram.items-title"))) {
            current.set("shop-hologram.items-title", defaults.getString("shop-hologram.items-title"));
            changed = true;
        }
        if ("&eMELHORIAS".equals(current.getString("shop-hologram.upgrades-title"))) {
            current.set("shop-hologram.upgrades-title", defaults.getString("shop-hologram.upgrades-title"));
            changed = true;
        }
        if ("&7Clique para melhorar sua equipe".equals(current.getString("shop-hologram.upgrades-subtitle"))) {
            current.set("shop-hologram.upgrades-subtitle", defaults.getString("shop-hologram.upgrades-subtitle"));
            changed = true;
        }
        if (current.getDouble("shop-hologram.height") == 2.6D) {
            current.set("shop-hologram.height", defaults.getDouble("shop-hologram.height"));
            changed = true;
        }
        if (current.getInt("match.respawn-seconds") == 5) {
            current.set("match.respawn-seconds", 3);
            changed = true;
        }
        if ("IRON_CHESTPLATE".equals(current.getString("shop.category-icons.ARMOR"))) {
            current.set("shop.category-icons.ARMOR", "DIAMOND_CHESTPLATE");
            changed = true;
        }
        String chatFormat = current.getString("match-chat.format");
        if (chatFormat != null && (chatFormat.contains("Â»") || chatFormat.contains("»"))) {
            current.set("match-chat.format", chatFormat.replace("Â»", ">").replace("»", ">"));
            changed = true;
        }
        if (!changed) return false;
        byte[] original = Files.readAllBytes(file.toPath());
        File backup = new File(file.getParentFile(), "config.yml.pre-presentation.bak");
        if (!backup.exists()) Files.write(backup.toPath(), original);
        try { current.save(file); }
        catch (Exception error) {
            Files.write(file.toPath(), original);
            throw new IOException("Falha ao salvar as opcoes de apresentacao.", error);
        }
        return true;
    }
}
