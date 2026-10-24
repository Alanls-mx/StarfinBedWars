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
        "shop.description-line-length",
        "bed-hologram.enabled", "bed-hologram.height", "bed-hologram.intact",
        "bed-hologram.destroyed", "purchase-sounds.shop", "purchase-sounds.upgrade",
        "purchase-sounds.volume", "purchase-sounds.pitch",
        "shop-npc.items-skin", "shop-npc.upgrades-skin",
        "bed-hologram.visibility", "bed-hologram.show-to-spectators",
        "bed-hologram.show-when-intact", "bed-hologram.show-when-destroyed",
        "bed-hologram.destroyed-duration-seconds",
        "generator-holograms.enabled", "generator-holograms.text.enabled",
        "generator-holograms.text.show-level", "generator-holograms.text.show-resource",
        "generator-holograms.text.show-countdown", "generator-holograms.text.update-interval-ticks",
        "generator-holograms.central-block.enabled", "generator-holograms.central-block.rotate",
        "generator-holograms.central-block.rotation-speed",
        "generator-holograms.central-block.floating.enabled",
        "generator-holograms.central-block.floating.amplitude",
        "generator-holograms.central-block.floating.period-ticks",
        "generator-holograms.orbit.enabled", "generator-holograms.orbit.icon-count",
        "generator-holograms.orbit.radius", "generator-holograms.orbit.height-offset",
        "generator-holograms.orbit.rotation-speed", "generator-holograms.orbit.update-interval-ticks",
        "generator-holograms.orbit.vertical-oscillation.enabled",
        "generator-holograms.orbit.vertical-oscillation.amplitude",
        "generator-holograms.resources.diamond.central-material",
        "generator-holograms.resources.diamond.orbit-material",
        "generator-holograms.resources.emerald.central-material",
        "generator-holograms.resources.emerald.orbit-material",
        "generator-holograms.performance.max-icons-per-generator",
        "generator-holograms.performance.pause-when-no-viewers",
        "generator-holograms.performance.activation-distance",
        "visuals.lobby-countdown.start-at", "visuals.lobby-countdown.title",
        "visuals.lobby-countdown.subtitle", "visuals.lobby-countdown.cancelled",
        "visuals.lobby-countdown.sound", "visuals.lobby-countdown.sound-enabled",
        "messages.player-joined", "messages.player-left", "messages.you-joined",
        "messages.reconnected", "messages.lobby-ready", "messages.lobby-countdown",
        "messages.spawn-preparing", "messages.spawn-countdown",
        "messages.countdown-cancelled", "messages.match-started", "messages.own-bed",
        "messages.boundary-height", "messages.boundary-edge",
        "messages.boundary-warning-cooldown-seconds",
        "messages.death.void", "messages.death.void-killer",
        "messages.death.fall", "messages.death.fall-killer",
        "messages.death.fire", "messages.death.lava", "messages.death.drowning",
        "messages.death.explosion", "messages.death.explosion-killer",
        "messages.death.projectile", "messages.death.projectile-killer",
        "messages.death.combat", "messages.death.combat-killer",
        "messages.death.generic", "messages.death.generic-killer",
        "messages.respawn-countdown", "messages.respawned",
        "messages.you-eliminated", "messages.start-tutorial",
        "visuals.death.title", "visuals.death.subtitle",
        "special-items.bridge-egg.width", "special-items.bridge-egg.thickness",
        "special-items.bridge-egg.max-blocks", "special-items.bridge-egg.max-ticks",
        "special-items.bridge-egg.decay-delay-seconds",
        "special-items.bridge-egg.decay-interval-ticks",
        "special-items.defender-knockback.horizontal",
        "special-items.defender-knockback.vertical",
        "special-items.defender-lifetime-seconds",
        "special-items.defender-persistent",
        "match-point.enabled", "match-point.after-minutes",
        "match-point.message", "match-point.dragon-height-offset",
        "match-point.dragon-angular-speed", "match-point.blocks-per-sweep",
        "match-point.dragon-damage-radius", "match-point.dragon-damage",
        "visuals.victory.celebration.enabled",
        "visuals.victory.celebration.fireworks-per-burst",
        "visuals.victory.celebration.interval-ticks",
        "visuals.victory.celebration.radius"
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
