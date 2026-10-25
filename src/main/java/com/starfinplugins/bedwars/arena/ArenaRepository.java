package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.domain.arena.ArenaBoundsMode;
import com.starfinplugins.bedwars.domain.arena.ArenaRegion;
import com.starfinplugins.bedwars.domain.arena.ArenaRegionType;
import com.starfinplugins.bedwars.domain.arena.Position;
import com.starfinplugins.bedwars.domain.arena.ResourceGeneratorDefinition;
import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.arena.SpawnStructureDefinition;
import com.starfinplugins.bedwars.domain.economy.ResourceType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ArenaRepository {
    private final File directory;
    private final List<String> recoveredIds = new ArrayList<String>();

    public ArenaRepository(File dataFolder) {
        this.directory = new File(dataFolder, "arenas");
    }

    public List<ArenaDefinition> loadAll() throws IOException {
        recoveredIds.clear();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Nao foi possivel criar o diretorio de arenas.");
        }
        File[] files = directory.listFiles();
        if (files == null) {
            throw new IOException("Nao foi possivel listar as arenas.");
        }
        recoverMissingFiles(files);
        files = directory.listFiles();
        if (files == null) throw new IOException("Nao foi possivel listar as arenas apos recuperacao.");
        List<ArenaDefinition> result = new ArrayList<ArenaDefinition>();
        for (File file : files) {
            if (!file.isFile() || !file.getName().matches("[a-z0-9_-]{1,32}\\.yml")) {
                continue;
            }
            try {
                ArenaDefinition arena = read(file);
                if (!file.getName().equals(arena.getId() + ".yml")) {
                    throw new IOException("ID diferente do nome do arquivo.");
                }
                if (!backupFile(arena.getId()).isFile()) {
                    writeBackup(arena.getId(), file.toPath());
                }
                result.add(arena);
            } catch (Exception error) {
                throw new IOException("Arena invalida em " + file.getName() + ": " + error.getMessage(), error);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<String> getRecoveredIds() {
        return Collections.unmodifiableList(new ArrayList<String>(recoveredIds));
    }

    public void save(ArenaDefinition arena) throws IOException {
        ensureDirectory();
        File target = file(arena.getId());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 8);
        yaml.set("id", arena.getId());
        yaml.set("name", arena.getDisplayName());
        yaml.set("group", arena.getGroup());
        yaml.set("source-world", arena.getSourceWorld());
        yaml.set("minimum-players", arena.getMinimumPlayers());
        yaml.set("countdown-seconds", arena.getCountdownSeconds());
        yaml.set("enabled", arena.isEnabled());
        writePosition(yaml, "lobby", arena.getLobby());
        writePosition(yaml, "spectator", arena.getSpectator());
        writePosition(yaml, "shops.items", arena.getItemShop());
        writePosition(yaml, "shops.upgrades", arena.getUpgradeShop());
        writePosition(yaml, "edit-spawn", arena.getEditSpawn());
        writeRegion(yaml, "bounds", arena.getBounds());
        writePosition(yaml, "bounds-center", arena.getBoundsCenter());
        yaml.set("bounds-mode", arena.getBoundsMode().name());
        yaml.set("bounds-width", arena.getBoundsWidth());
        yaml.set("bounds-depth", arena.getBoundsDepth());
        yaml.set("bounds-up-height", arena.getBoundsUpHeight());
        yaml.set("bounds-down-depth", arena.getBoundsDownDepth());
        yaml.set("ceiling-height", arena.getCeilingHeight());
        yaml.set("void-height", arena.getVoidHeight());
        for (TeamDefinition team : arena.getTeamList()) {
            String path = "teams." + team.getId() + ".";
            yaml.set(path + "name", team.getDisplayName());
            yaml.set(path + "color", team.getColor());
            yaml.set(path + "color-data", team.getColorData());
            yaml.set(path + "capacity", team.getCapacity());
            writePosition(yaml, path + "spawn", team.getSpawn());
            writePosition(yaml, path + "respawn", team.getRespawn());
            writePosition(yaml, path + "bed", team.getBed());
            writePosition(yaml, path + "shops.items", team.getItemShop());
            writePosition(yaml, path + "shops.upgrades", team.getUpgradeShop());
            SpawnStructureDefinition structure = team.getSpawnStructure();
            if (structure != null) {
                yaml.set(path + "spawn-structure.type", structure.getBlockTypeId());
                yaml.set(path + "spawn-structure.data", structure.getBlockData());
                yaml.set(path + "spawn-structure.count", structure.getBlocks().size());
                for (int index = 0; index < structure.getBlocks().size(); index++) {
                    SpawnStructureDefinition.BlockPosition block = structure.getBlocks().get(index);
                    String blockPath = path + "spawn-structure.blocks." + index + ".";
                    yaml.set(blockPath + "x", block.getX());
                    yaml.set(blockPath + "y", block.getY());
                    yaml.set(blockPath + "z", block.getZ());
                }
            }
        }
        for (ResourceGeneratorDefinition generator : arena.getGeneratorList()) {
            String path = "generators." + generator.getId() + ".";
            yaml.set(path + "resource", generator.getResource().name());
            yaml.set(path + "team", generator.getTeamId());
            yaml.set(path + "starting-level", generator.getStartingLevel());
            writePosition(yaml, path + "position", generator.getPosition());
            writeRegion(yaml, path + "region", generator.getRegion());
        }
        for (java.util.Map.Entry<ArenaRegionType, ArenaRegion> entry : arena.getRegions().entrySet()) {
            String path = "regions." + entry.getKey().name().toLowerCase(java.util.Locale.ROOT) + ".";
            ArenaRegion region = entry.getValue();
            yaml.set(path + "world", region.getWorld());
            yaml.set(path + "min-x", region.getMinimumX());
            yaml.set(path + "min-y", region.getMinimumY());
            yaml.set(path + "min-z", region.getMinimumZ());
            yaml.set(path + "max-x", region.getMaximumX());
            yaml.set(path + "max-y", region.getMaximumY());
            yaml.set(path + "max-z", region.getMaximumZ());
        }
        File temp = File.createTempFile("arena-", ".yml.tmp", directory);
        try {
            yaml.save(temp);
            ArenaDefinition written = read(temp);
            if (!arena.getId().equals(written.getId())) {
                throw new IOException("ID alterado durante serializacao da arena.");
            }
            writeBackup(arena.getId(), temp.toPath());
            moveReplace(temp.toPath(), target.toPath());
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Nao foi possivel validar o YAML da arena " + arena.getId(), error);
        } finally {
            if (temp.exists() && !temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    public boolean delete(String id) throws IOException {
        File target = file(id);
        File backup = backupFile(id);
        if (target.exists() && !target.delete()) return false;
        return !backup.exists() || backup.delete();
    }

    public String snapshot(String id) throws IOException {
        File target = file(id);
        if (!target.isFile()) throw new IOException("Arquivo da arena nao encontrado para snapshot.");
        return new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
    }

    public ArenaDefinition restoreSnapshot(String id, String sourceWorld, String snapshot) throws IOException {
        if (snapshot == null || snapshot.length() > 1024 * 1024) {
            throw new IOException("Snapshot da arena ausente ou excede 1 MiB.");
        }
        ensureDirectory();
        File temporary = File.createTempFile("arena-undo-", ".yml", directory);
        try {
            Files.write(temporary.toPath(), snapshot.getBytes(StandardCharsets.UTF_8));
            ArenaDefinition restored;
            try {
                restored = read(temporary);
            } catch (Exception error) {
                throw new IOException("Snapshot de arena invalido.", error);
            }
            if (!id.equals(restored.getId()) || !sourceWorld.equals(restored.getSourceWorld())) {
                throw new IOException("Snapshot pertence a outra arena ou mundo-fonte.");
            }
            writeBackup(id, temporary.toPath());
            moveReplace(temporary.toPath(), file(id).toPath());
            return restored;
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    public File getTemplateDirectory(String id) {
        return new File(new File(directory.getParentFile(), "templates"), id);
    }

    private ArenaDefinition read(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        int version = yaml.getInt("version", -1);
        if (version < 1 || version > 8) {
            throw new IOException("versao de arena nao suportada");
        }
        ArenaDefinition arena = new ArenaDefinition(yaml.getString("id"), yaml.getString("source-world"));
        arena.setDisplayName(yaml.getString("name", arena.getId()));
        arena.setGroup(yaml.getString("group", "default"));
        arena.setMinimumPlayers(yaml.getInt("minimum-players", 2));
        arena.setCountdownSeconds(yaml.getInt("countdown-seconds", 30));
        arena.setEnabled(yaml.getBoolean("enabled", false));
        arena.setLobby(readPosition(yaml, "lobby"));
        arena.setSpectator(readPosition(yaml, "spectator"));
        arena.setItemShop(readPosition(yaml, "shops.items"));
        arena.setUpgradeShop(readPosition(yaml, "shops.upgrades"));
        arena.setEditSpawn(readPosition(yaml, "edit-spawn"));
        arena.setBounds(readRegion(yaml, "bounds"));
        if (version >= 5) {
            arena.setBoundsConfiguration(readPosition(yaml, "bounds-center"),
                ArenaBoundsMode.valueOf(yaml.getString("bounds-mode", "MANUAL")),
                yaml.getInt("bounds-width"), yaml.getInt("bounds-depth"),
                yaml.getInt("bounds-up-height"), yaml.getInt("bounds-down-depth"));
        }
        arena.setCeilingHeight(yaml.getInt("ceiling-height", 255));
        arena.setVoidHeight(yaml.getInt("void-height", 0));
        ConfigurationSection teams = yaml.getConfigurationSection("teams");
        if (teams != null) {
            for (String id : teams.getKeys(false)) {
                ConfigurationSection teamConfig = teams.getConfigurationSection(id);
                if (teamConfig == null) {
                    throw new IOException("configuracao de equipe invalida: " + id);
                }
                TeamDefinition team = new TeamDefinition(id, teamConfig.getString("name"),
                    teamConfig.getString("color"), teamConfig.getInt("capacity"));
                team.setAppearance(teamConfig.getString("name"), teamConfig.getString("color"),
                    teamConfig.getInt("color-data", team.getColorData()));
                team.setSpawn(readPosition(teamConfig, "spawn"));
                team.setRespawn(readPosition(teamConfig, "respawn"));
                team.setBed(readPosition(teamConfig, "bed"));
                team.setItemShop(readPosition(teamConfig, "shops.items"));
                team.setUpgradeShop(readPosition(teamConfig, "shops.upgrades"));
                int structureCount = teamConfig.getInt("spawn-structure.count", -1);
                if (structureCount >= 0) {
                    if (structureCount > 128) throw new IOException("estrutura de spawn excede o limite: " + id);
                    List<SpawnStructureDefinition.BlockPosition> blocks =
                        new ArrayList<SpawnStructureDefinition.BlockPosition>();
                    for (int index = 0; index < structureCount; index++) {
                        String blockPath = "spawn-structure.blocks." + index + ".";
                        blocks.add(new SpawnStructureDefinition.BlockPosition(
                            teamConfig.getInt(blockPath + "x", Integer.MIN_VALUE),
                            teamConfig.getInt(blockPath + "y", -1),
                            teamConfig.getInt(blockPath + "z", Integer.MIN_VALUE)));
                    }
                    team.setSpawnStructure(new SpawnStructureDefinition(
                        teamConfig.getInt("spawn-structure.type", -1),
                        teamConfig.getInt("spawn-structure.data", -1), blocks));
                }
                arena.addTeam(team);
            }
        }
        ConfigurationSection generators = yaml.getConfigurationSection("generators");
        if (generators != null) {
            for (String id : generators.getKeys(false)) {
                ConfigurationSection generator = generators.getConfigurationSection(id);
                if (generator == null || !generator.isString("resource")) {
                    throw new IOException("configuracao de gerador invalida: " + id);
                }
                ResourceType resource = ResourceType.valueOf(generator.getString("resource"));
                arena.addGenerator(new ResourceGeneratorDefinition(id, resource,
                    readPosition(generator, "position"), generator.getString("team"),
                    generator.getInt("starting-level", 1), readRegion(generator, "region")));
            }
        }
        ConfigurationSection regions = yaml.getConfigurationSection("regions");
        if (regions != null) {
            for (String key : regions.getKeys(false)) {
                ConfigurationSection region = regions.getConfigurationSection(key);
                if (region == null || !region.isString("world") || !region.isInt("min-x")
                        || !region.isInt("min-y") || !region.isInt("min-z")
                        || !region.isInt("max-x") || !region.isInt("max-y") || !region.isInt("max-z")) {
                    throw new IOException("configuracao de regiao invalida: " + key);
                }
                ArenaRegionType type = ArenaRegionType.parse(key);
                arena.setRegion(type, new ArenaRegion(region.getString("world"),
                    region.getInt("min-x"), region.getInt("min-y"), region.getInt("min-z"),
                    region.getInt("max-x"), region.getInt("max-y"), region.getInt("max-z")));
            }
        }
        return arena;
    }

    private void writePosition(YamlConfiguration yaml, String path, Position position) {
        if (position == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".world", position.getWorld());
        yaml.set(path + ".x", position.getX());
        yaml.set(path + ".y", position.getY());
        yaml.set(path + ".z", position.getZ());
        yaml.set(path + ".yaw", position.getYaw());
        yaml.set(path + ".pitch", position.getPitch());
    }

    private void writeRegion(YamlConfiguration yaml, String path, ArenaRegion region) {
        if (region == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".world", region.getWorld());
        yaml.set(path + ".min-x", region.getMinimumX());
        yaml.set(path + ".min-y", region.getMinimumY());
        yaml.set(path + ".min-z", region.getMinimumZ());
        yaml.set(path + ".max-x", region.getMaximumX());
        yaml.set(path + ".max-y", region.getMaximumY());
        yaml.set(path + ".max-z", region.getMaximumZ());
    }

    private ArenaRegion readRegion(ConfigurationSection section, String path) {
        ConfigurationSection value = section.getConfigurationSection(path);
        if (value == null) return null;
        if (!value.isString("world") || !value.isInt("min-x") || !value.isInt("min-y")
                || !value.isInt("min-z") || !value.isInt("max-x") || !value.isInt("max-y")
                || !value.isInt("max-z")) {
            throw new IllegalArgumentException("regiao incompleta em " + path);
        }
        return new ArenaRegion(value.getString("world"), value.getInt("min-x"), value.getInt("min-y"),
            value.getInt("min-z"), value.getInt("max-x"), value.getInt("max-y"), value.getInt("max-z"));
    }

    private Position readPosition(ConfigurationSection section, String path) {
        ConfigurationSection value = section.getConfigurationSection(path);
        if (value == null) {
            return null;
        }
        if (!value.isString("world") || !value.isDouble("x") || !value.isDouble("y")
                || !value.isDouble("z") || !value.isDouble("yaw") || !value.isDouble("pitch")) {
            throw new IllegalArgumentException("localizacao incompleta em " + path);
        }
        return new Position(value.getString("world"), value.getDouble("x"), value.getDouble("y"),
            value.getDouble("z"), (float) value.getDouble("yaw"), (float) value.getDouble("pitch"));
    }

    private File file(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("arena id invalido");
        }
        return new File(directory, id + ".yml");
    }

    private void ensureDirectory() throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Nao foi possivel criar o diretorio de arenas.");
        }
    }

    private File backupFile(String id) { return new File(directory, id + ".yml.bak"); }

    private void writeBackup(String id, java.nio.file.Path source) throws IOException {
        File temporary = File.createTempFile("arena-backup-", ".tmp", directory);
        try {
            Files.copy(source, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moveReplace(temporary.toPath(), backupFile(id).toPath());
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private void recoverMissingFiles(File[] files) throws IOException {
        for (File backup : files) {
            if (!backup.isFile() || !backup.getName().matches("[a-z0-9_-]{1,32}\\.yml\\.bak")) {
                continue;
            }
            String id = backup.getName().substring(0, backup.getName().length() - ".yml.bak".length());
            File primary = file(id);
            if (primary.exists()) continue;
            try {
                ArenaDefinition recovered = read(backup);
                if (!id.equals(recovered.getId())) throw new IOException("ID divergente no backup.");
            } catch (Exception invalid) {
                throw new IOException("Backup invalido da arena " + id + ": " + invalid.getMessage(), invalid);
            }
            File temporary = File.createTempFile("arena-recovery-", ".tmp", directory);
            try {
                Files.copy(backup.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                moveReplace(temporary.toPath(), primary.toPath());
                recoveredIds.add(id);
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
        }
    }

    private void moveReplace(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
