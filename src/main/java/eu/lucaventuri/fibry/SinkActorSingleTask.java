package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ExtendedClosable;
import eu.lucaventuri.common.Stateful;

import java.util.concurrent.TimeUnit;

/**
 * A Sink Actor is an actor that is not processing any messages, though it can run code.
 * This class is used when the caller cannot execute any particular code after the actor is created, typically because the actor will die after the first task, or the same task will be repeated several times (e.g. scheduler).
 */
public interface SinkActorSingleTask<S> extends AutoCloseable, Stateful<S>, ExtendedClosable {
    public void askExit();

    public boolean isExiting();

    public void waitForExit();

    public void askExitAndWait();

    public void askExitAndWait(long timeout, TimeUnit unit);

    @Override
    default public void close() throws Exception {
        askExit();
    }

    @Override
    public SinkActorSingleTask<S> closeOnExit(AutoCloseable... closeables);
}
