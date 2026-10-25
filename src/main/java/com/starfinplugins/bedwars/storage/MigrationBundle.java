package com.starfinplugins.bedwars.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable copy of local metadata: no credentials, live worlds or session data. */
public final class MigrationBundle {
    public final String serverId;
    public final Map<String, Entry> arenas;
    public final Map<String, Entry> documents;

    public MigrationBundle(String serverId, Map<String, Entry> arenas, Map<String, Entry> documents) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Identificador do servidor invalido.");
        }
        this.serverId = serverId;
        this.arenas = Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(arenas));
        this.documents = Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(documents));
    }

    public static final class Entry {
        public final String text, sha256;

        public Entry(String text) {
            if (text == null || text.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
                throw new IllegalArgumentException("Arquivo ausente ou maior que 1 MiB.");
            }
            this.text = text;
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(64);
                for (byte b : hash) hex.append(String.format("%02x", b & 0xff));
                sha256 = hex.toString();
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 indisponivel", impossible);
            }
        }
    }
}
