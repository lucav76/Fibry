package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.RunnableEx;

public interface SinkActor<S> extends SinkActorSingleMessage {
    public void execAsync(Runnable worker);

    default public <E extends Exception> void execAsyncEx(RunnableEx<E> worker) {
        execAsync(Exceptions.silentRunnable(worker));
    }

    /** Queue a request to exit, that will be processed after all the messages currently in the queue */
    default boolean sendPoisonPill() {
        try {
            execAsync(this::askExit);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
