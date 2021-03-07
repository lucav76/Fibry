package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.collections.ConstrainedMapLRU;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Registry aboe to associate message Id with CompletableFutures */
class MessageRegistry<T> {
    private static final AtomicLong nextMessageId = new AtomicLong();
    private final ConstrainedMapLRU<Long, CompletableFuture<T>> mapResults;

    static class IdAndFuture<T> {
        final long id;
        final CompletableFuture<T> future;

        IdAndFuture(long id, CompletableFuture<T> future) {
            this.id = id;
            this.future = future;
        }
    }

    MessageRegistry(int maxMessages) {
        mapResults = new ConstrainedMapLRU<>(maxMessages, entry -> entry.getValue().cancel(true));
    }

    IdAndFuture<T> getNewFuture() {
        long id = nextMessageId.incrementAndGet();
        var idAndFuture = new IdAndFuture<T>(id, new CompletableFuture<>());

        mapResults.put(id, idAndFuture.future);

        return idAndFuture;
    }

    boolean hasFutureOf(long messageId) {
        return mapResults.get(messageId) != null;
    }

    void completeFuture(long messageId, T value) {
        var future = mapResults.get(messageId);

        if (future != null) {
            future.complete(value);
        }
    }

    void completeExceptionally(long messageId, Throwable e) {
        var future = mapResults.get(messageId);

        if (future != null) {
            future.completeExceptionally(e);
        }
    }
}
