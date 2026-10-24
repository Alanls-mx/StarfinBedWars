package com.starfinplugins.bedwars.domain;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.ArenaValidator;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import com.starfinplugins.bedwars.domain.match.GameMatch;
import com.starfinplugins.bedwars.domain.match.MatchEndReason;
import com.starfinplugins.bedwars.domain.match.MatchState;
import com.starfinplugins.bedwars.domain.match.ParticipantState;
import java.io.File;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ArenaDomainTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validatesArenaAndAllowsCapacityAboveFour() throws Exception {
        ArenaDefinition arena = arena(8);
        assertEquals(16, arena.getMaximumPlayers());
        File template = temporaryFolder.newFolder("template");
        assertTrue(new File(template, "level.dat").createNewFile());
        assertTrue(ArenaValidator.validate(arena, template).isEmpty());
    }

    @Test
    public void rejectsMissingArenaRequirements() {
        ArenaDefinition arena = new ArenaDefinition("missing", "world");
        assertFalse(ArenaValidator.validate(arena, new File("missing-template")).isEmpty());
    }

    @Test
    public void requiresIndependentTeamRespawn() throws Exception {
        ArenaDefinition arena = arena(2);
        arena.getTeams().get("red").setRespawn(null);
        File template = temporaryFolder.newFolder("template-respawn");
        assertTrue(new File(template, "level.dat").createNewFile());
        assertTrue(ArenaValidator.validate(arena, template).contains(
            "Respawn ausente para a equipe red."));
    }

    @Test
    public void rejectsEditSpawnFromAnotherWorld() throws Exception {
        ArenaDefinition arena = arena(4);
        arena.setEditSpawn(position("other", 0.5, 70, 0.5));
        File template = temporaryFolder.newFolder("template-edit-spawn");
        assertTrue(new File(template, "level.dat").createNewFile());
        assertTrue(ArenaValidator.validate(arena, template).contains(
            "Localizacao de ponto de edicao pertence a outro mundo."));
    }

    @Test
    public void rejectsVoidAtOrAboveTheArenaCeiling() throws Exception {
        ArenaDefinition arena = arena(4);
        arena.setBounds(new ArenaRegion("world", -16, 0, -16, 16, 255, 16));
        arena.setCeilingHeight(64);
        arena.setVoidHeight(64);
        File template = temporaryFolder.newFolder("template-heights");
        assertTrue(new File(template, "level.dat").createNewFile());

        assertTrue(ArenaValidator.validate(arena, template).contains(
            "Altura do void deve ser menor que a altura do teto."));
    }

    @Test
    public void rejectsGeneratorAnchorOutsideItsRegion() throws Exception {
        ArenaDefinition arena = arena(4);
        arena.addGenerator(new ResourceGeneratorDefinition("iron_1", ResourceType.IRON,
            position("world", 10, 64, 10), "red", 1,
            new ArenaRegion("world", -2, 60, -2, 2, 70, 2)));
        File template = temporaryFolder.newFolder("template-generator-region");
        assertTrue(new File(template, "level.dat").createNewFile());

        assertTrue(ArenaValidator.validate(arena, template).contains(
            "Ponto do gerador iron_1 fora da regiao configurada."));
    }

    @Test
    public void allowsTeamEmeraldGenerator() {
        ResourceGeneratorDefinition generator = new ResourceGeneratorDefinition("emerald_1",
            ResourceType.EMERALD, position("world", 0.5, 64, 0.5), "red", 1);
        assertEquals("red", generator.getTeamId());
    }

    @Test
    public void normalizesRegionBoundsAndChecksInclusiveContainment() {
        ArenaRegion region = new ArenaRegion("world", 10, 80, 12, -10, 20, -12);
        assertEquals(-10, region.getMinimumX());
        assertEquals(10, region.getMaximumX());
        assertTrue(region.contains(position("world", -10, 20, -12)));
        assertTrue(region.contains(position("world", 10, 80, 12)));
        assertFalse(region.contains(position("other", 0, 40, 0)));
        assertFalse(region.contains(position("world", 11.0, 40, 0)));
    }

    @Test
    public void balancesPlayersAndGuardsMatchTransitions() {
        ArenaDefinition arena = arena(8);
        GameMatch match = new GameMatch(arena);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        assertTrue(match.addParticipant(first));
        assertTrue(match.addParticipant(second));
        assertTrue(match.addParticipant(third));
        assertEquals(2, match.getTeam("red").getMembers().size());
        assertEquals(1, match.getTeam("blue").getMembers().size());
        match.transition(MatchState.STARTING);
        assertTrue(match.removeWaitingParticipant(third));
        assertTrue(match.addParticipant(UUID.randomUUID()));
        match.transition(MatchState.IN_GAME);
        assertTrue(match.setParticipantState(first, ParticipantState.ELIMINATED));
        assertTrue(match.destroyBed("red"));
        assertFalse(match.destroyBed("red"));
        assertTrue(match.finish(MatchEndReason.VICTORY, "blue"));
        assertEquals(MatchState.ENDING, match.getState());
        assertNotNull(match.getEndedAt());
        assertFalse(match.finish(MatchEndReason.DRAW, null));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsIllegalMatchTransition() {
        new GameMatch(arena(4)).transition(MatchState.IN_GAME);
    }

    @Test
    public void teamUpgradesAndTrapQueueAreMatchScoped() {
        TeamDefinition definition = new TeamDefinition("red", "Vermelho", "c", 4);
        com.starfinplugins.bedwars.domain.match.GameTeam team =
            new com.starfinplugins.bedwars.domain.match.GameTeam(definition);
        assertTrue(team.upgrade(TeamUpgradeType.SHARPNESS, 1));
        assertFalse(team.upgrade(TeamUpgradeType.SHARPNESS, 1));
        assertEquals(1, team.getUpgradeLevel(TeamUpgradeType.SHARPNESS));
        assertTrue(team.addTrap(TeamTrapType.ALARM, 1));
        assertFalse(team.addTrap(TeamTrapType.BLINDNESS, 1));
        assertEquals(TeamTrapType.ALARM, team.consumeTrap());
        assertEquals(null, team.consumeTrap());
    }

    private ArenaDefinition arena(int capacity) {
        ArenaDefinition arena = new ArenaDefinition("test", "world");
        arena.setRegion(ArenaRegionType.MAIN, new ArenaRegion("world", -16, 0, -16, 16, 127, 16));
        arena.setLobby(position("world", 0, 64, 0));
        arena.setMinimumPlayers(2);
        TeamDefinition red = new TeamDefinition("red", "Vermelho", "c", capacity);
        red.setSpawn(position("world", 4, 64, 0));
        red.setRespawn(position("world", 4, 64, 2));
        red.setBed(position("world", 5.5, 64, 0.5));
        TeamDefinition blue = new TeamDefinition("blue", "Azul", "9", capacity);
        blue.setSpawn(position("world", -4, 64, 0));
        blue.setRespawn(position("world", -4, 64, 2));
        blue.setBed(position("world", -5.5, 64, 0.5));
        arena.addTeam(red);
        arena.addTeam(blue);
        return arena;
    }

    private Position position(String world, double x, double y, double z) {
        return new Position(world, x, y, z, 0.0f, 0.0f);
    }
}
