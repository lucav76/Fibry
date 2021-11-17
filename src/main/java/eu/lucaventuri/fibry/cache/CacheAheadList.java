package eu.lucaventuri.fibry.cache;

import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.MessageReceiver;
import eu.lucaventuri.fibry.ReceivingActor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Used to pre-cache, ahead of time, some resource that might be slow to get.
 * For example, if you need to retrieve some random numbers or some sequences from a remote service,
 * you might want to get a batch in advance, so that it is ready for your users as quickly as possible,
 * and keep that batch full.
 * The minSizeRefill parameter needs to be tuned based on the expected amount of time needed to retrieve a
 * new batch of values, and the number of requests per second
 */
public class CacheAheadList<R> implements Supplier<R> {
    private final ReceivingActor<CacheOperationMessage<R>, List<R>, List<R>> cacheActor;
    private final Actor<Integer, Void, Void> refillActor;
    private final AtomicLong refillExpiration = new AtomicLong(0);
    private final int timeoutMs;
    private final Supplier<List<R>> emergencySupplier;
    private final AtomicLong numAsyncRefills = new AtomicLong();
    private final AtomicLong numSyncRefills = new AtomicLong();
    private final AtomicLong numItemsRetrieved = new AtomicLong();
    private final int numRetries;
    private final int minSizeRefill;
    private final List<R> list;

    public enum CacheOperation {GET, PUT}

    private static abstract class CacheOperationMessage<R> {
        final CacheOperation operation;
        final List<R> data;

        private CacheOperationMessage(CacheOperation operation, List<R> data) {
            this.operation = operation;
            this.data = data;

            assert (operation == CacheOperation.GET && data == null) || (operation == CacheOperation.PUT && data != null && !data.isEmpty());
        }
    }

    public static class CacheOperationGet<R> extends CacheOperationMessage<R> {
        final int numValues;

        private CacheOperationGet(int messagesToRetrieve) {
            super(CacheOperation.GET, null);

            this.numValues = messagesToRetrieve;
        }
    }

    public static class CacheOperationPut<R> extends CacheOperationMessage<R> {
        private CacheOperationPut(List<R> data) {
            super(CacheOperation.PUT, data);
        }
    }

    public CacheAheadList(BiConsumer<Integer, Consumer<List<R>>> simplifiedRefiller, int minSizeRefill, int timeoutMs, Supplier<List<R>> emergencySupplier, int numRetries) {
        this(new ArrayList<>(), null, simplifiedRefiller, minSizeRefill, timeoutMs, emergencySupplier, numRetries);
    }


    /**
     * @param list List that will contain the values; it could be a persistent list, for example using MapDB
     * @param listCommitter Optional runnable to commit the value, for example if you use write-ahead-logs in MapDB
     * @param simplifiedRefiller BiConsumer that can refill the list; it will simple have to send more data to the consumer provided (which is this actor);
     * The parameters accepted are the suggested number of elements to produce and the actor where to send these elements
     * @param minSizeRefill If the list had less than these elements, an asynchronous refill will be triggered
     * @param timeoutMs Timeout, in ms, allowed for the refill
     * @param emergencySupplier In case that the refill cannot be performed, the emergency supplier could supply "less good value", to keep the clients happy
     * @param numRetries Number of retries
     */
    public CacheAheadList(List<R> list, Runnable listCommitter, BiConsumer<Integer, Consumer<List<R>>> simplifiedRefiller, int minSizeRefill, int timeoutMs, Supplier<List<R>> emergencySupplier, int numRetries) {
        Objects.requireNonNull(simplifiedRefiller, "Refiller cannot be null");
        assert numRetries >= 0;

        this.numRetries = numRetries;
        this.timeoutMs = timeoutMs;
        this.emergencySupplier = emergencySupplier;
        this.minSizeRefill = minSizeRefill;
        this.list = list;

        this.cacheActor = ActorSystem.anonymous().initialState(list).newReceivingActorWithReturn((rec, msg) -> {
            if (msg.operation == CacheOperation.PUT) {
                list.addAll(msg.data);
                commit(listCommitter);
                numItemsRetrieved.addAndGet(msg.data.size());

                return null;
            }
            if (msg.operation == CacheOperation.GET) {
                List<R> result = new ArrayList<>();

                for (int i = 0; i < ((CacheOperationGet<R>) msg).numValues; i++) {
                    R value = retrieveOne(rec, listCommitter);

                    if (value != null)
                        result.add(value);
                }

                return result;
            }

            return null;
        });

        refillActor = ActorSystem.anonymous().newActor(numMessages -> simplifiedRefiller.accept(numMessages, data -> cacheActor.sendMessage(new CacheOperationPut<>(data))));

        asyncRefill();
    }

