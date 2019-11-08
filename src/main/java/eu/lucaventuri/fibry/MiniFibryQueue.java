package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface MiniFibryQueue<T, R, S> extends MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> {
    public static <T, R, S> MiniFibryQueue<T, R, S> dropping() {
        return new MiniFibryQueue<T, R, S>() {
            @Override
            public boolean add(Either3<Consumer<S>, T, MessageWithAnswer<T, R>> message) {
                return false;
            }

            @Override
            public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> take() throws InterruptedException {
                throw new UnsupportedOperationException("This queue drops all the message and so cannot provide the take() operation ");
            }

            @Override
            public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> poll(long timeout, TimeUnit unit) throws InterruptedException {
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
            public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> peek() {
                return null;
            }
        };
    }

    /**
     * Queue delegating to another queue. Used for Asynchronous actors, to create the queue on the constructor
     */
    public static class MiniFibryQueueDelegate<T, R, S> implements MiniFibryQueue<T, R, S> {
        private volatile MiniFibryQueue<T, R, S> queue = null;

        public void setQueue(MiniFibryQueue<T, R, S> queue) {
            this.queue = queue;
        }

        @Override
        public boolean add(Either3<Consumer<S>, T, MessageWithAnswer<T, R>> message) {
            return queue.add(message);
        }

        @Override
        public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> take() throws InterruptedException {
            return queue.take();
        }

        @Override
        public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        @Override
        public void clear() {
            queue.clear();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public Either3<Consumer<S>, T, MessageWithAnswer<T, R>> peek() {
            return queue.peek();
        }
    }
}
