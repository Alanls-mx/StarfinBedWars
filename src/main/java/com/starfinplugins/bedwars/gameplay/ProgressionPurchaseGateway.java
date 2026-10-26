package com.starfinplugins.bedwars.gameplay;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Future network-currency integration point. An implementation must atomically
 * check the expected level, debit real currency and persist the new entitlement
 * (or compensate the debit on failure). The transaction ID makes retries
 * idempotent across BungeeCord servers. Never report success before persistence.
 */
public interface ProgressionPurchaseGateway {
    enum Result { UNAVAILABLE, INSUFFICIENT_FUNDS, CONFLICT, COMMITTED, FAILED }

    final class Request {
        public final UUID transactionId, playerId;
        public final ScopedProgress.Kind kind;
        public final String entryId, purchaseScope;
        public final int expectedLevel, nextLevel, price;

        public Request(UUID transactionId, UUID playerId, ScopedProgress.Kind kind,
                       String entryId, String purchaseScope, int expectedLevel,
                       int nextLevel, int price) {
            if (transactionId == null || playerId == null || kind == null || price < 0
                    || nextLevel != expectedLevel + 1 || nextLevel < 1 || nextLevel > 5)
                throw new IllegalArgumentException("Tentativa de compra invalida");
            ScopedProgress.key(kind, entryId, purchaseScope);
            this.transactionId = transactionId;
            this.playerId = playerId;
            this.kind = kind;
            this.entryId = entryId;
            this.purchaseScope = purchaseScope;
            this.expectedLevel = expectedLevel;
            this.nextLevel = nextLevel;
            this.price = price;
        }
    }

    void purchase(Request request, Consumer<Result> completed);

    static ProgressionPurchaseGateway unavailable() {
        return (request, completed) -> completed.accept(Result.UNAVAILABLE);
    }
}
