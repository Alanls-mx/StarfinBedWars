package com.starfinplugins.bedwars.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class RankingPage {
    public final RankingSpec spec;
    public final List<Entry> entries;
    public final Entry viewer;
    public final long totalPlayers;

    public RankingPage(RankingSpec spec, List<Entry> entries, Entry viewer, long totalPlayers) {
        this.spec = spec;
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
        this.viewer = viewer;
        this.totalPlayers = totalPlayers;
    }

    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final long position, globalXp;
        public final double value;

        public Entry(UUID uuid, String name, long position, long globalXp, double value) {
            this.uuid = uuid;
            this.name = name;
            this.position = position;
            this.globalXp = globalXp;
            this.value = value;
        }
    }
}
