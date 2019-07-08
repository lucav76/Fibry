package eu.lucaventuri.fibry;

import eu.lucaventuri.collections.ClassifiedMap;
import eu.lucaventuri.common.SystemUtils;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Provide the functionality to read messages in order and to retrieve them by class and filtering.
 * Thread safety is achieved using the blocking queue, while receive() uses the ClassifiedMap.
 * To improve performance, a lock-less blocking queue "with nodes" should be implemented to get both the retrieval behaviors from a single object
 */
public class MessageBag<T, CONV> extends AbstractQueue<T> implements MessageReceiver<T> {
    private final BlockingQueue<T> queue;
    private final ClassifiedMap map = new ClassifiedMap();
    private final Function<T, CONV> converter;

    public MessageBag(BlockingQueue<T> queue, Function<T, CONV> converter) {
        this.queue = queue;
        this.converter = converter;
    }

    public MessageBag(BlockingQueue<T> queue) {
        this.queue = queue;
        this.converter = null;
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

    /**
     * Used to receive specific messages; please notice that delivery order is not guaranteed.
     * This method can be slow, so it should be used with care, on actors that process a single request and that are not supposed to receive many messages.
     * Please consider using sendMessageReturn() if appropriate.
     */
    public <K, E extends CONV> CONV receiveAndConvert(Class<E> clz, Predicate<E> filter) {
        if (map.isEmpty())
            return receiveFromQueueAndConvert(clz, filter);

        CONV message = receiveFromMapAndConvert(clz, filter, converter);

        return message != null ? message : receiveFromQueueAndConvert(clz, filter);
    }

    private <K, E extends K> K receiveFromMapAndConvert(Class<E> clz, Predicate<E> filter, Function<T, K> converter) {
        return map.scanAndChooseAndConvert(clz, filter, converter);
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

            if (converter != null)
                map.addToTailConverted(message, converter.apply(message).getClass());
            else
                map.addToTail(message);
        }
    }

    private <E extends CONV> CONV receiveFromQueueAndConvert(Class<E> clz, Predicate<E> filter) {
        while (true) {
            T message = retrieveFromQueue();
            CONV messageConverted = converter.apply(message);

            if (clz.isInstance(messageConverted)) {
                if (filter.test((E) messageConverted)) {
                    return (E) messageConverted;
                }
            }

            if (converter != null)
                map.addToTailConverted(message, converter.apply(message).getClass());
            else
                map.addToTail(message);
        }
    }

    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException("Iterator not available in " + this.getClass().getName());
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean offer(T element) {
        return queue.offer(element);
    }

    @Override
    public T poll() {
        return retrieveFromQueue(); // We want it always blocking
    }

    @Override
    public T peek() {
        if (map.isEmpty())
            return queue.peek();

        return map.peekHead();
    }
}
