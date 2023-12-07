package eu.lucaventuri.fibry;

import eu.lucaventuri.concurrent.AntiFreeze;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generator is like a lazy Iterable, so it can no know implicitly if the elements are over.
 * In general, the generator should be able to be iterated multiple times
 */
public interface Generator<T> extends Iterable<T> {
    enum State {
        /** It is not known yet if there are other elements available */
        WAITING,
        /** There are other elements available */
        GENERATING,
        /** All the elements have been produced */
        FINISHED;
    }

    interface Yielder<T> {
        void yield(T element);
    }

    /** This producer is the simplest, but the performance are suboptimal */
    interface GeneratorProducer<T> {
        /** Calls yield as many times as needed to add the items, then simply exit the function **/
        void produceAllItems(Yielder<T> yielder);
    }

    /**
     * This producer can reach top speed, however the last element must be returned, not yielded; returning null is not allowed.
     */
    interface AdvancedGeneratorProducer<T> {
        /**
         * Produces all the items, yielding all of them except the last one, which will be returned
         *
         * @param yielder Yielder, to provide the elements
         * @return the last element (must be != null)
         */
        T produceAllItems(Yielder<T> yielder);
    }

    default Stream<T> toStream() {
        return streamFromIterable(this);
    }

    /**
     * Transforms a blocking queue in a generator; the difference being that we will explicitly says when the data are over.
     * This is an advanced method, and callers should preferably use fromProducer() or fromAdvancedProducer()
     */
    static <T> Iterator<T> fromQueue(BlockingQueue<T> queue, AtomicReference<State> stateRef, final boolean maxThroughput) {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                State state;

                // Waiting to understand if there is an element or not
                while ((state = stateRef.get()) == State.WAITING && queue.isEmpty()) {
                    if (!maxThroughput)
                        Thread.yield();
                }

                return state == State.GENERATING || !queue.isEmpty();
            }

