package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface MiniFibryQueue<T, R, S> extends MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> {
    public static <T, R, S> MiniFibryQueue dropping() {
        return new MiniFibryQueue<T,R,S>() {
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
}
