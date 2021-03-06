package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.collections.ConstrainedMapLRU;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Registry aboe to associate message Id with CompletableFutures */
public class MessageRegistry<T> {
    private final AtomicLong nextMessageId = new AtomicLong();
    private final ConstrainedMapLRU<Long, CompletableFuture<T>> mapResults;

    public static class IdAndFuture<T> {
        public final long id;
        public final CompletableFuture<T> future;

        public IdAndFuture(long id, CompletableFuture<T> future) {
            this.id = id;
            this.future = future;
        }
    }

    public MessageRegistry(int maxMessages) {
        mapResults = new ConstrainedMapLRU<Long, CompletableFuture<T>>(maxMessages, entry -> entry.getValue().cancel(true));
    }

    public IdAndFuture<T> getNewFuture() {
        long id = nextMessageId.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();

        mapResults.put(id, future);

        return new IdAndFuture<>(id, future);
    }

    public boolean hasFutureOf(long messageId) {
        return mapResults.get(messageId) != null;
    }

    public void completeFuture(long messageId, T value) {
        var future = mapResults.get(messageId);

        if (future != null) {
            future.complete(value);
        }
    }

    public void completeExceptionally(long messageId, Throwable e) {
        var future = mapResults.get(messageId);

        if (future != null) {
            future.completeExceptionally(e);
        }
    }
}
