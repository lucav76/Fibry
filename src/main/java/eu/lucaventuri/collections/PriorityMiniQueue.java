package eu.lucaventuri.collections;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import eu.lucaventuri.fibry.MiniQueue;

/**
 * Priority queue; this could be useful for weighted work-stealing actor pools, because the poison pill could receive
 * a lower priority, and stay at the bottom;
 * Insertion order is not preserved, the only way to get a specific order is using the priority explicitly
 */
public class PriorityMiniQueue<T> implements MiniQueue<T> {
    private final PriorityBlockingQueue<PriorityMiniQueue.Prioritizable<T>> backingQueue = new PriorityBlockingQueue<>();
    private final double defaultPriority;

    public PriorityMiniQueue(double defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public PriorityMiniQueue() {
        this(0);
    }

    public static class Prioritizable<T> implements Comparable<Prioritizable<T>> {
        private final T data;
        private final double priority;
        public Prioritizable(T data, double priority) {
            this.data = data;
            this.priority = priority;
        }

        @Override
        public int compareTo(Prioritizable<T> o) {
            double diff = priority - o.priority;

            return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
        }
    }

    @Override
    public boolean add(T message) {
        return backingQueue.add(new Prioritizable<>(message, defaultPriority));
    }

    public boolean add(T message, double priority) {
        return backingQueue.add(new Prioritizable<>(message, priority));
    }

    @Override
    public T take() throws InterruptedException {
        return backingQueue.take().data;
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        Prioritizable<T> message = backingQueue.poll(timeout, unit);

        return message == null ? null : message.data;
    }

    @Override
    public void clear() {
        backingQueue.clear();
    }

    @Override
    public int size() {
        return backingQueue.size();
    }

    @Override
    public T peek() {
        Prioritizable<T> message = backingQueue.peek();

        return message == null ? null : message.data;
    }
}
