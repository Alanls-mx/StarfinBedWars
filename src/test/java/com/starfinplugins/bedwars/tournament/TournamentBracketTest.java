package com.starfinplugins.bedwars.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class TournamentBracketTest {
    @Test public void sizesByesAndReproducibleDraw() {
        for (int count : new int[] {2, 3, 4, 5, 6, 8, 16, 32, 64}) {
            List<TournamentBracket.Entry> entries = new ArrayList<TournamentBracket.Entry>();
            for (int i = 1; i <= count; i++)
                entries.add(new TournamentBracket.Entry(new UUID(0L, i), i));
            List<TournamentBracket.Pair> seeded = TournamentBracket.plan(entries,
                TournamentBracket.Draw.SEEDED, 1L);
            int size = 1; while (size < count) size <<= 1;
            assertEquals(size - 1, seeded.size());
            int byes = 0;
            for (TournamentBracket.Pair pair : seeded) if (pair.round == 1 && pair.bye()) byes++;
            assertEquals(size - count, byes);
            List<TournamentBracket.Pair> randomA = TournamentBracket.plan(entries,
                TournamentBracket.Draw.RANDOM, 12345L);
            List<TournamentBracket.Pair> randomB = TournamentBracket.plan(entries,
                TournamentBracket.Draw.RANDOM, 12345L);
            for (int i = 0; i < randomA.size(); i++) {
                assertEquals(randomA.get(i).first, randomB.get(i).first);
                assertEquals(randomA.get(i).second, randomB.get(i).second);
            }
        }
    }
}
