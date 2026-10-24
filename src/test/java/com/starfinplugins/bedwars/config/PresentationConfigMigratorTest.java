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
        assertEquals("&cVocê não pode destruir sua própria cama!",
            updated.getString("messages.own-bed"));
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
