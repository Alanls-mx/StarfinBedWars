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

/** Per-field last-write-wins, crash-safe journal. ACK never removes a newer pending update. */
public final class PreferencesOutbox {
    public enum Kind { LANGUAGE, QUICK_BUY }
    private final Path directory;

    public PreferencesOutbox(Path directory) { this.directory = directory; }

    public synchronized void write(UUID player, Kind kind, String value) throws IOException {
        if (player == null || kind == null || value == null || value.length() > 2048) {
            throw new IllegalArgumentException("Preferencia invalida.");
        }
        Files.createDirectories(directory);
        Properties properties = new Properties();
        properties.setProperty("uuid", player.toString());
        properties.setProperty("kind", kind.name());
        properties.setProperty("value", value);
        properties.setProperty("revision", UUID.randomUUID().toString());
        Path target = file(player, kind);
        Path temp = Files.createTempFile(directory, "preference-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "StarfinBedWars preference outbox");
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    public List<Path> pending() throws IOException {
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<Path> result = new ArrayList<Path>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path path : paths) if (Files.isRegularFile(path)) result.add(path);
        }
        Collections.sort(result);
        return result;
    }

    public Pending read(Path path) throws IOException {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) { props.load(input); }
        try {
            UUID id = UUID.fromString(props.getProperty("uuid"));
            Kind kind = Kind.valueOf(props.getProperty("kind"));
            if (!path.getFileName().toString().equals(file(id, kind).getFileName().toString())) {
                throw new IllegalArgumentException("Arquivo e jogador divergentes");
            }
            String value = props.getProperty("value");
            String revision = props.getProperty("revision");
            UUID.fromString(revision);
            if (value == null || value.length() > 2048) throw new IllegalArgumentException("Valor invalido");
            if (kind == Kind.LANGUAGE && !PlayerPreferences.supportedLanguage(value)) {
                throw new IllegalArgumentException("Idioma invalido no outbox");
            }
            if (kind == Kind.QUICK_BUY && !value.equals(PlayerPreferences.encode(
                    PlayerPreferences.decode(value)))) {
                throw new IllegalArgumentException("Favoritos invalidos no outbox");
            }
            return new Pending(id, kind, value, revision);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Preferencia pendente invalida", error);
        }
    }

    public synchronized void acknowledge(Path path, Pending version) throws IOException {
        if (Files.isRegularFile(path) && read(path).revision.equals(version.revision)) {
            Files.delete(path);
        }
    }

    private Path file(UUID id, Kind kind) {
        return directory.resolve(id + "-" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".properties");
    }

    public static final class Pending {
        public final UUID uuid;
        public final Kind kind;
        public final String value, revision;
        private Pending(UUID uuid, Kind kind, String value, String revision) {
            this.uuid = uuid;
            this.kind = kind;
            this.value = value;
            this.revision = revision;
        }
    }
}
