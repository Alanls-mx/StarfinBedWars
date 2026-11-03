package com.starfinplugins.bedwars.tournament;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Crash retry journal; game_id makes MySQL replay idempotent. */
public final class TournamentOutbox {
    public static final class Result {
        public final UUID gameId, minecraftMatch;
        public final String reason, winningArenaTeam;
        public Result(UUID gameId, UUID minecraftMatch, String reason, String winningArenaTeam) {
            this.gameId = gameId; this.minecraftMatch = minecraftMatch;
            this.reason = reason; this.winningArenaTeam = winningArenaTeam;
        }
    }
    private final Path directory;
    public TournamentOutbox(Path directory) { this.directory = directory; }
    public Path write(Result result) throws IOException {
        Files.createDirectories(directory);
        Properties p = new Properties();
        p.setProperty("game", result.gameId.toString());
        p.setProperty("match", result.minecraftMatch.toString());
        p.setProperty("reason", result.reason);
        p.setProperty("winner", result.winningArenaTeam == null ? "" : result.winningArenaTeam);
        Path target = directory.resolve(result.gameId + ".properties");
        Path temp = directory.resolve(result.gameId + "." + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                OutputStream stream = Channels.newOutputStream(channel);
                p.store(stream, "Tournament result"); stream.flush(); channel.force(true);
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
        return target;
    }
    public List<Path> pending() throws IOException {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<Path> result = new ArrayList<Path>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path file : files) if (Files.isRegularFile(file)) result.add(file);
        }
        Collections.sort(result); return result;
    }
    public Result read(Path path) throws IOException {
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(path)) { p.load(input); }
        try {
            UUID game = UUID.fromString(p.getProperty("game"));
            if (!path.getFileName().toString().equals(game + ".properties"))
                throw new IllegalArgumentException("filename");
            String winner = p.getProperty("winner");
            return new Result(game, UUID.fromString(p.getProperty("match")),
                p.getProperty("reason"), winner.isEmpty() ? null : winner);
        } catch (RuntimeException invalid) { throw new IOException("Invalid tournament journal", invalid); }
    }
    public void acknowledge(Path path) throws IOException { Files.delete(path); }
}
