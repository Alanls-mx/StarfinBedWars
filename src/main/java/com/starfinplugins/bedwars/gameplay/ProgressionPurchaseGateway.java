package com.starfinplugins.bedwars.gameplay;

import java.util.UUID;
import java.util.function.Consumer;

/** Purchase contract. Currency is charged only after a durable MySQL reservation. */
public interface ProgressionPurchaseGateway {
    enum Result { UNAVAILABLE, INSUFFICIENT_FUNDS, CONFLICT, COMMITTED, FAILED,
        RECONCILIATION_REQUIRED }

    final class Request {
        public final UUID transactionId, playerId;
        public final ScopedProgress.Kind kind;
        public final String entryId, purchaseScope, mode;
        public final int expectedLevel, nextLevel, price;

        public Request(UUID transactionId, UUID playerId, ScopedProgress.Kind kind,
                       String entryId, String purchaseScope, String mode, int expectedLevel,
                       int nextLevel, int price) {
            if (transactionId == null || playerId == null || kind == null || price < 0
                    || nextLevel != expectedLevel + 1 || nextLevel < 1 || nextLevel > 5)
                throw new IllegalArgumentException("Tentativa de compra invalida");
            ScopedProgress.key(kind, entryId, purchaseScope);
            ScopedProgress.key(kind, entryId, mode);
            this.transactionId = transactionId;
            this.playerId = playerId;
            this.kind = kind;
            this.entryId = entryId;
            this.purchaseScope = purchaseScope;
            this.mode = mode;
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
