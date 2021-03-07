package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.collections.ConstrainedMapLRU;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Registry aboe to associate message Id with CompletableFutures */
class MessageRegistry<T> {
    private static final AtomicLong nextMessageId = new AtomicLong();
    private final ConstrainedMapLRU<Long, FutureData<T>> mapResults;

    //should the future send also the other data? How can I get them from whenComplete?
    //semderactor and original id?

    static class FutureData<T> {
        final CompletableFuture<T> future;
        final TcpActorSender<T> refActor;

        public FutureData(CompletableFuture<T> future, TcpActorSender<T> refActor) {
            this.future = future;
            this.refActor = refActor;
        }
    }

    static class IdAndFuture<T> {
        final long id;
        final FutureData<T> data;

        IdAndFuture(long id, CompletableFuture<T> future, TcpActorSender<T> refActor) {
            this.id = id;
            this.data = new FutureData<>(future, refActor);
        }
    }

    MessageRegistry(int maxMessages) {
        mapResults = new ConstrainedMapLRU<>(maxMessages, entry -> entry.getValue().future.cancel(true));
    }

    IdAndFuture<T> getNewFuture(TcpActorSender<T> refActor) {
        long id = nextMessageId.incrementAndGet();
        var idAndFuture = new IdAndFuture<>(id, new CompletableFuture<>(), refActor);

        mapResults.put(id, idAndFuture.data);

        return idAndFuture;
    }

    boolean hasFutureOf(long messageId) {
        return mapResults.get(messageId) != null;
    }

    void completeFuture(long messageId, T value) {
        var futureData = mapResults.get(messageId);

        if (futureData != null) {
            futureData.future.complete(value);
        }
    }

    void completeExceptionally(long messageId, Throwable e) {
        var futureData = mapResults.get(messageId);

        if (futureData != null) {
            futureData.future.completeExceptionally(e);
        }
    }

    TcpActorSender<T> getSender(long messageId) {
        return mapResults.get(messageId).refActor;
    }
}
