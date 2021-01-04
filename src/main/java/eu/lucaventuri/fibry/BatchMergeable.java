package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Mergeable;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.*;

/** Mergeable meant to work for batches */
public abstract class BatchMergeable implements Mergeable {
    private final List<CountDownLatch> latches = new Vector<>(List.of(new CountDownLatch(1)));

    /** It only merges the latches */
    @Override
    public BatchMergeable mergeSignalWith(Mergeable m) {
        if (!(m instanceof BatchMergeable))
            throw new IllegalArgumentException("Expecting an object of type BatchMergeable");

        latches.addAll(((BatchMergeable) m).latches);

        return this;
    }

    @Override
    public void signalMessageProcessed() {
        for (var latch : latches)
            latch.countDown();
    }

    private CountDownLatch getOriginalLatch() {
        return latches.get(0);
    }

    @Override
    public void awaitBatchProcessing() throws InterruptedException {
        getOriginalLatch().await();
    }

    @Override
    public boolean awaitBatchProcessing(long timeout, TimeUnit unit) throws InterruptedException {
        return getOriginalLatch().await(timeout, unit);
    }

    public Future<Void> asFuture() {
        return new Future<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return getOriginalLatch().getCount() == 0;
            }

            @Override
            public Void get() throws InterruptedException {
                awaitBatchProcessing();
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException {
                awaitBatchProcessing(timeout, unit);
                return null;
            }
        };
    }
}