    private R retrieveOne(MessageReceiver<CacheOperationMessage<R>> rec, Runnable listCommitter) {
        int numCalls = numRetries + 1;

        while (list.isEmpty() && numCalls >= 0) {
            syncRefill(rec, listCommitter);
            numCalls--;
        }

        if (list.isEmpty() && emergencySupplier != null) {
            var emergencyList = emergencySupplier.get();

            if (emergencyList != null && !emergencyList.isEmpty()) {
                list.addAll(emergencyList);
                numItemsRetrieved.addAndGet(emergencyList.size());
            }
        }

        if (list.isEmpty()) {
            return null;
        }

        if (list.size() <= minSizeRefill)
            asyncRefill();

        R value = list.remove(0);
        commit(listCommitter);
        return value;
    }

    private void syncRefill(MessageReceiver<CacheOperationMessage<R>> rec, Runnable listCommitter) {
        assert list.isEmpty();
        assert rec != null;
        long expiration = refillExpiration.get();
        CacheOperationPut<R> msg = null;

        if (expiration != 0) {
            long now = System.currentTimeMillis();

            while (list.isEmpty() && msg == null && now < expiration) {
                msg = rec.receive(CacheOperationPut.class, t -> true, expiration);
            }

            if (msg != null)
                refillExpiration.set(0);
        }

        if (msg == null) {
            refillActor.sendMessage(getSuggestedRefillSize());

            msg = rec.receive(CacheOperationPut.class, t -> true, timeoutMs);
        }

        if (msg != null && msg.data != null) {
            list.addAll(msg.data);
            commit(listCommitter);
            numItemsRetrieved.addAndGet(msg.data.size());
        }

        numSyncRefills.incrementAndGet();
    }

    private int getSuggestedRefillSize() {
        return Math.max(Math.max(1, minSizeRefill / 4), minSizeRefill - list.size());
    }

    private void asyncRefill() {
        refillExpiration.set(System.currentTimeMillis() + timeoutMs);
        refillActor.sendMessage(getSuggestedRefillSize());
        numAsyncRefills.incrementAndGet();
    }

    private void commit(Runnable listCommitter) {
        if (listCommitter != null)
            listCommitter.run();
    }

    @Override
    public R get() {
        try {
            return getAsync().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println(e.toString());

            return null;
        }
    }

    public CompletableFuture<R> getAsync() {
        return cacheActor.sendMessageReturn(new CacheOperationGet<>(1))
                .thenApply(list -> list == null || list.isEmpty() ? null : list.get(0));
    }

    public Optional<R> getOptional() {
        try {
            return getOptionalAsync().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println(e.toString());

            return Optional.empty();
        }
    }

    public CompletableFuture<Optional<R>> getOptionalAsync() {
        return cacheActor.sendMessageReturn(new CacheOperationGet<>(1))
                .thenApply(list -> list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    public List<R> get(int num) {
        try {
            return getAsync(num).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println(e.toString());

            return null;
        }
    }

    public CompletableFuture<List<R>> getAsync(int num) {
        return cacheActor.sendMessageReturn(new CacheOperationGet<>(num));
    }

    public long getNumAsyncRefills() {
        return numAsyncRefills.get();
    }

    public long getNumSyncRefills() {
        return numSyncRefills.get();
    }

    public long getNumItemsRetrieved() {
        return numItemsRetrieved.get();
    }
}
