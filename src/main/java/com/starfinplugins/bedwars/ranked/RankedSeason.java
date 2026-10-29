package com.starfinplugins.bedwars.ranked;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Immutable database snapshot of a competitive season. */
public final class RankedSeason {
    public enum State { DRAFT, SCHEDULED, ACTIVE, CLOSING, FINALIZING, FINISHED, CANCELLED }
    public final String id, name, description, resetType, resetSource;
    public final State state;
    public final long startsAt, endsAt, version;
    public final int initialRating, placementRequired;
    public final double resetFactor;
    public final Set<String> modes;

    public RankedSeason(String id, String name, String description, State state,
                        long startsAt, long endsAt, long version, int initialRating,
                        int placementRequired, String resetType, String resetSource,
                        double resetFactor, Set<String> modes) {
        this.id = id; this.name = name; this.description = description; this.state = state;
        this.startsAt = startsAt; this.endsAt = endsAt; this.version = version;
        this.initialRating = initialRating; this.placementRequired = placementRequired;
        this.resetType = resetType; this.resetSource = resetSource; this.resetFactor = resetFactor;
        this.modes = Collections.unmodifiableSet(new HashSet<String>(modes));
    }
    public boolean accepting(String mode, long now) {
        return state == State.ACTIVE && now >= startsAt && now < endsAt && modes.contains(mode);
    }
}
