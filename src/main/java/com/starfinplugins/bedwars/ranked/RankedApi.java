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
    Map<UUID, Integer> calculateRatingChanges(RankedMatchSnapshot snapshot,
                                               Map<UUID, Integer> ratings);
    void authorize(GameMatch match, Plugin issuer);
}
