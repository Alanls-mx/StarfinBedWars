package com.starfinplugins.bedwars.stats;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Durable retry queue. The same match/player may be replayed safely after a crash. */
public final class StatsOutbox {
    private final Path directory;

    public StatsOutbox(Path directory) { this.directory = directory; }

    public Path write(List<MatchResult> results) throws IOException {
        if (results == null || results.isEmpty()) throw new IllegalArgumentException("Resultados vazios");
        UUID match = results.get(0).matchId;
        Properties properties = new Properties();
        properties.setProperty("match", match.toString());
        properties.setProperty("count", Integer.toString(results.size()));
        for (int i = 0; i < results.size(); i++) {
            MatchResult r = results.get(i);
            if (!match.equals(r.matchId)) throw new IllegalArgumentException("Partidas misturadas");
            properties.setProperty("player." + i, r.playerId + "|" + r.name + "|"
                + r.kills + "|" + r.finalKills + "|" + r.deaths + "|" + r.finalDeaths + "|" + r.beds
                + "|" + r.outcome + "|" + r.xp + "|" + (r.mode == null ? "" : r.mode)
                + "|" + r.completedAtMillis);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(match + ".properties");
        Path temporary = directory.resolve(match + "." + UUID.randomUUID() + ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                properties.store(stream, "StarfinBedWars pending match results");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    public List<Path> pending() throws IOException {
        if (!Files.exists(directory)) return Collections.emptyList();
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path path : stream) if (Files.isRegularFile(path)) files.add(path);
        }
        Collections.sort(files);
        return files;
    }

    public List<MatchResult> read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { properties.load(stream); }
        try {
            UUID match = UUID.fromString(properties.getProperty("match"));
            if (!path.getFileName().toString().equals(match + ".properties")) {
                throw new IllegalArgumentException("ID do arquivo nao corresponde a partida");
            }
            int count = Integer.parseInt(properties.getProperty("count"));
            if (count < 1 || count > 1000) throw new IllegalArgumentException("Quantidade invalida");
            List<MatchResult> results = new ArrayList<MatchResult>();
            for (int i = 0; i < count; i++) {
                String[] fields = properties.getProperty("player." + i).split("\\|", -1);
                // Both previous 8-field and 9-field outbox formats remain replayable.
                if (fields.length != 8 && fields.length != 9 && fields.length != 11) {
                    throw new IllegalArgumentException("Campos invalidos");
                }
                boolean updated = fields.length >= 9;
                results.add(new MatchResult(match, UUID.fromString(fields[0]), fields[1],
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[4]), updated ? Integer.parseInt(fields[5]) : 0,
                    Integer.parseInt(fields[updated ? 6 : 5]),
                    MatchResult.Outcome.valueOf(fields[updated ? 7 : 6]),
                    Long.parseLong(fields[updated ? 8 : 7]),
                    fields.length == 11 ? fields[9] : null,
                    fields.length == 11 ? Long.parseLong(fields[10])
                        : Files.getLastModifiedTime(path).toMillis()));
            }
            return results;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Outbox de estatisticas invalido: " + path.getFileName(), error);
        }
    }

    public void acknowledge(Path path) throws IOException { Files.delete(path); }
}
