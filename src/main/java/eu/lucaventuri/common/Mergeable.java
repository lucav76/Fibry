package eu.lucaventuri.common;

import eu.lucaventuri.fibry.BatchMergeable;

import java.util.concurrent.TimeUnit;

/** Object that can be merged with another one of th esame type */
public interface Mergeable {
    String getKey();

    Mergeable mergeWith(Mergeable m);

    /** Merges the signals */
    default Mergeable mergeSignalWith(Mergeable m) {
        /** Not implemented. Suggested implementation: inherit from BatchMergeable, which uses a list of CountDownLatches  */
        return this;
    }

    /** Tracking the execution of mergeable messages can be tricky, as they are executed in baches.
     * This method provide an entry point, but the operation is considered optioanl. */
    default void signalMessageProcessed() {
        /** Not implemented. Suggested implementation: inherit from BatchMergeable, which uses a list of CountDownLatches  */
    }

    /**
     * Wait indefinitely, until the message is processed (post merge).
     * To be clear, there is no return value availabe, as the message can be merged, but when this function returns,
     * it means that the batch has been processed
     */
    default void awaitBatchProcessing() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    /**
     * Wait until the message is processed (post merge) or the timeout is expired.
     * To be clear, there is no return value availabe, as the message can be merged, but when this function returns,
     * it means that the batch has been processed.
     *
     * @return true if the message has been processed in the specified amount of time
     */
    default boolean awaitBatchProcessing(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }
}
