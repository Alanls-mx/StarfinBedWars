package com.starfinplugins.bedwars.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Pure bracket planner. Positions are stable once persisted. */
public final class TournamentBracket {
    public enum Draw { RANDOM, SEEDED }
    public static final class Entry {
        public final UUID teamId;
        public final int seed;
        public Entry(UUID teamId, int seed) {
            if (teamId == null || seed < 1) throw new IllegalArgumentException("Invalid entry");
            this.teamId = teamId; this.seed = seed;
        }
    }
    public static final class Pair {
        public final UUID first, second;
        public final int round, slot;
        Pair(UUID first, UUID second, int round, int slot) {
            this.first = first; this.second = second; this.round = round; this.slot = slot;
        }
        public boolean bye() { return (first == null) != (second == null); }
    }
    private TournamentBracket() { }

    public static List<Pair> plan(List<Entry> registrations, Draw draw, long randomSeed) {
        if (registrations == null || registrations.size() < 2 || registrations.size() > 64
                || draw == null) throw new IllegalArgumentException("Team count must be 2..64");
        Set<UUID> unique = new HashSet<UUID>();
        for (Entry entry : registrations) if (entry == null || !unique.add(entry.teamId))
            throw new IllegalArgumentException("Duplicate team");
        int size = 1;
        while (size < registrations.size()) size <<= 1;
        List<Entry> ordered = new ArrayList<Entry>(registrations);
        if (draw == Draw.SEEDED) ordered.sort(Comparator.comparingInt((Entry e) -> e.seed)
            .thenComparing(e -> e.teamId));
        else {
            ordered.sort(Comparator.comparing(e -> e.teamId));
            Collections.shuffle(ordered, new Random(randomSeed));
        }
        List<Integer> positions = seedPositions(size);
        UUID[] slots = new UUID[size];
        for (int i = 0; i < ordered.size(); i++) slots[positions.indexOf(i + 1)] = ordered.get(i).teamId;
        List<Pair> bracket = new ArrayList<Pair>();
        int rounds = Integer.numberOfTrailingZeros(size);
        for (int round = 1; round <= rounds; round++) {
            int matches = size >> round;
            for (int slot = 0; slot < matches; slot++) {
                bracket.add(new Pair(round == 1 ? slots[slot * 2] : null,
                    round == 1 ? slots[slot * 2 + 1] : null, round, slot));
            }
        }
        return Collections.unmodifiableList(bracket);
    }

    private static List<Integer> seedPositions(int size) {
        List<Integer> result = new ArrayList<Integer>(); result.add(1); result.add(2);
        for (int width = 4; width <= size; width <<= 1) {
            List<Integer> next = new ArrayList<Integer>();
            for (int seed : result) { next.add(seed); next.add(width + 1 - seed); }
            result = next;
        }
        return result;
    }
}
