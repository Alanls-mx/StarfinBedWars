package com.starfinplugins.bedwars.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PresentationConfigMigratorTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void addsLayoutsAndKeepsConfiguredTitleWithOriginalBackup() throws Exception {
        File config = folder.newFile("config.yml");
        String original = "config-version: 1\nscoreboard:\n  title: '&aMinha arena'\n";
        Files.write(config.toPath(), original.getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals("&aMinha arena", updated.getString("scoreboard.title"));
        assertTrue(updated.getStringList("scoreboard.layouts.in-game").contains("{teams}"));
        assertEquals("OWN_TEAM", updated.getString("bed-hologram.visibility"));
        assertEquals(4, updated.getInt("generator-holograms.orbit.icon-count"));
        assertEquals(3, updated.getInt("special-items.bridge-egg.width"));
        assertEquals(2, updated.getInt("special-items.bridge-egg.thickness"));
        assertEquals(480, updated.getInt("special-items.bridge-egg.max-blocks"));
        assertEquals(5, updated.getInt("special-items.bridge-egg.decay-delay-seconds"));
        assertEquals(3, updated.getInt("special-items.bridge-egg.decay-interval-ticks"));
        assertTrue(updated.getBoolean("special-items.defender-persistent"));
        assertTrue(updated.getBoolean("match-point.enabled"));
        assertEquals(30, updated.getInt("match-point.after-minutes"));
        assertEquals(40, updated.getInt("match-point.dragon-patrol-spacing"));
        assertEquals(40, updated.getInt("match-point.dragon-blast-interval-ticks"));
        assertEquals(3, updated.getInt("match-point.dragon-blast-radius"));
        assertEquals(1, updated.getInt("match-point.dragon-blast-depth"));
        assertEquals(8, updated.getInt("match-point.dragon-blast-blocks"));
        assertEquals(5, updated.getInt("match-point.demolition.partial-minutes"));
        assertEquals(80, updated.getInt("match-point.demolition.partial-percent"));
        assertEquals(8, updated.getInt("match-point.demolition.complete-minutes"));
        assertEquals(7000, updated.getInt("match-point.demolition.survey-positions-per-tick"));
        assertEquals(220, updated.getInt("match-point.demolition.max-blocks-per-tick"));
        assertTrue(updated.getBoolean("match-point.lightning.enabled"));
        assertEquals(60, updated.getInt("match-point.lightning.interval-ticks"));
        assertEquals(3, updated.getInt("match-point.lightning.radius"));
        assertEquals(24, updated.getInt("match-point.lightning.max-blocks-per-strike"));
        assertEquals(5.0D, updated.getDouble("match-point.dragon-blast-min-movement"), 0.0D);
        assertTrue(updated.getBoolean("match-point.bossbar-enabled"));
        assertEquals(9, updated.getInt("match.victory-celebration-seconds"));
        assertTrue(updated.getBoolean("visuals.victory.celebration.enabled"));
        assertEquals(4, updated.getInt("visuals.victory.celebration.fireworks-per-burst"));
        assertEquals(0.75D, updated.getDouble(
            "special-items.defender-knockback.horizontal"), 0.0D);
        assertEquals(0.85D, updated.getDouble(
            "special-items.defender-knockback.vertical"), 0.0D);
        assertEquals("&cVocê não pode destruir sua própria cama!",
            updated.getString("messages.own-bed"));
        assertEquals(28, updated.getInt("shop.description-line-length"));
        assertEquals(4, updated.getInt("messages.boundary-warning-cooldown-seconds"));
        assertEquals("&cVocê atingiu o limite de altura desta arena!",
            updated.getString("messages.boundary-height"));
        assertEquals("&cVocê chegou ao limite da arena! Não é possível sair do mapa.",
            updated.getString("messages.boundary-edge"));
        assertEquals("&e&lINICIANDO", updated.getString("visuals.lobby-countdown.title"));
        assertEquals(original, new String(Files.readAllBytes(
            new File(folder.getRoot(), "config.yml.pre-presentation.bak").toPath()),
            StandardCharsets.UTF_8));
        assertFalse(PresentationConfigMigrator.mergeFile(config, defaults));
    }

    @Test
    public void preservesCustomMatchMessagesWhenMigrating() throws Exception {
        File config = folder.newFile("custom-messages.yml");
        Files.write(config.toPath(), ("messages:\n"
            + "  own-bed: '&4Minha mensagem personalizada'\n"
            + "  death:\n    void: '&7{player} sumiu.'\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals("&4Minha mensagem personalizada", updated.getString("messages.own-bed"));
        assertEquals("&7{player} sumiu.", updated.getString("messages.death.void"));
        assertEquals("&eA partida comeca em &6{time} &esegundos!",
            updated.getString("messages.lobby-countdown"));
    }

    @Test
    public void preservesCustomDragonBlastsAndVictoryTime() throws Exception {
        File config = folder.newFile("custom-dragon-blasts.yml");
        Files.write(config.toPath(), ("match:\n"
            + "  victory-celebration-seconds: 12\n"
            + "match-point:\n"
            + "  dragon-blast-blocks: 6\n"
            + "  dragon-blast-interval-ticks: 60\n"
            + "  demolition:\n"
            + "    complete-minutes: 10\n"
            + "    partial-percent: 70\n"
            + "  lightning:\n"
            + "    enabled: false\n"
            + "    max-blocks-per-strike: 16\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals(12, updated.getInt("match.victory-celebration-seconds"));
        assertEquals(6, updated.getInt("match-point.dragon-blast-blocks"));
        assertEquals(60, updated.getInt("match-point.dragon-blast-interval-ticks"));
        assertEquals(3, updated.getInt("match-point.dragon-blast-radius"));
        assertEquals(10, updated.getInt("match-point.demolition.complete-minutes"));
        assertEquals(5, updated.getInt("match-point.demolition.partial-minutes"));
        assertEquals(70, updated.getInt("match-point.demolition.partial-percent"));
        assertFalse(updated.getBoolean("match-point.lightning.enabled"));
        assertEquals(16, updated.getInt("match-point.lightning.max-blocks-per-strike"));
        assertEquals(60, updated.getInt("match-point.lightning.interval-ticks"));
        assertTrue(updated.getBoolean("match-point.bossbar-enabled"));
        assertFalse(PresentationConfigMigrator.mergeFile(config, defaults));
    }

    @Test
    public void preservesCustomBoundaryMessagesAndLoreLength() throws Exception {
        File config = folder.newFile("custom-boundaries.yml");
        Files.write(config.toPath(), ("shop:\n"
            + "  description-line-length: 22\n"
            + "messages:\n"
            + "  boundary-edge: '&cPare agora!'\n"
            + "  boundary-warning-cooldown-seconds: 7\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals(22, updated.getInt("shop.description-line-length"));
        assertEquals(7, updated.getInt("messages.boundary-warning-cooldown-seconds"));
        assertEquals("&cPare agora!", updated.getString("messages.boundary-edge"));
        assertEquals("&cVocê atingiu o limite de altura desta arena!",
            updated.getString("messages.boundary-height"));
        assertFalse(PresentationConfigMigrator.mergeFile(config, defaults));
    }

    @Test
    public void replacesOnlyLegacyTabFooterWithSharedBrand() throws Exception {
        File config = folder.newFile("legacy.yml");
        Files.write(config.toPath(), ("tab:\n  header: '&b&lSTARFIN BEDWARS\\n&fstarfinplugins.com.br'\n"
            + "  footer: '&eStarfinPlugins'\n"
            + "scoreboard:\n  footer: '&ewww.meuserver.com.br'\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals("{footer}", updated.getString("tab.footer"));
        assertEquals("&b&lSTARFIN BEDWARS\\n{footer}", updated.getString("tab.header"));
        assertEquals("&ewww.meuserver.com.br", updated.getString("scoreboard.footer"));
    }

    @Test
    public void preservesCustomBridgeAndDefenderBalancing() throws Exception {
        File config = folder.newFile("custom-specials.yml");
        Files.write(config.toPath(), ("special-items:\n"
            + "  bridge-egg:\n    width: 5\n    thickness: 1\n    decay-delay-seconds: 8\n"
            + "  defender-knockback:\n    horizontal: 0.90\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals(5, updated.getInt("special-items.bridge-egg.width"));
        assertEquals(1, updated.getInt("special-items.bridge-egg.thickness"));
        assertEquals(8, updated.getInt("special-items.bridge-egg.decay-delay-seconds"));
        assertEquals(3, updated.getInt("special-items.bridge-egg.decay-interval-ticks"));
        assertEquals(0.90D, updated.getDouble(
            "special-items.defender-knockback.horizontal"), 0.0D);
        assertEquals(0.85D, updated.getDouble(
            "special-items.defender-knockback.vertical"), 0.0D);
        assertFalse(PresentationConfigMigrator.mergeFile(config, defaults));
    }

    @Test
    public void preservesExistingBedTextAndGeneratorSettings() throws Exception {
        File config = folder.newFile("custom.yml");
        Files.write(config.toPath(), ("bed-hologram:\n  intact: '&aMinha cama'\n"
            + "  destroyed: '&4Perdida'\n  height: 1.8\n"
            + "generator-holograms:\n  orbit:\n    icon-count: 2\n")
            .getBytes(StandardCharsets.UTF_8));
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertTrue(PresentationConfigMigrator.mergeFile(config, defaults));
        YamlConfiguration updated = YamlConfiguration.loadConfiguration(config);
        assertEquals("&aMinha cama", updated.getString("bed-hologram.intact"));
        assertEquals("&4Perdida", updated.getString("bed-hologram.destroyed"));
        assertEquals(1.8D, updated.getDouble("bed-hologram.height"), 0.0D);
        assertEquals(2, updated.getInt("generator-holograms.orbit.icon-count"));
        assertEquals(6.0D, updated.getDouble("generator-holograms.orbit.rotation-speed"), 0.0D);
    }
}
