package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.RunnableEx;

public interface SinkActor<S> {
    public void execAsync(Runnable worker);

    default public <E extends Exception> void execAsyncEx(RunnableEx<E> worker) {
        execAsync(Exceptions.silentRunnable(worker));
    }

    public S getState();

    public void askExit();

    public boolean isExiting();

    public void waitForExit();
}
