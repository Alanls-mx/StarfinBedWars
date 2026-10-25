package com.starfinplugins.bedwars.stats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreferencesOutboxTest {
    @Test public void restartPreservesEditsAndOldAckDoesNotEraseNewUpdate() throws Exception {
        Path directory = Files.createTempDirectory("sbw-pref-journal-test");
        UUID id = UUID.randomUUID();
        PreferencesOutbox outbox = new PreferencesOutbox(directory);
        outbox.write(id, PreferencesOutbox.Kind.LANGUAGE, "en_US");
        PreferencesOutbox restarted = new PreferencesOutbox(directory);
        List<Path> files = restarted.pending();
        assertEquals(1, files.size());
        PreferencesOutbox.Pending old = restarted.read(files.get(0));
        assertEquals("en_US", old.value);
        outbox.write(id, PreferencesOutbox.Kind.LANGUAGE, "es_ES");
        restarted.acknowledge(files.get(0), old);
        assertEquals(1, restarted.pending().size());
        PreferencesOutbox.Pending latest = restarted.read(files.get(0));
        assertEquals("es_ES", latest.value);
        restarted.acknowledge(files.get(0), latest);
        assertTrue(restarted.pending().isEmpty());
        Files.delete(directory);
    }

    @Test public void separateFieldsNeverClobberEachOther() throws Exception {
        Path directory = Files.createTempDirectory("sbw-pref-fields-test");
        UUID id = UUID.randomUUID();
        PreferencesOutbox outbox = new PreferencesOutbox(directory);
        outbox.write(id, PreferencesOutbox.Kind.LANGUAGE, "pt_BR");
        outbox.write(id, PreferencesOutbox.Kind.QUICK_BUY, "wool,stone_sword");
        assertEquals(2, outbox.pending().size());
        for (Path path : outbox.pending()) {
            outbox.acknowledge(path, outbox.read(path));
        }
        Files.delete(directory);
    }
}
