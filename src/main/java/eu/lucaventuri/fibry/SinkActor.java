package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.RunnableEx;
import eu.lucaventuri.common.Stateful;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A Sink Actor is an actor that is not processing any messages, though it can run code. In practice, this is an executor.
 */
public interface SinkActor<S> extends SinkActorSingleTask<S>, Executor {
    public void execAsync(Runnable worker);

    @Override
    default public void execute(Runnable command) {
        execAsync(command);
    }

    public void execAsyncStateful(Consumer<Stateful<S>> worker);

    default public <E extends Exception> void execAsyncEx(RunnableEx<E> worker) {
        execAsync(Exceptions.silentRunnable(worker));
    }

    public void execAsyncState(Consumer<S> worker);

    public void execAndWaitState(Consumer<S> worker);

    /**
     * Queue a request to exit, that will be processed after all the messages currently in the queue
     */
    default boolean sendPoisonPill() {
        try {
            execAsync(this::askExit);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
