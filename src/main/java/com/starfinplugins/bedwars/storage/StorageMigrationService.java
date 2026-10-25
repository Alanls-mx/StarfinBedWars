package com.starfinplugins.bedwars.storage;

import com.starfinplugins.bedwars.StarfinBedWarsPlugin;
import com.starfinplugins.bedwars.arena.ArenaService;
import com.starfinplugins.bedwars.domain.arena.ArenaDefinition;
import com.starfinplugins.bedwars.network.NetworkSettings;
import com.starfinplugins.bedwars.service.ManagedService;
import com.starfinplugins.bedwars.stats.StatsSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Explicit, backup-first, non-destructive export from arena/config YAML to SQL storage. */
public final class StorageMigrationService implements ManagedService {
    private static final String[] SHARED_CONFIGS = {
        "config.yml", "shop.yml", "generators.yml", "generator-events.yml",
        "upgrades.yml", "traps.yml", "worlds.yml", "reconnect.yml",
        "shop-favorites.yml", "managed-worlds.yml"
    };
    private final StarfinBedWarsPlugin plugin;
    private final ArenaService arenas;
    private final AtomicBoolean importing = new AtomicBoolean();
    private ExecutorService worker;

    public StorageMigrationService(StarfinBedWarsPlugin plugin, ArenaService arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    @Override public String getName() { return "Migracao segura YAML para SQL"; }

    @Override public void start() {
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "StarfinBedWars-Migration-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public void stop() {
        if (worker == null) return;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                plugin.getLogger().warning("Migracao ainda em andamento ao desligar; confira backup e destino.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    public void execute(CommandSender sender, boolean apply, SqlMigrationWriter.Target target) {
        if (apply && !importing.compareAndSet(false, true)) {
            sender.sendMessage("§cJa existe uma migracao em andamento.");
            return;
        }
        try {
            for (ArenaDefinition arena : arenas.getArenas()) {
                if (arenas.isEditing(arena.getId())) {
                    throw new IllegalStateException("Finalize a edicao da arena " + arena.getId()
                        + " antes de migrar, para nao exportar configuracao parcial.");
                }
            }
            final MigrationBundle bundle = snapshot();
            sender.sendMessage("§bMigracao " + target + " | servidor: " + bundle.serverId
                + " | arenas: " + bundle.arenas.size()
                + " | configuracoes: " + bundle.documents.size() + ".");
            if (!apply) {
                sender.sendMessage("§ePrevia concluida. Use /sbw migrate apply "
                    + target.name().toLowerCase(java.util.Locale.ROOT) + " confirm para importar.");
                return;
            }
            sender.sendMessage("§eBackup e importacao agendados; nenhuma origem sera apagada.");
            final StatsSettings mysql = StatsSettings.load(plugin);
            worker.execute(() -> {
                try {
                    Path backup = backup(bundle);
                    new SqlMigrationWriter().importBundle(target, bundle, mysql, plugin.getDataFolder());
                    notifySender(sender, "§aMigracao para " + target + " concluida. Backup: "
                        + backup.getFileName() + ". Dados originais preservados.");
                    if (target == SqlMigrationWriter.Target.MYSQL) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (plugin.getNetworkRegistry() != null) plugin.getNetworkRegistry().refresh();
                        });
                    }
                } catch (Exception error) {
                    plugin.getLogger().warning("Migracao SQL nao concluida ("
                        + error.getClass().getSimpleName() + "): " + safe(error.getMessage()));
                    notifySender(sender, "§cFalha na migracao: " + safe(error.getMessage())
                        + ". Arquivos locais preservados; confira o backup e logs.");
                } finally {
                    importing.set(false);
                }
            });
        } catch (Exception error) {
            sender.sendMessage("§cMigracao nao iniciada: " + safe(error.getMessage()));
            if (apply) importing.set(false);
        }
    }

    private MigrationBundle snapshot() throws IOException {
        String serverId = NetworkSettings.load(plugin).serverId;
        Map<String, MigrationBundle.Entry> arenaData = new LinkedHashMap<String, MigrationBundle.Entry>();
        for (ArenaDefinition arena : arenas.getArenas()) {
            arenaData.put(arena.getId(), new MigrationBundle.Entry(
                arenas.getStoredConfiguration(arena)));
        }
        Map<String, MigrationBundle.Entry> configs = new LinkedHashMap<String, MigrationBundle.Entry>();
        for (String name : SHARED_CONFIGS) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) continue;
            if (!file.isFile() || file.length() > 1024 * 1024) {
                throw new IOException("Configuracao invalida ou maior que 1 MiB: " + name);
            }
            configs.put(name, new MigrationBundle.Entry(new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
        }
        return new MigrationBundle(serverId, arenaData, configs);
    }

    private Path backup(MigrationBundle bundle) throws IOException {
        Path directory = new File(plugin.getDataFolder(), "migration-backups").toPath();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "migration-", ".zip.tmp");
        Path destination = directory.resolve("migration-" + System.currentTimeMillis() + "-"
            + UUID.randomUUID() + ".zip");
        try {
            try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (Map.Entry<String, MigrationBundle.Entry> entry : bundle.arenas.entrySet()) {
                    add(archive, "arenas/" + entry.getKey() + ".yml", entry.getValue().text);
                }
                for (Map.Entry<String, MigrationBundle.Entry> entry : bundle.documents.entrySet()) {
                    add(archive, "configs/" + entry.getKey(), entry.getValue().text);
                }
                add(archive, "README.txt", "Export StarfinBedWars do servidor " + bundle.serverId
                    + ". Arquivos de mundo/template, credenciais, sessoes e inventarios nao inclusos."
                    + "\nA exportacao NAO exclui arquivos locais.\n");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination);
            }
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void notifySender(CommandSender sender, String message) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(sender instanceof Player) || ((Player) sender).isOnline()) sender.sendMessage(message);
        });
    }

    private static String safe(String value) {
        if (value == null) return "erro nao especificado";
        // Never echo SQL exception messages verbatim; drivers may embed connection URLs.
        return value.contains("jdbc:") || value.contains("password=")
            ? "falha de conexao SQL (veja configuracao e permissoes)" : value;
    }
}
