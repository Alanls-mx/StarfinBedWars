package com.starfinplugins.bedwars.ranked;

/** A persisted cosmetic reward definition and the viewer's claim state. */
public final class RankedSeasonReward {
    public final String seasonId, id, mode, pt, en, es, state;
    public final int minimumRating, maximumPosition, minimumGames;
    public RankedSeasonReward(String seasonId, String id, String mode, String pt, String en,
            String es, String state, int minimumRating, int maximumPosition, int minimumGames) {
        this.seasonId = seasonId; this.id = id; this.mode = mode;
        this.pt = pt; this.en = en; this.es = es; this.state = state;
        this.minimumRating = minimumRating; this.maximumPosition = maximumPosition;
        this.minimumGames = minimumGames;
    }
    public String name(String language) {
        return "en_US".equals(language) ? en : "es_ES".equals(language) ? es : pt;
    }
}
