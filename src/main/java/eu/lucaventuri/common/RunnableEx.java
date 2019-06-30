package eu.lucaventuri.common;

@FunctionalInterface
public interface RunnableEx<E extends Throwable> {
    void run() throws E;
}