            @Override
            public T next() {
                while (true) {
                    if (stateRef.get() == State.FINISHED && queue.isEmpty())
                        throw new NoSuchElementException();

                    try {
                        T elem = queue.poll(10, TimeUnit.MILLISECONDS);

                        // In case of null, it will retry and eventually throw an error in case of problems

                        if (elem != null)
                            return elem;
                    } catch (InterruptedException e) {
                        // Just retry
                    }
                }
            }
        };
    }

    /**
     * Simplest way to create a generator, but it can be slow if the queue size is small, so a queue size of 100+ is recommended if performance are not good
     */
    static <T> Generator<T> fromProducer(GeneratorProducer<T> producer, int queueSize) {
        return fromProducer(producer, queueSize, false);
    }

    /**
     * Simplest way to create a generator, but it can be slow if the queue size is small, so a queue size of 100+ is recommended if performance are not good
     */
    static <T> Generator<T> fromProducer(GeneratorProducer<T> producer, int queueSize, boolean maxThroughput) {
        return fromProducer(producer, queueSize, maxThroughput, 0, 0);
    }

    /**
     * Simplest way to create a generator, but it can be slow if the queue size is small, so a queue size of 100+ is recommended if performance are not good
     */
    static <T> Generator<T> fromProducer(GeneratorProducer<T> producer, int queueSize, boolean maxThroughput, int itemTimeoutMs, int fullTimeoutMs) {
        assert queueSize >= 1;

        return () -> {
            AtomicReference<State> stateRef = new AtomicReference<>(State.WAITING);
            BlockingQueue<T> queue = new LinkedBlockingDeque<>(queueSize);

            Stereotypes.def().runOnce(() -> {
                AntiFreeze frz = fullTimeoutMs <=0 ? null : new AntiFreeze(itemTimeoutMs, fullTimeoutMs);

                try {
                    producer.produceAllItems(elem -> {
                        safeOffer(queue, elem);
                        if (frz != null)
                            frz.notifyActivity();
                    });
                } finally {
                    stateRef.set(State.FINISHED);
                    if (frz != null)
                        frz.notifyFinished();
                }
            });

            return fromQueue(queue, stateRef, maxThroughput);
        };
    }

    /**
     * Simplest way to create a generator, but it can be slow if the queue size is small, so a queue size of 100+ is recommended if performance are not good
     */
    static <T> Generator<T> fromParallelProducers(Supplier<GeneratorProducer<T>> producerSupplier, int numProducers, int queueSize) {
        return fromParallelProducers(producerSupplier, numProducers, queueSize, false);
    }

    /**
     * Simplest way to create a generator, but it can be slow if the queue size is small, so a queue size of 100+ is recommended if performance are not good
     */
    static <T> Generator<T> fromParallelProducers(Supplier<GeneratorProducer<T>> producerSupplier, int numProducers, int queueSize, boolean maxThroughput) {
        assert queueSize >= 1;

        return () -> {
            AtomicReference<State> stateRef = new AtomicReference<>(State.WAITING);
            BlockingQueue<T> queue = new LinkedBlockingDeque<>(queueSize);
            CountDownLatch latch = new CountDownLatch(numProducers);
            AtomicInteger numElements = new AtomicInteger();

            for (int i = 0; i < numProducers; i++) {
                Stereotypes.def().runOnce(() -> {
                    try {
                        producerSupplier.get().produceAllItems(elem -> {
                            safeOffer(queue, elem);
                            numElements.incrementAndGet();
                        });
                        latch.countDown();
                        System.out.println("Counting down at " + numElements.get());

                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                        }
                    } finally {
                        stateRef.set(State.FINISHED);
                        System.out.println("Finished at " + numElements.get());
                    }
                });
            }

            return fromQueue(queue, stateRef, maxThroughput);
        };
    }

    /**
     * This is the way to create a fast generator, with the small inconvenience that the last element should be returned, not yielded.
     * Failing to do so and returning null, will eventually result in a NoSuchElementException
     */
    static <T> Generator<T> fromAdvancedProducer(AdvancedGeneratorProducer<T> producer, int queueSize) {
        return fromAdvancedProducer(producer, queueSize, false);
    }

    /**
     * This is the way to create a fast generator, with the small inconvenience that the last element should be returned, not yielded.
     * Failing to do so and returning null, will eventually result in a NoSuchElementException
     */
    static <T> Generator<T> fromAdvancedProducer(AdvancedGeneratorProducer<T> producer, int queueSize, boolean maxThroughput) {
     return fromAdvancedProducer(producer, queueSize, maxThroughput, 0, 0);
    }

    /**
     * This is the way to create a fast generator, with the small inconvenience that the last element should be returned, not yielded.
     * Failing to do so and returning null, will eventually result in a NoSuchElementException
     */
    static <T> Generator<T> fromAdvancedProducer(AdvancedGeneratorProducer<T> producer, int queueSize, boolean maxThroughput, int itemTimeoutMs, int fullTimeoutMs) {
        assert queueSize >= 1;

        return () -> {
            AtomicReference<State> stateRef = new AtomicReference<>(State.WAITING);
            BlockingQueue<T> queue = new LinkedBlockingDeque<>(queueSize);

            Stereotypes.def().runOnce(() -> {
                AntiFreeze frz = fullTimeoutMs <=0 ? null : new AntiFreeze(itemTimeoutMs, fullTimeoutMs);

                try {
                    T lastElement = producer.produceAllItems(elem -> {
                        stateRef.set(State.GENERATING);
                        safeOffer(queue, elem);
                        if (frz != null)
                            frz.notifyActivity();
                    });
                    offerLastElement(stateRef, queue, lastElement);
                    if (frz != null)
                        frz.notifyActivity();
                } finally {
                    stateRef.set(State.FINISHED);
                    if (frz != null)
                        frz.notifyFinished();
                }
            });

            return fromQueue(queue, stateRef, maxThroughput);
        };
    }

    /**
     * This creates the fastest generator (slightly faster than advanced generators), with two small inconveniences:
     * - the last element should be returned, not yielded
     * - the generator needs to produce at least one element (the one that is returned)
     * Failing to do so and returning null, will eventually result in a NoSuchElementException
     */
    static <T> Generator<T> fromNonEmptyAdvancedProduce(AdvancedGeneratorProducer<T> producer, int queueSize) {
        return fromNonEmptyAdvancedProduce(producer, queueSize, false);
    }

    /**
     * This creates the fastest generator (slightly faster than advanced generators), with two small inconveniences:
     * - the last element should be returned, not yielded
     * - the generator needs to produce at least one element (the one that is returned)
     * Failing to do so and returning null, will eventually result in a NoSuchElementException
     */
    static <T> Generator<T> fromNonEmptyAdvancedProduce(AdvancedGeneratorProducer<T> producer, int queueSize, boolean maxThroughput) {
        assert queueSize >= 1;

        return () -> {
            AtomicReference<State> stateRef = new AtomicReference<>(State.GENERATING);
            BlockingQueue<T> queue = new LinkedBlockingDeque<>(queueSize);

            Stereotypes.def().runOnce(() -> {
                try {
                    T lastElement = producer.produceAllItems(elem -> {
                        safeOffer(queue, elem);
                    });
                    offerLastElement(stateRef, queue, lastElement);
                } finally {
                    stateRef.set(State.FINISHED);
                }
            });

            return fromQueue(queue, stateRef, maxThroughput);
        };
    }

    static <T> void offerLastElement(AtomicReference<State> stateRef, BlockingQueue<T> queue, T lastElement) {
        assert lastElement != null || stateRef.get() == State.WAITING;

        if (lastElement != null) {
            stateRef.set(State.WAITING);
            safeOffer(queue, lastElement);
        }
    }

    static <T> void safeOffer(BlockingQueue<T> queue, T elem) {
        while (true) {
            try {
                queue.offer(elem, Integer.MAX_VALUE, TimeUnit.SECONDS);
                break;
            } catch (InterruptedException e) {
                // just retry
            }
        }
    }

    static <T> Stream<T> streamFromIterator(Iterator<T> iter) {
        Spliterator<T>
                spliterator = Spliterators
                .spliteratorUnknownSize(iter, 0);

        return StreamSupport.stream(spliterator, false);
    }

    static <T> Stream<T> streamFromIterable(Iterable<T> iterable) {
        return streamFromIterator(iterable.iterator());
    }
}
