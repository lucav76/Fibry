package eu.lucaventuri.jmacs;

import eu.lucaventuri.collections.ClassifiedMap;
import eu.lucaventuri.common.SystemUtils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Provide the functionality to read messages in order and to retrieve them by class and filtering.
 * Thread safety is achieved using the blocking queue, while receive() uses the ClassifiedMap.
 * To improve performance, a lock-less blocking queue "with nodes" should be implemented to join both the retrieval options
 */
public class MessageSilo<T> {
    private final LinkedBlockingQueue<T> queue;
    private final ClassifiedMap map = new ClassifiedMap();

    public MessageSilo(LinkedBlockingQueue<T> queue) {
        this.queue = queue;
    }

    public T readMessage() {
        if (map.isEmpty())
            return retrieveFromQueue();

        return map.removeHead();
    }

    private T retrieveFromQueue() {
        while (true) {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                SystemUtils.sleep(1);
            }
        }
    }

    /**
     * Used to receive specific messages; please notice that delivery order is not guaranteed.
     * This method can be slow, so it should be used with care, on actors that process a single request and that are not supposed to receive many messages.
     * Please consider using sendMessageReturn() if appropriate.
     */
    public <E extends T> E receive(Class<E> clz, Predicate<E> filter) {
        if (map.isEmpty())
            return receiveFromQueue(clz, filter);

        E message = receiveFromMap(clz, filter);

        return message != null ? message : receiveFromQueue(clz, filter);
    }

    private <E extends T> E receiveFromMap(Class<E> clz, Predicate<E> filter) {
        return map.scanAndChoose(clz, filter);
    }

    // FIXME: adds timeout
    private <E extends T> E receiveFromQueue(Class<E> clz, Predicate<E> filter) {
        while (true) {
            T message = retrieveFromQueue();

            if (clz.isInstance(message)) {
                if (filter.test((E) message)) {
                    return (E) message;
                }
            }

            map.addToTail(message);
        }
    }
}
