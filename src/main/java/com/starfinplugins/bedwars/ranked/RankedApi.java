package com.starfinplugins.bedwars.ranked;

import com.starfinplugins.bedwars.domain.match.GameMatch;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/** Public, read-only async competitive API plus server-side admission capability. */
public interface RankedApi {
    CompletableFuture<RankedProfile> getCompetitiveProfile(UUID playerId, String mode);
    CompletableFuture<Integer> getRating(UUID playerId, String mode);
    CompletableFuture<String> getDivision(UUID playerId, String mode);
    CompletableFuture<Integer> getPlacementProgress(UUID playerId, String mode);
    CompletableFuture<List<RankedResult>> getCompetitiveMatchResult(UUID matchId);
    CompletableFuture<List<RankedResult>> getCompetitiveHistory(UUID playerId,
                                                                 String context, int page);
    CompletableFuture<RankedSeason> getActiveSeason();
    CompletableFuture<RankedSeason> getSeason(String id);
    CompletableFuture<List<RankedSeason>> getSeasons(int limit, int page);
    CompletableFuture<List<RankedStanding>> getLeaderboard(String seasonId, String mode,
                                                            int pageSize, int page);
    CompletableFuture<List<RankedStanding>> getLeaderboard(String seasonId, String mode,
                                                            String divisionId, int pageSize, int page);
    CompletableFuture<RankedStanding> getRankedPosition(UUID playerId, String seasonId, String mode);
    CompletableFuture<List<RankedStanding>> getSeasonHistory(UUID playerId, int page);
    CompletableFuture<List<RankedSeasonReward>> getSeasonRewards(UUID playerId, String seasonId);
    CompletableFuture<Boolean> claimSeasonReward(UUID playerId, String seasonId,
                                                  String mode, String rewardId);
    Map<UUID, Integer> calculateRatingChanges(RankedMatchSnapshot snapshot,
                                               Map<UUID, Integer> ratings);
    void authorize(GameMatch match, Plugin issuer);
}
