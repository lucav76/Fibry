package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

/**
 * A Sink Actor is an actor that is not processing any messages, though it can run code.
 * This class is used when the caller cannot execute any particular code after the actor is created, typically because the actor will die after the first task, or the same task will be repeated several times (e.g. scheduler).
 */
public interface SinkActorSingleTask<S> extends AutoCloseable, Stateful<S> {
    public void askExit();

    public boolean isExiting();

    public void waitForExit();

    @Override
    default public void close() throws Exception {
        askExit();
    }

    public SinkActorSingleTask<S> closeOnExit(AutoCloseable... closeables);
}
