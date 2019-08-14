package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

public interface SinkActorSingleMessage<S> extends AutoCloseable, Stateful<S> {
    public void askExit();

    public boolean isExiting();

    public void waitForExit();

    @Override
    default public void close() throws Exception {
        askExit();
    }

    public SinkActorSingleMessage<S> closeOnExit(AutoCloseable... closeables);
}
