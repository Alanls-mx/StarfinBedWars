package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaGameplayRules;
import com.starfinplugins.bedwars.domain.arena.ArenaBoundsMode;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.arena.SpawnStructureDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.setup.SpawnPlatformLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArenaRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void savesLargerTeamSpawnRoom() throws Exception {
        ArenaRepository repository = new ArenaRepository(temporaryFolder.getRoot());
        ArenaDefinition arena = new ArenaDefinition("large", "template");
        TeamDefinition team = new TeamDefinition("red", "Vermelho", "c", 8);
        List<SpawnStructureDefinition.BlockPosition> blocks =
            new ArrayList<SpawnStructureDefinition.BlockPosition>();
        for (SpawnPlatformLayout.BlockCoordinate coordinate :
                SpawnPlatformLayout.forTeamCapacity(0, 64, 0, 8).getBlocks())
            blocks.add(new SpawnStructureDefinition.BlockPosition(coordinate.getX(),
                coordinate.getY(), coordinate.getZ()));
        team.setSpawnStructure(new SpawnStructureDefinition(95, 14, blocks));
        arena.addTeam(team);
        repository.save(arena);
        assertEquals(blocks.size(), repository.loadAll().get(0).getTeams().get("red")
            .getSpawnStructure().getBlocks().size());
        assertTrue(blocks.size() > 256);
    }

    @Test
    public void savesAndLoadsArenaDefinition() throws Exception {
        ArenaRepository repository = new ArenaRepository(temporaryFolder.getRoot());
        ArenaDefinition arena = new ArenaDefinition("duo", "bedwars_template");
        arena.setDisplayName("Duplas");
        arena.setGroup("duplas");
        arena.setMinimumPlayers(4);
        arena.setCountdownSeconds(20);
        arena.setEnabled(true);
        arena.setGameplayRules(new ArenaGameplayRules(true, false, 4,
            new java.util.LinkedHashSet<String>(Arrays.asList("default", "builder")),
            new java.util.LinkedHashSet<String>(Arrays.asList("guardian"))));
        arena.setLobby(position("bedwars_template", 0.5, 70, 0.5));
        TeamDefinition team = new TeamDefinition("red", "Vermelho", "c", 6);
        team.setAppearance("Vermelho", "c", 14);
        team.setSpawn(position("bedwars_template", 10.5, 65, 2.5));
        team.setRespawn(position("bedwars_template", 11.5, 65, 2.5));
        team.setBed(position("bedwars_template", 12.5, 64, 2.5));
        team.setItemShop(position("bedwars_template", 13.5, 65, 2.5));
        team.setUpgradeShop(position("bedwars_template", 14.5, 65, 2.5));
        team.setSpawnStructure(new SpawnStructureDefinition(95, 14, Arrays.asList(
            new SpawnStructureDefinition.BlockPosition(9, 64, 1),
            new SpawnStructureDefinition.BlockPosition(10, 68, 2))));
        arena.addTeam(team);
        arena.setItemShop(position("bedwars_template", 1.5, 70, 1.5));
        arena.setUpgradeShop(position("bedwars_template", 2.5, 70, 1.5));
        arena.setEditSpawn(position("bedwars_template", 3.5, 72, 1.5));
        arena.setRegion(ArenaRegionType.MAIN,
            new ArenaRegion("bedwars_template", -32, 0, -32, 32, 127, 32));
        arena.setBounds(new ArenaRegion("bedwars_template", -32, 0, -32, 32, 255, 32));
        arena.setBoundsConfiguration(position("bedwars_template", 0.5, 64, 0.5),
            ArenaBoundsMode.AUTOMATIC, 280, 240, 50, 40);
        arena.setCeilingHeight(120);
        arena.setVoidHeight(5);
        ArenaRegion generatorRegion = new ArenaRegion("bedwars_template", 8, 62, 0, 11, 65, 4);
        arena.addGenerator(new ResourceGeneratorDefinition("iron_1", ResourceType.IRON,
            position("bedwars_template", 10.5, 64, 3.5), "red", 2, generatorRegion));

        repository.save(arena);

        List<ArenaDefinition> loaded = repository.loadAll();
        assertEquals(1, loaded.size());
        ArenaDefinition copy = loaded.get(0);
        assertEquals("duo", copy.getId());
        assertEquals("Duplas", copy.getDisplayName());
        assertEquals("duplas", copy.getGroup());
        assertEquals(4, copy.getMinimumPlayers());
        assertTrue(copy.isEnabled());
        assertEquals(6, copy.getTeams().get("red").getCapacity());
        assertEquals(14, copy.getTeams().get("red").getColorData());
        assertEquals(10.5, copy.getTeams().get("red").getSpawn().getX(), 0.0);
        assertEquals(37.5f, copy.getTeams().get("red").getSpawn().getYaw(), 0.0f);
        assertEquals(11.5, copy.getTeams().get("red").getRespawn().getX(), 0.0);
        assertEquals(12.5, copy.getTeams().get("red").getBed().getX(), 0.0);
        assertEquals(13.5, copy.getTeams().get("red").getItemShop().getX(), 0.0);
        assertEquals(14.5, copy.getTeams().get("red").getUpgradeShop().getX(), 0.0);
        SpawnStructureDefinition structure = copy.getTeams().get("red").getSpawnStructure();
        assertEquals(95, structure.getBlockTypeId());
        assertEquals(14, structure.getBlockData());
        assertEquals(2, structure.getBlocks().size());
        assertEquals(9, structure.getBlocks().get(0).getX());
        assertEquals(68, structure.getBlocks().get(1).getY());
        assertEquals(1.5, copy.getItemShop().getX(), 0.0);
        assertEquals(2.5, copy.getUpgradeShop().getX(), 0.0);
        assertEquals(3.5, copy.getEditSpawn().getX(), 0.0);
        assertEquals(ResourceType.IRON, copy.getGenerators().get("iron_1").getResource());
        assertEquals("red", copy.getGenerators().get("iron_1").getTeamId());
        assertEquals(2, copy.getGenerators().get("iron_1").getStartingLevel());
        assertEquals(-32, copy.getRegion(ArenaRegionType.MAIN).getMinimumX());
        assertEquals(32, copy.getRegion(ArenaRegionType.MAIN).getMaximumZ());
        assertEquals(120, copy.getCeilingHeight());
        assertEquals(5, copy.getVoidHeight());
        assertEquals(255, copy.getBounds().getMaximumY());
        assertEquals(ArenaBoundsMode.AUTOMATIC, copy.getBoundsMode());
        assertEquals(280, copy.getBoundsWidth());
        assertEquals(240, copy.getBoundsDepth());
        assertEquals(50, copy.getBoundsUpHeight());
        assertEquals(40, copy.getBoundsDownDepth());
        assertEquals(0.5, copy.getBoundsCenter().getX(), 0.0);
        assertEquals(8, copy.getGenerators().get("iron_1").getRegion().getMinimumX());
        assertEquals(Boolean.TRUE, copy.getGameplayRules().generatorSplit);
        assertEquals(Boolean.FALSE, copy.getGameplayRules().antiAfk);
        assertEquals(Integer.valueOf(4), copy.getGameplayRules().respawnSeconds);
        assertTrue(copy.getGameplayRules().kits.contains("builder"));
        assertTrue(copy.getGameplayRules().abilities.contains("guardian"));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(
            new File(temporaryFolder.getRoot(), "arenas"), "duo.yml"));
        assertEquals(9, saved.getInt("version"));
        assertEquals("duplas", saved.getString("group"));
        assertFalse(repository.getTemplateDirectory("duo").exists());
    }

    @Test
    public void readsVersionTwoArenaWithoutEditSpawn() throws Exception {
        File data = temporaryFolder.newFolder("legacy-data");
        File arenaDirectory = new File(data, "arenas");
        assertTrue(arenaDirectory.mkdirs());
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("version", 2);
        legacy.set("id", "legacy");
        legacy.set("source-world", "old_map");
        legacy.set("name", "Legacy");
        legacy.save(new File(arenaDirectory, "legacy.yml"));

        ArenaDefinition loaded = new ArenaRepository(data).loadAll().get(0);

        assertEquals("legacy", loaded.getId());
        assertEquals("default", loaded.getGroup());
        assertEquals(null, loaded.getEditSpawn());
        assertEquals(null, loaded.getGameplayRules().generatorSplit);
    }

    @Test
    public void restoresAnExactArenaSnapshot() throws Exception {
        ArenaRepository repository = new ArenaRepository(temporaryFolder.getRoot());
        ArenaDefinition arena = new ArenaDefinition("undo", "undo_map");
        repository.save(arena);
        String snapshot = repository.snapshot(arena.getId());

        arena.setDisplayName("changed");
        repository.save(arena);
        ArenaDefinition restored = repository.restoreSnapshot(arena.getId(), arena.getSourceWorld(), snapshot);

        assertEquals("undo", restored.getDisplayName());
        assertEquals(snapshot, repository.snapshot(arena.getId()));
    }

    @Test
    public void recoversMissingArenaYamlFromLatestValidatedBackupOnRestart() throws Exception {
        ArenaRepository firstBoot = new ArenaRepository(temporaryFolder.getRoot());
        ArenaDefinition arena = new ArenaDefinition("durable", "source_world");
        firstBoot.save(arena);
        arena.setDisplayName("Mapa salvo");
        firstBoot.save(arena);
        File primary = new File(new File(temporaryFolder.getRoot(), "arenas"), "durable.yml");
        File backup = new File(new File(temporaryFolder.getRoot(), "arenas"), "durable.yml.bak");
        assertTrue(primary.isFile());
        assertTrue(backup.isFile());
        assertTrue(primary.delete());

        ArenaRepository secondBoot = new ArenaRepository(temporaryFolder.getRoot());
        List<ArenaDefinition> loaded = secondBoot.loadAll();

        assertEquals(1, loaded.size());
        assertEquals("Mapa salvo", loaded.get(0).getDisplayName());
        assertTrue(primary.isFile());
        assertEquals(new String(Files.readAllBytes(primary.toPath()), "UTF-8"),
            new String(Files.readAllBytes(backup.toPath()), "UTF-8"));
    }

    @Test
    public void doesNotOverwriteExistingYamlWithBackup() throws Exception {
        ArenaRepository repository = new ArenaRepository(temporaryFolder.getRoot());
        ArenaDefinition arena = new ArenaDefinition("existing", "source_world");
        repository.save(arena);
        File primary = new File(new File(temporaryFolder.getRoot(), "arenas"), "existing.yml");
        Files.write(primary.toPath(), "invalid: [".getBytes("UTF-8"));

        try {
            new ArenaRepository(temporaryFolder.getRoot()).loadAll();
            org.junit.Assert.fail("YAML principal invalido deve ser preservado para diagnostico.");
        } catch (java.io.IOException expected) {
            assertTrue(primary.isFile());
            assertEquals("invalid: [", new String(Files.readAllBytes(primary.toPath()), "UTF-8"));
        }
    }

    @Test
    public void createsBackupForArenaSavedByPreviousPluginVersion() throws Exception {
        File directory = new File(temporaryFolder.getRoot(), "arenas");
        assertTrue(directory.mkdirs());
        File primary = new File(directory, "legacyfile.yml");
        Files.write(primary.toPath(), ("version: 7\nid: legacyfile\n"
            + "source-world: world\nname: Mapa Antigo\n").getBytes("UTF-8"));

        assertEquals(1, new ArenaRepository(temporaryFolder.getRoot()).loadAll().size());
        assertTrue(new File(directory, "legacyfile.yml.bak").isFile());
    }

    private Position position(String world, double x, double y, double z) {
        return new Position(world, x, y, z, 37.5f, -12.5f);
    }
}
