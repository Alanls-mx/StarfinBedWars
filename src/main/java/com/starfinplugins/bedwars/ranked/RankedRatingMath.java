package com.starfinplugins.bedwars.ranked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure Java 8 pairwise Elo, normalized by the number of opposing teams. */
public final class RankedRatingMath {
    private RankedRatingMath() { }

    public static Map<UUID, Integer> changes(RankedMatchSnapshot match,
                                              Map<UUID, Integer> ratings, int kFactor) {
        if (match == null || ratings == null || kFactor < 1) throw new IllegalArgumentException();
        Map<String, List<RankedMatchSnapshot.Member>> teams = new HashMap<String, List<RankedMatchSnapshot.Member>>();
        for (RankedMatchSnapshot.Member member : match.members) {
            if (!ratings.containsKey(member.playerId) || ratings.get(member.playerId) < 0)
                throw new IllegalArgumentException("Rating ausente");
            teams.computeIfAbsent(member.teamId, key -> new ArrayList<RankedMatchSnapshot.Member>()).add(member);
        }
        Map<String, Double> averages = new HashMap<String, Double>();
        for (Map.Entry<String, List<RankedMatchSnapshot.Member>> team : teams.entrySet()) {
            long total = 0;
            for (RankedMatchSnapshot.Member member : team.getValue()) total += ratings.get(member.playerId);
            averages.put(team.getKey(), (double) total / team.getValue().size());
        }
        Map<UUID, Integer> result = new HashMap<UUID, Integer>();
        for (RankedMatchSnapshot.Member member : match.members) {
            double sum = 0;
            for (String other : teams.keySet()) {
                if (other.equals(member.teamId)) continue;
                double expected = 1.0 / (1.0 + Math.pow(10.0,
                    (averages.get(other) - averages.get(member.teamId)) / 400.0));
                double actual = member.abandoned ? 0.0 : match.draw ? 0.5
                    : member.teamId.equals(match.winnerTeam) ? 1.0
                    : other.equals(match.winnerTeam) ? 0.0 : 0.5;
                sum += actual - expected;
            }
            result.put(member.playerId, (int) Math.round(kFactor * sum / (teams.size() - 1)));
        }
        return Collections.unmodifiableMap(result);
    }
}
