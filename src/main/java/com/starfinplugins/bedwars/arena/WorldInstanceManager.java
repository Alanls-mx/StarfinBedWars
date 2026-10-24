package com.starfinplugins.bedwars.arena;

import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.world.VoidChunkGenerator;
import com.starfinplugins.bedwars.world.WorldManagementService;
import com.starfinplugins.bedwars.world.WorldManagementService.WorldType;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

public final class WorldInstanceManager {
    private static final String MARKER = ".starfinbedwars-instance";
    private final Plugin plugin;
    private final Path worldContainer;
    private final Path templateRoot;
    private final WorldManagementService worldManagement;

    public WorldInstanceManager(Plugin plugin) throws IOException {
        this(plugin, null);
    }

    public WorldInstanceManager(Plugin plugin, WorldManagementService worldManagement) throws IOException {
        this.plugin = plugin;
        this.worldManagement = worldManagement;
        worldContainer = plugin.getServer().getWorldContainer().toPath().toRealPath();
        Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Files.createDirectories(data);
        Path realData = data.toRealPath();
        Path templates = realData.resolve("templates");
        Files.createDirectories(templates);
        templateRoot = templates.toRealPath();
        if (!templateRoot.startsWith(realData) || templateRoot.equals(worldContainer)) {
            throw new IOException("Diretorio de templates fora da pasta do plugin.");
        }
        cleanupOrphanedInstances();
    }

