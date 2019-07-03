package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.concurrent.SignalingSingleConsumer;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

final class ActorUtils {
    private ActorUtils() { /* Static methods only */}

    static <T, R, S> void sendMessage(BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        queue.add(Either3.right(message));
    }

    static <T, R, S> CompletableFuture<R> sendMessageReturn(BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        MessageWithAnswer<T, R> mwr = new MessageWithAnswer<>(message);
        queue.add(Either3.other(mwr));

        return mwr.answers;
    }

    static <T, R, S> void execAsync(BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        queue.add(Either3.left(worker));
    }

    static <T, R, S> void execAndWait(BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        SignalingSingleConsumer<S> sc = SignalingSingleConsumer.of(worker);

        queue.add(Either3.left(sc));
        Exceptions.log(sc::await);
    }

    static <T, R, S> CompletableFuture<Void> execFuture(BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        SignalingSingleConsumer<S> sr = SignalingSingleConsumer.of(worker);

        queue.add(Either3.left(sr));

        return new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return sr.isDone();
            }

            @Override
            public Void get() throws InterruptedException, ExecutionException {
                sr.await();
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                sr.await(timeout, unit);
                return null;
            }
        };
    }

    static <T, R> Function<T, R> discardingToReturning(Consumer<T> actorLogic) {
        return message -> {
            actorLogic.accept(message);

            return null;
        };
    }

    static <T, R> Consumer<T> returningToDiscarding(Function<T, R> actorLogic) {
        return actorLogic::apply;
    }
}
