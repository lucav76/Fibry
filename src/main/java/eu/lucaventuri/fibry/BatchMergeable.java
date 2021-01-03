package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Mergeable;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Override
    public void awaitBatchProcessing() throws InterruptedException {
        latches.get(0).await();
    }

    @Override
    public boolean awaitBatchProcessing(long timeout, TimeUnit unit) throws InterruptedException {
        return latches.get(0).await(timeout, unit);
    }
}
