package eu.lucaventuri.common;

/** Object that can be merged with another one of th esame type */
public interface Mergeable {
    String getKey();

    Mergeable mergeWith(Mergeable m);

    /** Tracking the execution of mergeable messages can be tricky, as they are executed in baches.
     * This method provide an entry point, but the operation is considered optioanl. */
    default void signalMessageProcessed() {
        /** Suggested implementation: a list of CountDownLatches, with each latch associated to one of the initial messages;
         * Initial messages contain a list of one latch, merged messages have more than one
         * */
    }
}
