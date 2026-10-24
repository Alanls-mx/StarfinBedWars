package com.starfinplugins.bedwars.domain.arena;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ArenaValidator {
    private static final long DEFAULT_MAXIMUM_REGION_BLOCKS = 25000000L;

    private ArenaValidator() { }

    public static List<String> validate(ArenaDefinition arena, File templateDirectory) {
        return validate(arena, templateDirectory, DEFAULT_MAXIMUM_REGION_BLOCKS);
    }

    public static List<String> validate(ArenaDefinition arena, File templateDirectory,
                                        long maximumRegionBlocks) {
        List<String> errors = new ArrayList<String>();
        if (arena == null) {
            errors.add("Arena ausente.");
            return errors;
        }
        if (templateDirectory == null || !templateDirectory.isDirectory()
                || !new File(templateDirectory, "level.dat").isFile()) {
            errors.add("Template ausente ou sem level.dat.");
        }
        if (arena.getLobby() == null) {
            errors.add("Lobby nao configurado.");
        }
        if (arena.getTeamList().size() < 2) {
            errors.add("Configure pelo menos duas equipes.");
        }
        if (arena.getMinimumPlayers() < 2 || arena.getMinimumPlayers() > arena.getMaximumPlayers()) {
            errors.add("Minimo de jogadores invalido para a capacidade das equipes.");
        }
        if (arena.getCountdownSeconds() < 1 || arena.getCountdownSeconds() > 600) {
            errors.add("Countdown deve estar entre 1 e 600 segundos.");
        }
        if (arena.getCeilingHeight() < 1 || arena.getCeilingHeight() > 255) {
            errors.add("Altura do teto deve estar entre 1 e 255.");
        }
        if (arena.getVoidHeight() < 0 || arena.getVoidHeight() > 255) {
            errors.add("Altura do void deve estar entre 0 e 255.");
        } else if (arena.getVoidHeight() >= arena.getCeilingHeight()) {
            errors.add("Altura do void deve ser menor que a altura do teto.");
        }
        ArenaRegion bounds = arena.getBounds();
        if (bounds != null && (!arena.getSourceWorld().equals(bounds.getWorld())
                || bounds.getMinimumY() != 0 || bounds.getMaximumY() != 255)) {
            errors.add("Limites da arena invalidos ou acima do limite de blocos.");
        }
        ArenaRegion mainRegion = arena.getRegion(ArenaRegionType.MAIN);
        if (mainRegion == null) {
            errors.add("Regiao principal da arena nao configurada.");
        }
        for (java.util.Map.Entry<ArenaRegionType, ArenaRegion> entry : arena.getRegions().entrySet()) {
            ArenaRegion region = entry.getValue();
            if (!arena.getSourceWorld().equals(region.getWorld())) {
                errors.add("Regiao " + entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                    + " pertence a outro mundo.");
            }
            if (region.getVolume() > maximumRegionBlocks) {
                errors.add("Regiao " + entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                    + " excede o limite de " + maximumRegionBlocks + " blocos.");
            }
        }
        Set<String> ids = new HashSet<String>();
        for (TeamDefinition team : arena.getTeamList()) {
            if (!ids.add(team.getId())) {
                errors.add("Identificador de equipe duplicado: " + team.getId());
            }
            if (team.getSpawn() == null) {
                errors.add("Spawn ausente para a equipe " + team.getId() + ".");
            }
            if (team.getRespawn() == null) {
                errors.add("Respawn ausente para a equipe " + team.getId() + ".");
            }
            if (team.getBed() == null) {
                errors.add("Cama ausente para a equipe " + team.getId() + ".");
            }
            validateWorld(arena, team.getSpawn(), "spawn", team.getId(), errors);
            validateWorld(arena, team.getRespawn(), "respawn", team.getId(), errors);
            validateWorld(arena, team.getBed(), "cama", team.getId(), errors);
            validateWorld(arena, team.getItemShop(), "loja de itens", team.getId(), errors);
            validateWorld(arena, team.getUpgradeShop(), "loja de melhorias", team.getId(), errors);
        }
        validateWorld(arena, arena.getLobby(), "lobby", null, errors);
        validateWorld(arena, arena.getSpectator(), "espectador", null, errors);
        validateWorld(arena, arena.getItemShop(), "loja de itens", null, errors);
        validateWorld(arena, arena.getUpgradeShop(), "loja de melhorias", null, errors);
        validateWorld(arena, arena.getEditSpawn(), "ponto de edicao", null, errors);
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            validateWorld(arena, generator.getPosition(), "gerador " + generator.getId(), null, errors);
            ArenaRegion generatorRegion = generator.getRegion();
            if (generatorRegion != null && (!arena.getSourceWorld().equals(generatorRegion.getWorld())
                    || generatorRegion.getVolume() > maximumRegionBlocks)) {
                errors.add("Regiao do gerador " + generator.getId() + " invalida.");
            } else if (generatorRegion != null && !generatorRegion.contains(generator.getPosition())) {
                errors.add("Ponto do gerador " + generator.getId() + " fora da regiao configurada.");
            }
            if ((generator.getResource() == com.starfinplugins.bedwars.domain.economy.ResourceType.IRON
                    || generator.getResource() == com.starfinplugins.bedwars.domain.economy.ResourceType.GOLD)
                    && generator.getTeamId() == null) {
                errors.add("Gerador " + generator.getId() + " ainda nao foi associado a uma equipe.");
            }
            if (generator.getTeamId() != null && !arena.getTeams().containsKey(generator.getTeamId())) {
                errors.add("Gerador " + generator.getId() + " aponta para equipe inexistente.");
            }
        }
        if (mainRegion != null) {
            validateInside(mainRegion, arena.getLobby(), "lobby", errors);
            validateInside(mainRegion, arena.getSpectator(), "espectador", errors);
            validateInside(mainRegion, arena.getItemShop(), "loja de itens", errors);
            validateInside(mainRegion, arena.getUpgradeShop(), "loja de melhorias", errors);
            for (TeamDefinition team : arena.getTeamList()) {
                validateInside(mainRegion, team.getSpawn(), "spawn " + team.getId(), errors);
                validateInside(mainRegion, team.getRespawn(), "respawn " + team.getId(), errors);
                validateInside(mainRegion, team.getBed(), "cama " + team.getId(), errors);
                validateInside(mainRegion, team.getItemShop(), "loja de itens " + team.getId(), errors);
                validateInside(mainRegion, team.getUpgradeShop(), "loja de melhorias " + team.getId(), errors);
            }
            for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
                validateInside(mainRegion, generator.getPosition(), "gerador " + generator.getId(), errors);
                validateRegionInside(mainRegion, generator.getRegion(), "regiao do gerador "
                    + generator.getId(), errors);
            }
        }
        if (bounds != null) {
            validateInside(bounds, arena.getLobby(), "lobby", errors);
            validateInside(bounds, arena.getSpectator(), "espectador", errors);
            for (TeamDefinition team : arena.getTeamList()) {
                validateInside(bounds, team.getSpawn(), "spawn " + team.getId(), errors);
                validateInside(bounds, team.getRespawn(), "respawn " + team.getId(), errors);
                validateInside(bounds, team.getBed(), "cama " + team.getId(), errors);
                validateInside(bounds, team.getItemShop(), "loja de itens " + team.getId(), errors);
                validateInside(bounds, team.getUpgradeShop(), "loja de melhorias " + team.getId(), errors);
            }
        }
        return errors;
    }

    private static void validateRegionInside(ArenaRegion outer, ArenaRegion inner, String label,
                                             List<String> errors) {
        if (inner != null && (!outer.getWorld().equals(inner.getWorld())
                || inner.getMinimumX() < outer.getMinimumX() || inner.getMinimumY() < outer.getMinimumY()
                || inner.getMinimumZ() < outer.getMinimumZ() || inner.getMaximumX() > outer.getMaximumX()
                || inner.getMaximumY() > outer.getMaximumY() || inner.getMaximumZ() > outer.getMaximumZ())) {
            errors.add(label + " fora da regiao principal.");
        }
    }

    private static void validateInside(ArenaRegion region, Position position, String label,
                                       List<String> errors) {
        if (position != null && !region.contains(position)) {
            errors.add(label + " fora da regiao principal.");
        }
    }

    private static void validateWorld(ArenaDefinition arena, Position position, String type,
                                      String team, List<String> errors) {
        if (position != null && !arena.getSourceWorld().equals(position.getWorld())) {
            errors.add("Localizacao de " + type + (team == null ? "" : " da equipe " + team)
                + " pertence a outro mundo.");
        }
        if (position != null && (Math.abs(position.getX()) > 30000000
                || Math.abs(position.getZ()) > 30000000)) {
            errors.add("Coordenada de " + type + (team == null ? "" : " da equipe " + team)
                + " esta fora dos limites do mundo.");
        }
    }
}
