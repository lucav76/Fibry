package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.RunnableEx;

public interface SinkActorSingleMessage<S> extends AutoCloseable {
    public S getState();

    public void askExit();

    public boolean isExiting();

    public void waitForExit();

    @Override
    default public void close() throws Exception {
        askExit();
    }

    public SinkActorSingleMessage<S> closeOnExit(AutoCloseable... closeables);
}
