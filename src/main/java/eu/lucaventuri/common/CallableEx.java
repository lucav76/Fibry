package eu.lucaventuri.common;

@FunctionalInterface
public interface CallableEx<T, E extends Throwable> {
    T call() throws E;
}
