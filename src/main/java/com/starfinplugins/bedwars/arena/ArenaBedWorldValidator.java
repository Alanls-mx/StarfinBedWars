package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

/** Verifies actual beds in an editor or disposable world without generating chunks. */
public final class ArenaBedWorldValidator {
    private ArenaBedWorldValidator() { }

    public static List<String> validate(ArenaDefinition arena, World world) {
        List<String> errors = new ArrayList<String>();
        for (TeamDefinition team : arena.getTeamList()) {
            Position bed = team.getBed();
            if (bed == null) {
                errors.add("cama da equipe " + team.getId() + " sem ponto configurado");
                continue;
            }
            int x = (int) Math.floor(bed.getX());
            int y = (int) Math.floor(bed.getY());
            int z = (int) Math.floor(bed.getZ());
            boolean present;
            try {
                present = (world.isChunkLoaded(x >> 4, z >> 4)
                    || world.loadChunk(x >> 4, z >> 4, false))
                    && world.getBlockAt(x, y, z).getType() == Material.BED_BLOCK;
            } catch (RuntimeException failure) {
                errors.add("nao foi possivel ler cama da equipe " + team.getId()
                    + " em " + x + ", " + y + ", " + z + ": " + failure.getMessage());
                continue;
            }
            if (!present) errors.add("cama da equipe " + team.getId()
                + " ausente em " + x + ", " + y + ", " + z);
        }
        return errors;
    }
}
