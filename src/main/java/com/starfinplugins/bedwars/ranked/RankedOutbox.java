package com.starfinplugins.bedwars.ranked;

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

/** Local crash retry journal. MySQL's match primary key makes replay safe. */
public final class RankedOutbox {
    private final Path directory;
    public RankedOutbox(Path directory) { this.directory = directory; }

    public Path write(RankedMatchSnapshot match) throws IOException {
        Files.createDirectories(directory);
        Properties p = new Properties();
        p.setProperty("match", match.matchId.toString()); p.setProperty("mode", match.mode);
        p.setProperty("arena", match.arenaId); p.setProperty("server", match.serverId);
        p.setProperty("instance", match.instanceId); p.setProperty("context", match.context);
        p.setProperty("winner", match.winnerTeam == null ? "" : match.winnerTeam);
        p.setProperty("draw", Boolean.toString(match.draw));
        p.setProperty("start", Long.toString(match.startedAt));
        p.setProperty("end", Long.toString(match.endedAt));
        p.setProperty("initial", Integer.toString(match.initialRating));
        p.setProperty("minimum", Integer.toString(match.minimumRating));
        p.setProperty("k-factor", Integer.toString(match.kFactor));
        p.setProperty("count", Integer.toString(match.members.size()));
        for (int i = 0; i < match.members.size(); i++) {
            RankedMatchSnapshot.Member member = match.members.get(i);
            p.setProperty("member." + i, member.playerId + "|" + member.name + "|"
                + member.teamId + "|" + member.abandoned);
        }
        Path target = directory.resolve(match.matchId + ".properties");
        Path temp = directory.resolve(match.matchId + "." + UUID.randomUUID() + ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) { p.store(stream, "Ranked result"); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
        return target;
    }

    public List<Path> pending() throws IOException {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<Path> found = new ArrayList<Path>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path path : files) if (Files.isRegularFile(path)) found.add(path);
        }
        Collections.sort(found);
        return found;
    }
    public RankedMatchSnapshot read(Path path) throws IOException {
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(path)) { p.load(stream); }
        try {
            UUID id = UUID.fromString(p.getProperty("match"));
            if (!path.getFileName().toString().equals(id + ".properties")) throw new IllegalArgumentException();
            int count = Integer.parseInt(p.getProperty("count"));
            if (count < 2 || count > 100) throw new IllegalArgumentException();
            List<RankedMatchSnapshot.Member> members = new ArrayList<RankedMatchSnapshot.Member>();
            for (int i = 0; i < count; i++) {
                String[] fields = p.getProperty("member." + i).split("\\|", -1);
                if (fields.length != 4) throw new IllegalArgumentException();
                members.add(new RankedMatchSnapshot.Member(UUID.fromString(fields[0]),
                    fields[1], fields[2], Boolean.parseBoolean(fields[3])));
            }
            String winner = p.getProperty("winner");
            return new RankedMatchSnapshot(id, p.getProperty("mode"), p.getProperty("arena"),
                p.getProperty("server"), p.getProperty("instance"), p.getProperty("context"),
                winner.isEmpty() ? null : winner, Long.parseLong(p.getProperty("start")),
                Long.parseLong(p.getProperty("end")), Boolean.parseBoolean(p.getProperty("draw")), members,
                Integer.parseInt(p.getProperty("initial")), Integer.parseInt(p.getProperty("minimum")),
                Integer.parseInt(p.getProperty("k-factor")));
        } catch (RuntimeException invalid) {
            throw new IOException("Resultado competitivo pendente invalido: " + path.getFileName(), invalid);
        }
    }
    public void acknowledge(Path path) throws IOException { Files.delete(path); }
}
