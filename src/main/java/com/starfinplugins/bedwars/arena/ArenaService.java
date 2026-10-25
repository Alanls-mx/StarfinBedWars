package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaBoundsMode;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.ArenaValidator;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.arena.SpawnStructureDefinition;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import com.starfinplugins.bedwars.match.MatchEngineService;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.world.WorldManagementService;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ArenaService implements ManagedService {
    private final Plugin plugin;
    private final Map<String, ArenaDefinition> arenas = new LinkedHashMap<String, ArenaDefinition>();
    private ArenaRepository repository;
    private WorldInstanceManager worlds;
    private MatchEngineService matchEngine;
    private WorldManagementService worldManagement;
    private long maximumRegionBlocks = 25000000L;
    private final java.util.Set<String> editingArenas = new java.util.HashSet<String>();

    public ArenaService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "arenas"; }

    @Override
    public void start() throws Exception {
        repository = new ArenaRepository(plugin.getDataFolder());
        worlds = new WorldInstanceManager(plugin, worldManagement);
        List<ArenaDefinition> loaded = repository.loadAll();
        for (String id : repository.getRecoveredIds()) {
            plugin.getLogger().warning("Arquivo YAML da arena " + id
                + " estava ausente e foi restaurado do backup validado.");
        }
        for (ArenaDefinition arena : loaded) {
            if (arenas.put(arena.getId(), arena) != null) {
                throw new IOException("Identificador de arena duplicado: " + arena.getId());
            }
            List<String> errors = validate(arena);
            if (!errors.isEmpty() && arena.isEnabled()) {
                arena.setEnabled(false);
                plugin.getLogger().warning("Arena " + arena.getId()
                    + " foi desativada por configuracao invalida: " + join(errors));
            }
        }
        plugin.getLogger().info("Arenas carregadas: " + arenas.size());
    }

    @Override
    public void stop() {
        arenas.clear();
        editingArenas.clear();
    }

    public Collection<ArenaDefinition> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public ArenaDefinition getArena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public String snapshot(ArenaDefinition arena) throws IOException {
        requireEditable(arena);
        return repository.snapshot(arena.getId());
    }

    /** Read-only published/draft metadata for network discovery; safe during a match. */
    public String getStoredConfiguration(ArenaDefinition arena) throws IOException {
        if (arena == null || arenas.get(arena.getId()) != arena) {
            throw new IllegalArgumentException("Arena nao gerenciada.");
        }
        return repository.snapshot(arena.getId());
    }

    public ArenaDefinition restoreSnapshot(String arenaId, String snapshot) throws IOException {
        ArenaDefinition current = getArena(arenaId);
        if (current == null || !editingArenas.contains(current.getId())) {
            throw new IllegalStateException("A arena nao esta reservada para edicao.");
        }
        ArenaDefinition restored = repository.restoreSnapshot(current.getId(),
            current.getSourceWorld(), snapshot);
        arenas.put(current.getId(), restored);
        return restored;
    }

    public WorldInstanceManager getWorlds() {
        if (worlds == null) {
            throw new IllegalStateException("Servico de arenas nao inicializado.");
        }
        return worlds;
    }

    public void setMatchEngine(MatchEngineService matchEngine) {
        this.matchEngine = matchEngine;
    }

    public void setWorldManagement(WorldManagementService worldManagement) {
        this.worldManagement = worldManagement;
    }

    public void setMaximumRegionBlocks(long maximumRegionBlocks) {
        if (maximumRegionBlocks < 1L) throw new IllegalArgumentException("Limite de regiao invalido.");
        this.maximumRegionBlocks = maximumRegionBlocks;
    }

    public long getMaximumRegionBlocks() {
        return maximumRegionBlocks;
    }

    public boolean isEditing(String arenaId) {
        return arenaId != null && editingArenas.contains(arenaId);
    }

    public void reserveEditing(ArenaDefinition arena) {
        if (arena == null || arenas.get(arena.getId()) != arena
                || matchEngine != null && matchEngine.isArenaActive(arena.getId())
                || !editingArenas.add(arena.getId())) {
            throw new IllegalStateException("Arena ausente, em partida ou ja em modo de edicao.");
        }
    }

    public void releaseEditing(String arenaId) {
        if (arenaId != null) editingArenas.remove(arenaId);
    }

    public void setRegion(ArenaDefinition arena, ArenaRegionType type, ArenaRegion region)
            throws IOException {
        requireEditable(arena);
        if (region == null || !arena.getSourceWorld().equals(region.getWorld())) {
            throw new IllegalArgumentException("Regiao deve pertencer ao mundo-fonte da arena.");
        }
        if (region.getVolume() > maximumRegionBlocks) {
            throw new IllegalArgumentException("Regiao excede o limite de "
                + maximumRegionBlocks + " blocos.");
        }
        ArenaRegion previous = arena.setRegion(type, region);
        try {
            repository.save(arena);
        } catch (IOException error) {
            if (previous == null) arena.removeRegion(type);
            else arena.setRegion(type, previous);
            throw error;
        }
    }

    public void setEditSpawn(ArenaDefinition arena, Position position) throws IOException {
        requireEditable(arena);
        if (position == null || !arena.getSourceWorld().equals(position.getWorld())) {
            throw new IllegalArgumentException("Ponto de edicao deve pertencer ao mundo-fonte da arena.");
        }
        Position previous = arena.getEditSpawn();
        arena.setEditSpawn(position);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.setEditSpawn(previous);
            throw error;
        }
    }

    public void setBounds(ArenaDefinition arena, ArenaRegion bounds, int ceilingHeight, int voidHeight)
            throws IOException {
        requireEditable(arena);
        if (bounds == null || !arena.getSourceWorld().equals(bounds.getWorld())
                || bounds.getVolume() > maximumRegionBlocks
                || bounds.getMinimumY() != 0 || bounds.getMaximumY() != 255
                || ceilingHeight < 1 || ceilingHeight > 255 || voidHeight < 0
                || voidHeight >= ceilingHeight) {
            throw new IllegalArgumentException("Limites ou alturas da arena invalidos.");
        }
        ArenaRegion previousBounds = arena.getBounds();
        int previousCeiling = arena.getCeilingHeight();
        int previousVoid = arena.getVoidHeight();
        arena.setBounds(bounds);
        arena.setCeilingHeight(ceilingHeight);
        arena.setVoidHeight(voidHeight);
        saveOrRestore(arena, new Runnable() {
            @Override public void run() {
                arena.setBounds(previousBounds);
                arena.setCeilingHeight(previousCeiling);
                arena.setVoidHeight(previousVoid);
            }
        });
    }

    public void configureBounds(ArenaDefinition arena, ArenaRegion mainRegion, ArenaRegion bounds,
                                Position center, ArenaBoundsMode mode, int width, int depth,
                                int upHeight, int downDepth, int ceilingHeight, int voidHeight)
            throws IOException {
        requireEditable(arena);
        if (mainRegion == null || bounds == null
                || !arena.getSourceWorld().equals(mainRegion.getWorld())
                || !arena.getSourceWorld().equals(bounds.getWorld())
                || bounds.getMinimumX() > mainRegion.getMinimumX()
                || bounds.getMaximumX() < mainRegion.getMaximumX()
                || bounds.getMinimumZ() > mainRegion.getMinimumZ()
                || bounds.getMaximumZ() < mainRegion.getMaximumZ()
                || mainRegion.getVolume() > maximumRegionBlocks
                || bounds.getMinimumY() != 0 || bounds.getMaximumY() != 255
                || mode == null || width < 0 || depth < 0 || upHeight < 0 || downDepth < 0
                || mode == ArenaBoundsMode.AUTOMATIC
                    && (center == null || !arena.getSourceWorld().equals(center.getWorld())
                        || width < 1 || depth < 1 || upHeight < 0 || downDepth < 0)
                || ceilingHeight < 1 || ceilingHeight > 255 || voidHeight < 0
                || voidHeight >= ceilingHeight) {
            throw new IllegalArgumentException("Configuracao de limites da arena invalida.");
        }
        ArenaRegion previousMain = arena.getRegion(ArenaRegionType.MAIN);
        ArenaRegion previousBounds = arena.getBounds();
        Position previousCenter = arena.getBoundsCenter();
        ArenaBoundsMode previousMode = arena.getBoundsMode();
        int previousWidth = arena.getBoundsWidth();
        int previousDepth = arena.getBoundsDepth();
        int previousUp = arena.getBoundsUpHeight();
        int previousDown = arena.getBoundsDownDepth();
        int previousCeiling = arena.getCeilingHeight();
        int previousVoid = arena.getVoidHeight();
        arena.setRegion(ArenaRegionType.MAIN, mainRegion);
        arena.setBounds(bounds);
        arena.setBoundsConfiguration(center, mode, width, depth, upHeight, downDepth);
        arena.setCeilingHeight(ceilingHeight);
        arena.setVoidHeight(voidHeight);
        saveOrRestore(arena, new Runnable() {
            @Override public void run() {
                if (previousMain == null) arena.removeRegion(ArenaRegionType.MAIN);
                else arena.setRegion(ArenaRegionType.MAIN, previousMain);
                if (previousBounds == null) arena.setBounds(null);
                else arena.setBounds(previousBounds);
                arena.setBoundsConfiguration(previousCenter, previousMode, previousWidth,
                    previousDepth, previousUp, previousDown);
                arena.setCeilingHeight(previousCeiling);
                arena.setVoidHeight(previousVoid);
            }
        });
    }

    public void setTeamAppearance(ArenaDefinition arena, String teamId, String name,
                                  String color, int colorData) throws IOException {
        requireEditable(arena);
        TeamDefinition team = team(arena, teamId);
        if (team == null) throw new IllegalArgumentException("Equipe nao encontrada.");
        String previousName = team.getDisplayName();
        String previousColor = team.getColor();
        int previousData = team.getColorData();
        team.setAppearance(name, color, colorData);
        saveOrRestore(arena, new Runnable() {
            @Override public void run() { team.setAppearance(previousName, previousColor, previousData); }
        });
    }

    public void setTeamSpawnStructure(ArenaDefinition arena, String teamId,
                                      SpawnStructureDefinition structure) throws IOException {
        requireEditable(arena);
        TeamDefinition team = team(arena, teamId);
        if (team == null) throw new IllegalArgumentException("Equipe nao encontrada.");
        SpawnStructureDefinition previous = team.getSpawnStructure();
        team.setSpawnStructure(structure);
        saveOrRestore(arena, new Runnable() {
            @Override public void run() { team.setSpawnStructure(previous); }
        });
    }

    public void replaceTeams(ArenaDefinition arena, List<TeamDefinition> replacements)
            throws IOException {
        requireEditable(arena);
        Map<String, TeamDefinition> previous = arena.replaceTeams(replacements);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.replaceTeams(new java.util.ArrayList<TeamDefinition>(previous.values()));
            throw error;
        }
    }

    public void clearRegion(ArenaDefinition arena, ArenaRegionType type) throws IOException {
        requireEditable(arena);
        ArenaRegion previous = arena.removeRegion(type);
        if (previous == null) throw new IllegalArgumentException("Regiao nao configurada.");
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.setRegion(type, previous);
            throw error;
        }
    }

    public World loadSourceWorld(ArenaDefinition arena) {
        if (arena == null) throw new IllegalArgumentException("Arena ausente.");
        World world = Bukkit.getWorld(arena.getSourceWorld());
        return world == null ? Bukkit.createWorld(new WorldCreator(arena.getSourceWorld())) : world;
    }

    public void createFromWorld(String id, String worldName, final ResultCallback callback) throws IOException {
        requireIdAvailable(id);
        worlds.importTemplateAsync(worldName, id, new WorldInstanceManager.ImportCompletion() {
            @Override
            public void complete(Exception error) {
                if (error != null) {
                    callback.complete(null, error);
                    return;
                }
                try {
                    ArenaDefinition arena = new ArenaDefinition(id, worldName);
                    arena.addTeam(new TeamDefinition("red", "Vermelho", "c", 4));
                    arena.addTeam(new TeamDefinition("blue", "Azul", "9", 4));
                    repository.save(arena);
                    arenas.put(arena.getId(), arena);
                    callback.complete(arena, null);
                } catch (Exception saveError) {
                    callback.complete(null, saveError);
                }
            }
        });
    }

    public void configureTeam(ArenaDefinition arena, String id, String name, String color, int capacity)
            throws IOException {
        requireEditable(arena);
        String normalizedId = id == null ? null : id.toLowerCase(Locale.ROOT);
        TeamDefinition replacement = new TeamDefinition(normalizedId, name,
            color == null ? null : color.toLowerCase(Locale.ROOT), capacity);
        TeamDefinition previous = arena.getTeams().get(normalizedId);
        if (previous == null) {
            arena.addTeam(replacement);
        } else {
            replacement.setSpawn(previous.getSpawn());
            replacement.setRespawn(previous.getRespawn());
            replacement.setBed(previous.getBed());
            replacement.setItemShop(previous.getItemShop());
            replacement.setUpgradeShop(previous.getUpgradeShop());
            replacement.setSpawnStructure(previous.getSpawnStructure());
            arena.replaceTeam(replacement);
        }
        try {
            repository.save(arena);
        } catch (IOException error) {
            if (previous == null) arena.removeTeam(normalizedId);
            else arena.replaceTeam(previous);
            throw error;
        }
    }

    public void removeTeam(ArenaDefinition arena, String id) throws IOException {
        requireEditable(arena);
        if (arena.getTeamList().size() <= 2) {
            throw new IllegalStateException("A arena deve manter pelo menos duas equipes.");
        }
        TeamDefinition removed = arena.removeTeam(id);
        if (removed == null) throw new IllegalArgumentException("Equipe nao encontrada.");
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.addTeam(removed);
            throw error;
        }
    }

    public void setPosition(ArenaDefinition arena, String selector, String teamId, Position position)
            throws IOException {
        requireEditable(arena);
        TeamDefinition team = team(arena, teamId);
        Position previous;
        if ("lobby".equals(selector)) {
            if (teamId != null) throw new IllegalArgumentException("Lobby nao usa identificador de equipe.");
            previous = arena.getLobby();
            arena.setLobby(position);
        } else if ("spectator".equals(selector)) {
            if (teamId != null) throw new IllegalArgumentException("Espectador nao usa identificador de equipe.");
            previous = arena.getSpectator();
            arena.setSpectator(position);
        } else if ("spawn".equals(selector) && team != null) {
            previous = team.getSpawn();
            team.setSpawn(position);
        } else if ("respawn".equals(selector) && team != null) {
            previous = team.getRespawn();
            team.setRespawn(position);
        } else if ("bed".equals(selector) && team != null) {
            previous = team.getBed();
            team.setBed(position);
        } else {
            throw new IllegalArgumentException("Tipo de localizacao/equipe invalido.");
        }
        try {
            repository.save(arena);
        } catch (IOException error) {
            if ("lobby".equals(selector)) {
                arena.setLobby(previous);
            } else if ("spectator".equals(selector)) {
                arena.setSpectator(previous);
            } else if ("spawn".equals(selector)) {
                team.setSpawn(previous);
            } else if ("respawn".equals(selector)) {
                team.setRespawn(previous);
            } else {
                team.setBed(previous);
            }
            throw error;
        }
    }

    public void setMinimumPlayers(ArenaDefinition arena, int count) throws IOException {
        requireEditable(arena);
        if (count < 2 || count > arena.getMaximumPlayers()) {
            throw new IllegalArgumentException("Minimo deve estar entre 2 e a capacidade total da arena.");
        }
        int previous = arena.getMinimumPlayers();
        arena.setMinimumPlayers(count);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.setMinimumPlayers(previous);
            throw error;
        }
    }

    public void setCountdown(ArenaDefinition arena, int seconds) throws IOException {
        requireEditable(arena);
        if (seconds < 1 || seconds > 600) {
            throw new IllegalArgumentException("Countdown deve estar entre 1 e 600 segundos.");
        }
        int previous = arena.getCountdownSeconds();
        arena.setCountdownSeconds(seconds);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.setCountdownSeconds(previous);
            throw error;
        }
    }

    public ResourceGeneratorDefinition addGenerator(ArenaDefinition arena, ResourceType resource,
                                                     String teamId, Position position) throws IOException {
        return addGenerator(arena, resource, teamId, position, null);
    }

    public ResourceGeneratorDefinition addGenerator(ArenaDefinition arena, ResourceType resource,
                                                     String teamId, Position position, ArenaRegion region)
            throws IOException {
        requireEditable(arena);
        if (resource == null || position == null || !arena.getSourceWorld().equals(position.getWorld())) {
            throw new IllegalArgumentException("Recurso/localizacao invalido ou fora do mundo-fonte da arena.");
        }
        if (region != null && (!arena.getSourceWorld().equals(region.getWorld())
                || region.getVolume() > maximumRegionBlocks)) {
            throw new IllegalArgumentException("Regiao do gerador invalida ou excede o limite.");
        }
        String normalizedTeam = teamId == null ? null : teamId.toLowerCase(Locale.ROOT);
        if (normalizedTeam != null && team(arena, normalizedTeam) == null) {
            throw new IllegalArgumentException("Equipe nao encontrada.");
        }
        String base = resource.name().toLowerCase(Locale.ROOT);
        int suffix = 1;
        while (arena.getGenerators().containsKey(base + "_" + suffix)) suffix++;
        ResourceGeneratorDefinition definition =
            new ResourceGeneratorDefinition(base + "_" + suffix, resource, position,
                normalizedTeam, 1, region);
        arena.addGenerator(definition);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.removeGenerator(definition.getId());
            throw error;
        }
        return definition;
    }

    public List<ResourceGeneratorDefinition> addTeamGenerators(ArenaDefinition arena, String teamId,
                                                                Position position, ArenaRegion region)
            throws IOException {
        requireEditable(arena);
        TeamDefinition team = team(arena, teamId);
        if (team == null) throw new IllegalArgumentException("Equipe nao encontrada.");
        if (position == null || !arena.getSourceWorld().equals(position.getWorld())
                || region == null || !arena.getSourceWorld().equals(region.getWorld())
                || region.getVolume() > maximumRegionBlocks || !region.contains(position)) {
            throw new IllegalArgumentException("Posicao/regiao dos geradores da equipe invalida.");
        }
        List<ResourceGeneratorDefinition> added = new ArrayList<ResourceGeneratorDefinition>();
        ResourceType[] resources = {ResourceType.IRON, ResourceType.GOLD, ResourceType.EMERALD};
        for (ResourceType resource : resources) {
            boolean exists = false;
            for (ResourceGeneratorDefinition current : arena.getGeneratorList()) {
                if (current.getResource() == resource && team.getId().equals(current.getTeamId())) {
                    exists = true;
                    break;
                }
            }
            if (exists) continue;
            String base = resource.name().toLowerCase(Locale.ROOT);
            int suffix = 1;
            while (arena.getGenerators().containsKey(base + "_" + suffix)) suffix++;
            ResourceGeneratorDefinition definition = new ResourceGeneratorDefinition(
                base + "_" + suffix, resource, position, team.getId(), 1, region);
            arena.addGenerator(definition);
            added.add(definition);
        }
        if (added.isEmpty()) return Collections.emptyList();
        try {
            repository.save(arena);
        } catch (IOException error) {
            for (ResourceGeneratorDefinition generator : added) arena.removeGenerator(generator.getId());
            throw error;
        }
        return Collections.unmodifiableList(added);
    }

    public void removeGenerator(ArenaDefinition arena, String generatorId) throws IOException {
        requireEditable(arena);
        ResourceGeneratorDefinition removed = arena.removeGenerator(generatorId);
        if (removed == null) throw new IllegalArgumentException("Gerador nao encontrado.");
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.addGenerator(removed);
            throw error;
        }
    }

    public void setGeneratorTeam(ArenaDefinition arena, String generatorId, String teamId)
            throws IOException {
        requireEditable(arena);
        ResourceGeneratorDefinition current = arena.getGenerators().get(generatorId);
        TeamDefinition team = team(arena, teamId);
        if (current == null) throw new IllegalArgumentException("Gerador nao encontrado.");
        if (team == null) throw new IllegalArgumentException("Equipe nao encontrada.");
        if (current.getResource() != ResourceType.IRON && current.getResource() != ResourceType.GOLD
                && current.getResource() != ResourceType.EMERALD) {
            throw new IllegalArgumentException("Este recurso nao pode ser associado a equipe.");
        }
        ResourceGeneratorDefinition replacement = new ResourceGeneratorDefinition(
            current.getId(), current.getResource(), current.getPosition(), team.getId(),
            current.getStartingLevel(), current.getRegion());
        arena.replaceGenerator(replacement);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.replaceGenerator(current);
            throw error;
        }
    }

    public void setShopPosition(ArenaDefinition arena, boolean upgrades, Position position)
            throws IOException {
        requireEditable(arena);
        if (position == null || !arena.getSourceWorld().equals(position.getWorld())) {
            throw new IllegalArgumentException("NPC deve ser configurado no mundo-fonte da arena.");
        }
        Position previous = upgrades ? arena.getUpgradeShop() : arena.getItemShop();
        if (upgrades) arena.setUpgradeShop(position);
        else arena.setItemShop(position);
        try {
            repository.save(arena);
        } catch (IOException error) {
            if (upgrades) arena.setUpgradeShop(previous);
            else arena.setItemShop(previous);
            throw error;
        }
    }

    public void setTeamShopPosition(ArenaDefinition arena, String teamId, boolean upgrades,
                                   Position position) throws IOException {
        requireEditable(arena);
        TeamDefinition team = team(arena, teamId);
        if (team == null || position == null || !arena.getSourceWorld().equals(position.getWorld())) {
            throw new IllegalArgumentException("Equipe ou localizacao de loja invalida.");
        }
        Position previous = upgrades ? team.getUpgradeShop() : team.getItemShop();
        if (upgrades) team.setUpgradeShop(position);
        else team.setItemShop(position);
        saveOrRestore(arena, new Runnable() {
            @Override public void run() {
                if (upgrades) team.setUpgradeShop(previous);
                else team.setItemShop(previous);
            }
        });
    }

    public void setEnabled(ArenaDefinition arena, boolean enabled) throws IOException {
        if (arena == null || arenas.get(arena.getId()) != arena) {
            throw new IllegalArgumentException("Arena ausente ou nao gerenciada por este servico.");
        }
        if (enabled && isEditing(arena.getId())) {
            throw new IllegalStateException("Arena em modo de edicao nao pode ser habilitada. "
                + "Valide e publique primeiro com /sbw arena edit publish " + arena.getId() + ".");
        }
        if (enabled) {
            List<String> errors = validate(arena);
            if (!errors.isEmpty()) {
                throw new IllegalStateException(join(errors));
            }
        }
        boolean previous = arena.isEnabled();
        arena.setEnabled(enabled);
        try {
            repository.save(arena);
        } catch (IOException error) {
            arena.setEnabled(previous);
            throw error;
        }
    }

    public List<String> validate(ArenaDefinition arena) {
        return ArenaValidator.validate(arena, repository.getTemplateDirectory(arena.getId()),
            maximumRegionBlocks);
    }

    public Position position(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Localizacao invalida.");
        }
        return new Position(location.getWorld().getName(), location.getX(), location.getY(),
            location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation(Position position, World instance) {
        if (position == null || instance == null) {
            throw new IllegalArgumentException("Posicao e mundo sao obrigatorios.");
        }
        return new Location(instance, position.getX(), position.getY(), position.getZ(),
            position.getYaw(), position.getPitch());
    }

    public Position bedPosition(Player player, World expectedWorld) {
        org.bukkit.block.Block block = player.getTargetBlock(Collections.<Material>emptySet(), 6);
        if (block == null || block.getType() != Material.BED_BLOCK
                || block.getWorld() != expectedWorld) {
            throw new IllegalArgumentException("Olhe para uma cama valida no mundo-fonte da arena.");
        }
        return new Position(expectedWorld.getName(), block.getX() + 0.5, block.getY(),
            block.getZ() + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    public String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private void requireIdAvailable(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}") || arenas.containsKey(id)) {
            throw new IllegalArgumentException("Identificador invalido ou arena ja existente.");
        }
    }

    private void requireEditable(ArenaDefinition arena) {
        if (arena == null || arenas.get(arena.getId()) != arena
                || matchEngine != null && matchEngine.isArenaActive(arena.getId())) {
            throw new IllegalStateException("Arena ausente, em partida ou nao gerenciada por este servico.");
        }
    }

    private void saveOrRestore(ArenaDefinition arena, Runnable rollback) throws IOException {
        try {
            repository.save(arena);
        } catch (IOException error) {
            rollback.run();
            throw error;
        }
    }

    private TeamDefinition team(ArenaDefinition arena, String id) {
        return id == null ? null : arena.getTeams().get(id.toLowerCase(Locale.ROOT));
    }

    public interface ResultCallback {
        void complete(ArenaDefinition arena, Exception error);
    }
}
