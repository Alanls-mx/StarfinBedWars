package com.starfinplugins.bedwars.reconnect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/** Persists only metadata. Match worlds and inventories are not crash recoverable. */
public final class ReconnectRepository {
    private final File file;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public ReconnectRepository(File folder, Logger logger) {
        this.file = new File(folder, "reconnect-sessions.yml");
        this.logger = logger;
    }

    public int countStaleSessions() throws IOException {
        if (!file.exists()) return 0;
        if (!file.isFile()) throw new IOException("reconnect-sessions.yml nao e arquivo regular.");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getInt("version", -1) != 1 || !config.isList("sessions")) {
            throw new IOException("Metadados de reconnect invalidos ou versao desconhecida.");
        }
        return config.getList("sessions").size();
    }

    public void save(Collection<ReconnectSession> sessions) {
        final String serialized = serialize(sessions);
        writer.submit(new Runnable() {
            @Override public void run() {
                Path target = file.toPath();
                Path temporary = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
                try {
                    Files.write(temporary, serialized.getBytes(StandardCharsets.UTF_8));
                    try {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException error) {
                    logger.log(Level.SEVERE, "Falha ao persistir metadados de reconnect.", error);
                }
            }
        });
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.warning("Gravacao de reconnect ainda pendente no desligamento.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warning("Gravacao de reconnect interrompida.");
        }
    }

    private static String serialize(Collection<ReconnectSession> sessions) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", 1);
        List<java.util.Map<String, Object>> rows = new ArrayList<java.util.Map<String, Object>>();
        for (ReconnectSession session : sessions) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
            row.put("player", session.getPlayerId().toString());
            row.put("match", session.getMatchId().toString());
            row.put("arena", session.getArenaId());
            row.put("team", session.getTeamId());
            row.put("disconnected-at", session.getDisconnectedAt());
            row.put("expires-at", session.getExpiresAt());
            row.put("life", session.getLifeState().name());
            row.put("snapshot-version", session.getSnapshot().getVersion());
            row.put("state", session.getState().name());
            rows.add(row);
        }
        config.set("sessions", rows);
        return config.saveToString();
    }
}
