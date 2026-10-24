package com.starfinplugins.bedwars.domain.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaDefinition {
    private final String id;
    private final String sourceWorld;
    private final Map<String, TeamDefinition> teams = new LinkedHashMap<String, TeamDefinition>();
    private final Map<String, ResourceGeneratorDefinition> generators =
        new LinkedHashMap<String, ResourceGeneratorDefinition>();
    private final Map<ArenaRegionType, ArenaRegion> regions =
        new LinkedHashMap<ArenaRegionType, ArenaRegion>();
    private String displayName;
    private Position lobby;
    private Position spectator;
    private Position itemShop;
    private Position upgradeShop;
    private Position editSpawn;
    private ArenaRegion bounds;
    private Position boundsCenter;
    private ArenaBoundsMode boundsMode = ArenaBoundsMode.MANUAL;
    private int boundsWidth;
    private int boundsDepth;
    private int boundsUpHeight;
    private int boundsDownDepth;
    private int ceilingHeight = 255;
    private int voidHeight;
    private int minimumPlayers = 2;
    private int countdownSeconds = 30;
    private boolean enabled;

    public ArenaDefinition(String id, String sourceWorld) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("arena id invalido");
        }
        if (sourceWorld == null || !sourceWorld.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("source world invalido");
        }
        this.id = id;
        this.sourceWorld = sourceWorld;
        this.displayName = id;
    }

    public String getId() { return id; }
    public String getSourceWorld() { return sourceWorld; }
    public String getDisplayName() { return displayName; }
    public Position getLobby() { return lobby; }
    public Position getSpectator() { return spectator; }
    public Position getItemShop() { return itemShop; }
    public Position getUpgradeShop() { return upgradeShop; }
    public Position getEditSpawn() { return editSpawn; }
    public ArenaRegion getBounds() { return bounds; }
    public Position getBoundsCenter() { return boundsCenter; }
    public ArenaBoundsMode getBoundsMode() { return boundsMode; }
    public int getBoundsWidth() { return boundsWidth; }
    public int getBoundsDepth() { return boundsDepth; }
    public int getBoundsUpHeight() { return boundsUpHeight; }
    public int getBoundsDownDepth() { return boundsDownDepth; }
    public int getCeilingHeight() { return ceilingHeight; }
    public int getVoidHeight() { return voidHeight; }
    public int getMinimumPlayers() { return minimumPlayers; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public boolean isEnabled() { return enabled; }
    public void setDisplayName(String value) { displayName = value; }
    public void setLobby(Position value) { lobby = value; }
    public void setSpectator(Position value) { spectator = value; }
    public void setItemShop(Position value) { itemShop = value; }
    public void setUpgradeShop(Position value) { upgradeShop = value; }
    public void setEditSpawn(Position value) { editSpawn = value; }
    public void setBounds(ArenaRegion value) { bounds = value; }
    public void setBoundsConfiguration(Position center, ArenaBoundsMode mode, int width, int depth,
                                       int upHeight, int downDepth) {
        if (mode == null || width < 0 || depth < 0 || upHeight < 0 || downDepth < 0
                || mode == ArenaBoundsMode.AUTOMATIC && (center == null || width < 1 || depth < 1)) {
            throw new IllegalArgumentException("Configuracao de limites invalida.");
        }
        boundsCenter = center;
        boundsMode = mode;
        boundsWidth = width;
        boundsDepth = depth;
        boundsUpHeight = upHeight;
        boundsDownDepth = downDepth;
    }
    public void setCeilingHeight(int value) { ceilingHeight = value; }
    public void setVoidHeight(int value) { voidHeight = value; }
    public void setMinimumPlayers(int value) { minimumPlayers = value; }
    public void setCountdownSeconds(int value) { countdownSeconds = value; }
    public void setEnabled(boolean value) { enabled = value; }
    public Map<String, TeamDefinition> getTeams() { return Collections.unmodifiableMap(teams); }
    public List<TeamDefinition> getTeamList() {
        return Collections.unmodifiableList(new ArrayList<TeamDefinition>(teams.values()));
    }
    public void addTeam(TeamDefinition team) {
        if (team == null || teams.containsKey(team.getId())) {
            throw new IllegalArgumentException("equipe duplicada ou ausente");
        }
        teams.put(team.getId(), team);
    }
    public Map<String, TeamDefinition> replaceTeams(List<TeamDefinition> replacements) {
        if (replacements == null || replacements.size() < 2) {
            throw new IllegalArgumentException("A arena deve possuir pelo menos duas equipes.");
        }
        Map<String, TeamDefinition> previous = new LinkedHashMap<String, TeamDefinition>(teams);
        Map<String, TeamDefinition> next = new LinkedHashMap<String, TeamDefinition>();
        for (TeamDefinition team : replacements) {
            if (team == null || next.put(team.getId(), team) != null) {
                throw new IllegalArgumentException("Equipe ausente ou identificador duplicado.");
            }
        }
        teams.clear();
        teams.putAll(next);
        return Collections.unmodifiableMap(previous);
    }
    public TeamDefinition replaceTeam(TeamDefinition team) {
        if (team == null || !teams.containsKey(team.getId())) {
            throw new IllegalArgumentException("equipe ausente");
        }
        return teams.put(team.getId(), team);
    }
    public TeamDefinition removeTeam(String id) { return teams.remove(id); }
    public Map<String, ResourceGeneratorDefinition> getGenerators() {
        return Collections.unmodifiableMap(generators);
    }
    public List<ResourceGeneratorDefinition> getGeneratorList() {
        return Collections.unmodifiableList(new ArrayList<ResourceGeneratorDefinition>(generators.values()));
    }
    public void addGenerator(ResourceGeneratorDefinition generator) {
        if (generator == null || generators.containsKey(generator.getId())) {
            throw new IllegalArgumentException("gerador duplicado ou ausente");
        }
        generators.put(generator.getId(), generator);
    }
    public ResourceGeneratorDefinition removeGenerator(String id) {
        return generators.remove(id);
    }
    public ResourceGeneratorDefinition replaceGenerator(ResourceGeneratorDefinition generator) {
        if (generator == null || !generators.containsKey(generator.getId())) {
            throw new IllegalArgumentException("gerador ausente");
        }
        return generators.put(generator.getId(), generator);
    }
    public Map<ArenaRegionType, ArenaRegion> getRegions() {
        return Collections.unmodifiableMap(regions);
    }
    public ArenaRegion getRegion(ArenaRegionType type) { return regions.get(type); }
    public ArenaRegion setRegion(ArenaRegionType type, ArenaRegion region) {
        if (type == null || region == null) throw new IllegalArgumentException("Tipo/regiao ausente.");
        return regions.put(type, region);
    }
    public ArenaRegion removeRegion(ArenaRegionType type) { return regions.remove(type); }
    public int getMaximumPlayers() {
        int maximum = 0;
        for (TeamDefinition team : teams.values()) {
            maximum += team.getCapacity();
        }
        return maximum;
    }
}