    public void createInstance(final ArenaDefinition arena, final Completion completion) {
        if (arena == null || completion == null) {
            throw new IllegalArgumentException("Arena e callback sao obrigatorios.");
        }
        final String worldName = "sbw_" + UUID.randomUUID().toString().replace("-", "");
        final Path target = worldContainer.resolve(worldName);
        final Path source;
        final Path staging;
        try {
            source = resolveTemplate(arena.getId());
            if (!Files.isRegularFile(source.resolve("level.dat"))) {
                throw new IOException("Template sem level.dat.");
            }
            if (Files.exists(target) || Bukkit.getWorld(worldName) != null) {
                throw new IOException("Nome de instancia ja esta em uso.");
            }
            staging = Files.createTempDirectory(worldContainer, ".sbw-staging-");
        } catch (Exception error) {
            completion.complete(null, error);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Exception failure = null;
                try {
                    copyTree(source, staging);
                    Files.write(staging.resolve(MARKER), worldName.getBytes("UTF-8"));
                    moveWithoutReplacement(staging, target);
                } catch (Exception error) {
                    failure = error;
                    try {
                        deleteTree(staging, worldContainer);
                    } catch (IOException cleanupError) {
                        failure.addSuppressed(cleanupError);
                    }
                }
                final Exception copyFailure = failure;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (copyFailure != null) {
                            completion.complete(null, copyFailure);
                            return;
                        }
                        World world;
                        try {
                            world = loadInstance(arena, worldName);
                            if (world == null) {
                                throw new IOException("Bukkit nao carregou a instancia " + worldName);
                            }

                        } catch (Exception error) {
                            if (Bukkit.getWorld(worldName) == null) {
                                try {
                                    deleteMarkedInstance(worldName);
                                } catch (IOException cleanupError) {
                                    error.addSuppressed(cleanupError);
                                }
                            }
                            completion.complete(null, error);
                            return;
                        }
                        completion.complete(world, null);
                    }
                });
            }
        });
    }

    public World loadInstance(ArenaDefinition arena, String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        WorldType type = worldManagement == null ? null : worldManagement.getType(arena.getSourceWorld());
        if (type == WorldType.VOID_EMPTY || type == WorldType.VOID_PLATFORM) {
            creator.generateStructures(false);
            creator.generator(new VoidChunkGenerator());
        }
        return Bukkit.createWorld(creator);
    }

    public boolean removeInstance(World world) throws IOException {
        if (world == null || !world.getName().matches("sbw_[a-f0-9]{32}")) {
            return false;
        }
        Path folder = worldContainer.resolve(world.getName()).normalize();
        if (!folder.getParent().equals(worldContainer) || Files.isSymbolicLink(folder)
                || !Files.isDirectory(folder)) {
            return false;
        }
        verifyMarker(folder, world.getName());
        World main = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (!candidate.equals(world) && !candidate.getName().matches("sbw_[a-f0-9]{32}")) {
                main = candidate;
                break;
            }
        }
        if (main == null) {
            throw new IOException("Nao existe mundo de destino seguro para remover jogadores.");
        }
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            player.teleport(main.getSpawnLocation());
        }
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IOException("Nao foi possivel descarregar o mundo " + world.getName());
        }
        deleteTree(folder, worldContainer);
        return true;
    }

    public void publishInstanceAsTemplate(final ArenaDefinition arena, final World editWorld,
                                          final ImportCompletion completion) throws IOException {
        if (arena == null || editWorld == null || completion == null) {
            throw new IllegalArgumentException("Arena, mundo de edicao e callback sao obrigatorios.");
        }
        if (!editWorld.getName().matches("sbw_[a-f0-9]{32}") || !editWorld.getPlayers().isEmpty()) {
            throw new IOException("A instancia deve ser valida e estar sem jogadores para publicar.");
        }
        final Path instance = worldContainer.resolve(editWorld.getName()).normalize();
        if (!instance.getParent().equals(worldContainer) || Files.isSymbolicLink(instance)) {
            throw new IOException("Diretorio de edicao invalido.");
        }
        verifyMarker(instance, editWorld.getName());
        final Path template = resolveTemplate(arena.getId());
        final Path staging = Files.createTempDirectory(templateRoot, ".sbw-publish-");
        final Path backup = templateRoot.resolve(".sbw-backup-" + UUID.randomUUID().toString().replace("-", ""));
        editWorld.save();
        if (!Bukkit.unloadWorld(editWorld, true)) {
            deleteTree(staging, templateRoot);
            throw new IOException("Nao foi possivel descarregar a instancia de edicao.");
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Exception failure = null;
                boolean oldTemplateMoved = false;
                boolean newTemplateInstalled = false;
                boolean published = false;
                try {
                    copyTree(instance, staging);
                    if (!Files.isRegularFile(staging.resolve("level.dat"))) {
                        throw new IOException("A copia publicada nao contem level.dat.");
                    }
                    Files.move(template, backup);
                    oldTemplateMoved = true;
                    Files.move(staging, template);
                    newTemplateInstalled = true;
                    published = true;
                } catch (Exception error) {
                    failure = error;
                    try {
                        if (newTemplateInstalled) deleteTree(template, templateRoot);
                        if (oldTemplateMoved && Files.exists(backup) && !Files.exists(template)) {
                            Files.move(backup, template);
                        }
                    } catch (Exception rollbackError) {
                        failure.addSuppressed(rollbackError);
                    }
                    try {
                        if (Files.exists(staging)) deleteTree(staging, templateRoot);
                    } catch (Exception cleanupError) {
                        failure.addSuppressed(cleanupError);
                    }
                }
                if (published) {
                    try {
                        deleteTree(backup, templateRoot);
                    } catch (Exception cleanupError) {
                        plugin.getLogger().warning("Template publicado, mas backup preservado em "
                            + backup.getFileName() + ": " + cleanupError.getMessage());
                    }
                    try {
                        deleteTree(instance, worldContainer);
                    } catch (Exception cleanupError) {
                        plugin.getLogger().warning("Template publicado, mas instancia de edicao preservada: "
                            + cleanupError.getMessage());
                    }
                }
                final Exception result = failure;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        completion.complete(result);
                    }
                });
            }
        });
    }

    public Path resolveTemplate(String arenaId) throws IOException {
        if (arenaId == null || !arenaId.matches("[a-z0-9_-]{1,32}")) {
            throw new IOException("Identificador de arena invalido.");
        }
        Path candidate = templateRoot.resolve(arenaId).normalize();
        if (!candidate.getParent().equals(templateRoot) || !Files.isDirectory(candidate)
                || Files.isSymbolicLink(candidate)) {
            throw new IOException("Template nao encontrado ou caminho invalido para " + arenaId);
        }
        Path real = candidate.toRealPath();
        if (!real.startsWith(templateRoot)) {
            throw new IOException("Template fora do diretorio autorizado.");
        }
        return real;
    }

    public void importTemplateAsync(final String worldName, final String arenaId,
                                    final ImportCompletion completion) throws IOException {
        if (completion == null) {
            throw new IllegalArgumentException("Callback obrigatorio.");
        }
        if (worldName == null || !worldName.matches("[A-Za-z0-9_-]{1,64}")
                || arenaId == null || !arenaId.matches("[a-z0-9_-]{1,32}")) {
            throw new IOException("Nome de mundo ou identificador de arena invalido.");
        }
        if (Bukkit.getWorld(worldName) != null) {
            throw new IOException("O mundo deve estar descarregado para garantir um snapshot consistente.");
        }
        final Path source = worldContainer.resolve(worldName).normalize();
        final Path target = templateRoot.resolve(arenaId).normalize();
        if (!source.getParent().equals(worldContainer) || !Files.isDirectory(source)
                || Files.isSymbolicLink(source) || !Files.isRegularFile(source.resolve("level.dat"))
                || Files.isSymbolicLink(source.resolve("level.dat"))) {
            throw new IOException("Pasta do mundo-fonte invalida ou ausente.");
        }
        if (!target.getParent().equals(templateRoot) || Files.exists(target)) {
            throw new IOException("Template ja existe ou caminho invalido.");
        }
        final Path realSource = source.toRealPath();
        if (!realSource.startsWith(worldContainer)) {
            throw new IOException("Mundo-fonte fora do diretorio permitido.");
        }
        final Path staging = Files.createTempDirectory(templateRoot, ".sbw-template-");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Exception failure = null;
                try {
                    copyTree(realSource, staging);
                    moveWithoutReplacement(staging, target);
                } catch (Exception error) {
                    failure = error;
                    try {
                        deleteTree(staging, templateRoot);
                    } catch (IOException cleanupError) {
                        failure.addSuppressed(cleanupError);
                    }
                }
                final Exception result = failure;
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        completion.complete(result);
                    }
                });
            }
        });
    }

    private void cleanupOrphanedInstances() throws IOException {
        java.io.File[] children = worldContainer.toFile().listFiles();
        if (children == null) {
            throw new IOException("Nao foi possivel inspecionar diretorio de mundos.");
        }
        for (java.io.File child : children) {
            String name = child.getName();
            if (!name.matches("sbw_[a-f0-9]{32}") || !child.isDirectory()
                    || Files.isSymbolicLink(child.toPath()) || Bukkit.getWorld(name) != null) {
                continue;
            }
            Path folder = child.toPath().toRealPath();
            if (!folder.getParent().equals(worldContainer)) continue;
            try {
                verifyMarker(folder, name);
            } catch (IOException notOwned) {
                continue;
            }
            try {
                deleteTree(folder, worldContainer);
            } catch (IOException error) {
                plugin.getLogger().warning("Instancia antiga preservada; falha ao limpar "
                    + name + ": " + error.getMessage());
            }
        }
    }

    private void deleteMarkedInstance(String worldName) throws IOException {
        Path folder = worldContainer.resolve(worldName).normalize();
        if (!folder.getParent().equals(worldContainer) || Files.isSymbolicLink(folder)
                || !Files.isDirectory(folder)) return;
        verifyMarker(folder, worldName);
        deleteTree(folder, worldContainer);
    }

    private void verifyMarker(Path folder, String worldName) throws IOException {
        Path marker = folder.resolve(MARKER);
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker)
                || !worldName.equals(new String(Files.readAllBytes(marker), "UTF-8"))) {
            throw new IOException("Marcador de propriedade da instancia ausente ou invalido.");
        }
    }

    private void moveWithoutReplacement(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    private void copyTree(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symlink nao permitido em mundo: " + directory);
                }
                String name = directory.getFileName() == null ? "" : directory.getFileName().toString();
                if ("playerdata".equalsIgnoreCase(name) || "stats".equalsIgnoreCase(name)
                        || "advancements".equalsIgnoreCase(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path destination = target.resolve(source.relativize(directory));
                if (!Files.exists(destination)) Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symlink nao permitido em mundo: " + file);
                }
                String name = file.getFileName().toString();
                if ("uid.dat".equalsIgnoreCase(name) || "session.lock".equalsIgnoreCase(name)
                        || MARKER.equals(name)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteTree(final Path root, Path permittedParent) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        Path parent = permittedParent.toAbsolutePath().normalize();
        if (!normalized.getParent().equals(parent) || !Files.exists(normalized)) {
            throw new IOException("Recusa de exclusao fora do diretorio imediatamente permitido.");
        }
        Files.walkFileTree(normalized, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public interface Completion {
        void complete(World world, Exception error);
    }

    public interface ImportCompletion {
        void complete(Exception error);
    }
}
