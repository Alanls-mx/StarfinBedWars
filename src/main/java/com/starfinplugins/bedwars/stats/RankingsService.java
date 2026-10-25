package com.starfinplugins.bedwars.stats;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded worker-owned cache. The caller executes load only on the stats I/O executor. */
public final class RankingsService {
    private final Map<String, Cached> cache = new HashMap<String, Cached>();
    private final long ttlMillis;

    public RankingsService(int cacheSeconds) {
        if (cacheSeconds < 5 || cacheSeconds > 3600) throw new IllegalArgumentException("TTL invalido");
        ttlMillis = cacheSeconds * 1000L;
    }

    public RankingPage load(MySqlStatsRepository repository, RankingSpec spec, UUID viewer)
            throws SQLException {
        String key = spec.cacheKey() + "/" + viewer;
        long now = System.currentTimeMillis();
        Cached found = cache.get(key);
        if (found != null && found.expiresAt > now) return found.page;
        RankingPage page = repository.ranking(spec, viewer);
        if (cache.size() >= 128) cache.clear();
        cache.put(key, new Cached(page, now + ttlMillis));
        return page;
    }

    public void invalidate() { cache.clear(); }

    private static final class Cached {
        final RankingPage page;
        final long expiresAt;
        Cached(RankingPage page, long expiresAt) {
            this.page = page;
            this.expiresAt = expiresAt;
        }
    }
}
