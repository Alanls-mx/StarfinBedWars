package com.starfinplugins.bedwars.gameplay;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Future network currency adapter. Implementations must make transactionId idempotent. */
public interface AbilityCurrencyProvider {
    boolean isAvailable();

    /** Reserves funds without crediting the ability. Completion runs off the Bukkit thread. */
    CompletionStage<Reservation> reserve(UUID playerId, int amount, UUID transactionId);

    interface Reservation {
        boolean confirmed();
        /** Finalize only after the MySQL ability transaction has committed. */
        CompletionStage<Boolean> commit();
        /** Release a reservation if the ability transaction fails. */
        CompletionStage<Void> cancel();
    }
}
