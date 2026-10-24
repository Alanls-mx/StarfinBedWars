package com.starfinplugins.bedwars.world;

import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.service.ManagedService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorldManagementService implements ManagedService {
    private static final String MARKER = ".starfinbedwars-managed-world";
    private final Plugin plugin;
    private final WorldSettings settings;
    private final ArenaService arenas;
    private final Map<String, WorldType> worlds = new LinkedHashMap<String, WorldType>();
    private final Map<String, Long> deleteConfirmations = new LinkedHashMap<String, Long>();
    private final java.util.Set<String> serverWorlds = new java.util.HashSet<String>();
    private Path worldContainer;
    private File registry;

    public WorldManagementService(Plugin plugin, WorldSettings settings, ArenaService arenas) {
        this.plugin = plugin;
        this.settings = settings;
        this.arenas = arenas;
    }

    @Override
    public String getName() { return "world-management"; }

    @Override
    public void start() throws Exception {
        worldContainer = plugin.getServer().getWorldContainer().toPath().toRealPath();
        for (World world : Bukkit.getWorlds()) {
            serverWorlds.add(world.getName().toLowerCase(Locale.ROOT));
        }
        registry = new File(plugin.getDataFolder(), "managed-worlds.yml");
        loadRegistry();
        for (Map.Entry<String, WorldType> entry : worlds.entrySet()) {
            World loaded = Bukkit.getWorld(entry.getKey());
            if (loaded != null && entry.getValue() != WorldType.IMPORTED) createPlatform(loaded);
        }
    }

    @Override
    public void stop() {
        worlds.clear();
        deleteConfirmations.clear();
        serverWorlds.clear();
    }

    public Map<String, WorldType> getManagedWorlds() {
        return java.util.Collections.unmodifiableMap(worlds);
    }

    public WorldType getType(String name) {
        if (name == null) return null;
        for (Map.Entry<String, WorldType> entry : worlds.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private String registeredName(String name) {
        if (name == null) return null;
        for (String candidate : worlds.keySet()) {
            if (candidate.equalsIgnoreCase(name)) return candidate;
        }
        return null;
    }

    public World createVoidWorld(String name, WorldType type) throws IOException {
        requireValidName(name);
        if (name.toLowerCase(Locale.ROOT).startsWith("sbw_")) {
            throw new IllegalArgumentException("O prefixo sbw_ e reservado para instancias de arena.");
        }
        if (!settings.isVoidGeneratorEnabled()) {
            throw new IllegalStateException("O gerador Void esta desabilitado em worlds.yml.");
        }
        if (type != WorldType.VOID_EMPTY && type != WorldType.VOID_PLATFORM) {
            throw new IllegalArgumentException("Tipo de mundo Void invalido.");
        }
        if (getType(name) != null || Files.exists(worldPath(name))
                || Bukkit.getWorld(name) != null) {
            throw new IllegalArgumentException("Ja existe um mundo ou registro com esse nome.");
        }
        WorldCreator creator = new WorldCreator(name);
        creator.generateStructures(false);
        creator.generator(new VoidChunkGenerator());
        World world = Bukkit.createWorld(creator);
        if (world == null) throw new IOException("Bukkit nao conseguiu criar o mundo.");
        Path folder = worldPath(name);
        writeMarker(folder, name, type);
        worlds.put(name, type);
        world.setSpawnLocation(0, settings.getPlatformHeight() + 1, 0);
        if (type == WorldType.VOID_PLATFORM) createPlatform(world);
        try {
            saveRegistry();
        } catch (IOException error) {
            throw new IOException("Mundo criado e marcado, mas o registro persistente falhou; "
                + "o mundo foi preservado para recuperacao manual.", error);
        }
        return world;
    }

    public void importWorld(String name) throws IOException {
        if (!settings.isImportAllowed()) {
            throw new IllegalStateException("A importacao de mundos esta desabilitada em worlds.yml.");
        }
        requireValidName(name);
        if (name.toLowerCase(Locale.ROOT).startsWith("sbw_")
                || name.startsWith(".") || getType(name) != null) {
            throw new IllegalArgumentException("Nome reservado ou mundo ja registrado.");
        }
        Path folder = worldPath(name);
        if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder)
                || !Files.isRegularFile(folder.resolve("level.dat"))
                || Files.isSymbolicLink(folder.resolve("level.dat"))
                || !folder.toRealPath().startsWith(worldContainer)) {
            throw new IOException("Mundo externo ausente, invalido ou fora do diretorio do servidor.");
        }
        worlds.put(name, WorldType.IMPORTED);
        try {
            saveRegistry();
        } catch (IOException error) {
            worlds.remove(name);
            throw error;
        }
    }

    public World loadWorld(String name) throws IOException {
        WorldType type = getType(name);
        if (type == null) throw new IllegalArgumentException("Mundo nao registrado.");
        String canonicalName = registeredName(name);
        World existing = Bukkit.getWorld(canonicalName);
        if (existing != null) return existing;
        Path folder = worldPath(canonicalName);
        if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder)
                || !Files.isRegularFile(folder.resolve("level.dat"))
                || Files.isSymbolicLink(folder.resolve("level.dat"))) {
            throw new IOException("Pasta do mundo ausente ou invalida.");
        }
        if (type != WorldType.IMPORTED) verifyMarker(folder, canonicalName, type);
        WorldCreator creator = new WorldCreator(canonicalName);
        creator.generateStructures(false);
        if (type == WorldType.VOID_EMPTY || type == WorldType.VOID_PLATFORM) {
            creator.generator(new VoidChunkGenerator());
        }
        World loaded = Bukkit.createWorld(creator);
        if (loaded == null) throw new IOException("Bukkit nao conseguiu carregar o mundo.");
        if (type == WorldType.VOID_PLATFORM) createPlatform(loaded);
        return loaded;
    }

    public Location getSafeLocation(World world, WorldType type) {
        if (world == null) throw new IllegalArgumentException("Mundo ausente.");
        if (type == WorldType.VOID_EMPTY || type == WorldType.VOID_PLATFORM) {
            if (type == WorldType.VOID_EMPTY) createPlatform(world);
            int y = settings.getPlatformHeight();
            for (int radius = 0; radius <= settings.getPlatformRadius(); radius++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                        org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
                        if (ground.getType().isSolid()
                                && world.getBlockAt(x, y + 1, z).getType() == Material.AIR
                                && world.getBlockAt(x, y + 2, z).getType() == Material.AIR) {
                            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
                        }
                    }
                }
            }
            throw new IllegalStateException("Nao ha um local seguro na plataforma de spawn.");
        }
        Location spawn = world.getSpawnLocation();
        for (int radius = 0; radius <= 8; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) continue;
                    int x = spawn.getBlockX() + offsetX;
                    int z = spawn.getBlockZ() + offsetZ;
                    int y = world.getHighestBlockYAt(x, z);
                    if (y < 1 || y >= 254) continue;
                    org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
                    org.bukkit.block.Block feet = world.getBlockAt(x, y + 1, z);
                    org.bukkit.block.Block head = world.getBlockAt(x, y + 2, z);
                    if (ground.getType().isSolid() && feet.getType() == Material.AIR
                            && head.getType() == Material.AIR) {
                        return new Location(world, x + 0.5, y + 1.0, z + 0.5,
                            spawn.getYaw(), spawn.getPitch());
                    }
                }
            }
        }
        throw new IllegalStateException("Nao ha um local seguro proximo ao spawn deste mundo.");
    }

    public boolean confirmDeletion(CommandSenderIdentity requester, String name, boolean confirm)
            throws IOException {
        if (!settings.isManagedDeletionAllowed()) {
            throw new IllegalStateException("A exclusao esta desabilitada em worlds.yml.");
        }
        requireValidName(name);
        String key = requester.value + ":" + name.toLowerCase(Locale.ROOT);
        String canonicalName = registeredName(name);
        WorldType type = getType(name);
        if (type == null || type == WorldType.IMPORTED) {
            throw new IllegalArgumentException("Somente mundos Void criados pelo plugin podem ser excluidos.");
        }
        ensureWorldNotReferenced(name);
        Path folder = worldPath(canonicalName);
        verifyMarker(folder, canonicalName, type);
        long now = System.currentTimeMillis();
        Long expiry = deleteConfirmations.get(key);
        if (!confirm) {
            deleteConfirmations.put(key, now + 30000L);
            return false;
        }
        if (expiry == null || expiry.longValue() < now) {
            deleteConfirmations.remove(key);
            throw new IllegalStateException("Confirmacao ausente ou expirada; inicie com /sbw world delete.");
        }
        deleteConfirmations.remove(key);
        World loaded = Bukkit.getWorld(canonicalName);
        if (loaded != null) {
            if (!loaded.getPlayers().isEmpty()) {
                throw new IllegalStateException("Retire todos os jogadores do mundo antes da exclusao.");
            }
            if (!Bukkit.unloadWorld(loaded, false)) {
                throw new IOException("Bukkit nao conseguiu descarregar o mundo.");
            }
        }
        verifyMarker(folder, canonicalName, type);
        Path tombstone = worldContainer.resolve(".sbw-deleting-"
            + UUID.randomUUID().toString().replace("-", ""));
        Files.move(folder, tombstone);
        removeRegistered(canonicalName);
        try {
            saveRegistry();
        } catch (IOException registryError) {
            worlds.put(canonicalName, type);
            try {
                Files.move(tombstone, folder);
            } catch (IOException restoreError) {
                registryError.addSuppressed(restoreError);
            }
            throw registryError;
        }
        try {
            deleteManagedFolder(tombstone);
        } catch (IOException cleanupError) {
            throw new IOException("O mundo foi desregistrado, mas a limpeza do diretorio "
                + tombstone.getFileName() + " ficou incompleta: " + cleanupError.getMessage(), cleanupError);
        }
        return true;
    }

    public void teleport(Player player, String name) throws IOException {
        if (player == null) throw new IllegalArgumentException("Jogador ausente.");
        WorldType type = getType(name);
        World world = loadWorld(name);
        Location safe = getSafeLocation(world, type);
        if (!player.teleport(safe)) throw new IOException("Teleportacao recusada pelo servidor.");
    }

    private void ensureWorldNotReferenced(String name) {
        if (serverWorlds.contains(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Mundos principais do servidor nao podem ser excluidos.");
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(name) && isServerWorld(world)) {
                throw new IllegalStateException("Mundos principais do servidor nao podem ser excluidos.");
            }
        }
        for (ArenaDefinition arena : arenas.getArenas()) {
            if (arena.getSourceWorld().equalsIgnoreCase(name)) {
                throw new IllegalStateException("Mundo usado como origem da arena "
                    + arena.getId() + " nao pode ser excluido.");
            }
        }
        if (name.matches("sbw_[a-f0-9]{32}") || name.startsWith(".sbw-")) {
            throw new IllegalStateException("Instancias e diretorios temporarios nao podem ser excluidos aqui.");
        }
    }

    private boolean isServerWorld(World candidate) {
        return serverWorlds.contains(candidate.getName().toLowerCase(Locale.ROOT));
    }

    private void createPlatform(World world) {
        int radius = settings.getPlatformRadius();
        int height = settings.getPlatformHeight();
        Material material = settings.getPlatformMaterial();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                org.bukkit.block.Block block = world.getBlockAt(x, height, z);
                if (block.getType() == Material.AIR) block.setType(material);
            }
        }
        world.setSpawnLocation(0, height + 1, 0);
    }

    private void loadRegistry() throws IOException {
        if (!registry.exists()) {
            worlds.clear();
            saveRegistry();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(registry);
        } catch (Exception error) {
            throw new IOException("managed-worlds.yml invalido: " + error.getMessage(), error);
        }
        if (yaml.getInt("version", -1) != 1) throw new IOException("Versao de registro de mundos invalida.");
        ConfigurationSection section = yaml.getConfigurationSection("worlds");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            requireValidName(name);
            String encoded = section.getString(name);
            WorldType type;
            try {
                type = WorldType.valueOf(encoded);
            } catch (Exception error) {
                throw new IOException("Tipo de mundo registrado invalido: " + name, error);
            }
            Path folder = worldPath(name);
            if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder)
                    || !Files.isRegularFile(folder.resolve("level.dat"))) {
                throw new IOException("Pasta registrada ausente ou invalida: " + name);
            }
            if (type != WorldType.IMPORTED) verifyMarker(folder, name, type);
            if (getType(name) != null) throw new IOException("Nome de mundo duplicado: " + name);
            worlds.put(name, type);
        }
    }

    private void saveRegistry() throws IOException {
        if (!registry.getParentFile().exists() && !registry.getParentFile().mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta de dados do plugin.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        for (Map.Entry<String, WorldType> entry : worlds.entrySet()) {
            yaml.set("worlds." + entry.getKey(), entry.getValue().name());
        }
        File temp = File.createTempFile("worlds-", ".yml.tmp", registry.getParentFile());
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), registry.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temp.toPath(), registry.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    private Path worldPath(String name) throws IOException {
        if (worldContainer == null) {
            throw new IllegalStateException("Servico de mundos nao inicializado.");
        }
        Path path = worldContainer.resolve(name).normalize();
        if (!worldContainer.equals(path.getParent())) throw new IOException("Caminho de mundo invalido.");
        return path;
    }

    private void writeMarker(Path folder, String name, WorldType type) throws IOException {
        if (Files.isSymbolicLink(folder) || !Files.isDirectory(folder)
                || !folder.toRealPath().startsWith(worldContainer)) {
            throw new IOException("Pasta fora do servidor ou caminho invalido.");
        }
        Files.write(folder.resolve(MARKER), markerValue(name, type).getBytes("UTF-8"));
    }

    private void verifyMarker(Path folder, String name, WorldType type) throws IOException {
        Path marker = folder.resolve(MARKER);
        if (Files.isSymbolicLink(folder) || Files.isSymbolicLink(marker)
                || !Files.isDirectory(folder) || !Files.isRegularFile(marker)
                || !folder.toRealPath().startsWith(worldContainer)
                || !markerValue(name, type).equals(new String(Files.readAllBytes(marker), "UTF-8"))) {
            throw new IOException("Marcador de propriedade do mundo ausente ou invalido.");
        }
    }

    private String markerValue(String name, WorldType type) {
        return "starfinbedwars:" + name.toLowerCase(Locale.ROOT) + ":" + type.name() + ":v1";
    }

    private void deleteManagedFolder(Path folder) throws IOException {
        Path root = folder.toRealPath();
        if (!root.getParent().equals(worldContainer)) throw new IOException("Recusa de exclusao fora do servidor.");
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path directory,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symlink detectado; nenhuma exclusao foi iniciada.");
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symlink detectado; nenhuma exclusao foi iniciada.");
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlink detectado; exclusao interrompida.");
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException error)
                    throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void requireValidName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Nome de mundo invalido.");
        }
    }

    private void removeRegistered(String name) {
        String storedName = null;
        for (String candidate : worlds.keySet()) {
            if (candidate.equalsIgnoreCase(name)) {
                storedName = candidate;
                break;
            }
        }
        if (storedName != null) worlds.remove(storedName);
    }

    public static final class CommandSenderIdentity {
        private final String value;
        public CommandSenderIdentity(String value) {
            if (value == null || value.length() == 0) throw new IllegalArgumentException("Identidade ausente.");
            this.value = value;
        }
    }

    public enum WorldType {
        VOID_EMPTY,
        VOID_PLATFORM,
        IMPORTED
    }
}
