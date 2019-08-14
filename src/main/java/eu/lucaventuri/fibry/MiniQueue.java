package eu.lucaventuri.fibry;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public interface MiniQueue<T> {
    boolean add (T message);
    default boolean offer (T message) {
        try {
            return add(message);
        } catch(Exception e) {
            return false;
        }
    }
    T take () throws InterruptedException ;
    T poll(long timeout, TimeUnit unit) throws InterruptedException;
    void clear();
    int size();
    T peek();

    /** LinkedBlockingDeque implementing MiniQueue */
    static <T> MiniQueue<T> blocking() {
        class MiniQueuedBlockingQueue<T> extends LinkedBlockingDeque<T> implements MiniQueue<T> {};

        return new MiniQueuedBlockingQueue<>();
    }

    /** MiniQueue dropping all the messages */
    static <T> MiniQueue<T> dropping() {
        return new MiniQueue<T>() {
            @Override
            public boolean add(T message) {
                return false;
            }

            @Override
            public T take() throws InterruptedException {
                throw new UnsupportedOperationException("This queue drops all the message and so cannot provide the take() operation ");
            }

            @Override
            public T poll(long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException("This queue drops all the message and so cannot provide the poll() operation ");
            }

            @Override
            public void clear() {
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public T peek() {
                return null;
            }
        };
    }
}
