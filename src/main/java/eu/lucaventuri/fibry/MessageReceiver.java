package eu.lucaventuri.fibry;

import java.util.function.Predicate;

public interface MessageReceiver<T> {
    public T readMessage();
    public <E extends T> E receive(Class<E> clz, Predicate<E> filter);
}
