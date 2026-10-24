package com.starfinplugins.bedwars.domain.arena;

import com.starfinplugins.bedwars.domain.economy.ResourceType;

public final class ResourceGeneratorDefinition {
    private final String id;
    private final ResourceType resource;
    private final Position position;
    private final String teamId;
    private final int startingLevel;
    private final ArenaRegion region;

    public ResourceGeneratorDefinition(String id, ResourceType resource, Position position,
                                       String teamId, int startingLevel) {
        this(id, resource, position, teamId, startingLevel, null);
    }

    public ResourceGeneratorDefinition(String id, ResourceType resource, Position position,
                                       String teamId, int startingLevel, ArenaRegion region) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}") || resource == null || position == null
                || startingLevel < 1 || startingLevel > 10) {
            throw new IllegalArgumentException("Definicao de gerador invalida.");
        }
        if (teamId != null && ((resource != ResourceType.IRON && resource != ResourceType.GOLD
                && resource != ResourceType.EMERALD) || !teamId.matches("[a-z0-9_-]{1,24}"))) {
            throw new IllegalArgumentException("Gerador de recurso/equipe invalido.");
        }
        this.id = id;
        this.resource = resource;
        this.position = position;
        this.teamId = teamId;
        this.startingLevel = startingLevel;
        if (region != null && !region.getWorld().equals(position.getWorld())) {
            throw new IllegalArgumentException("Regiao do gerador pertence a outro mundo.");
        }
        this.region = region;
    }

    public String getId() { return id; }
    public ResourceType getResource() { return resource; }
    public Position getPosition() { return position; }
    public String getTeamId() { return teamId; }
    public int getStartingLevel() { return startingLevel; }
    public ArenaRegion getRegion() { return region; }
}
