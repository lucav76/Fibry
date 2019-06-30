package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.concurrent.SignalingSingleRunnable;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

final class ActorUtils {
    private ActorUtils() { /* Static methods only */}

    static <T, R> void sendMessage(BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue, T message) {
        queue.add(Either3.right(message));
    }

    static <T, R> CompletableFuture<R> sendMessageReturn(BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue, T message) {
        MessageWithAnswer<T, R> mwr = new MessageWithAnswer<>(message);
        queue.add(Either3.other(mwr));

        return mwr.answers;
    }

    static <T, R> void execAsync(BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue, Runnable run) {
        queue.add(Either3.left(run));
    }

    static <T, R> void execAndWait(BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue, Runnable run) {
        SignalingSingleRunnable sr = SignalingSingleRunnable.of(run);

        queue.add(Either3.left(sr));
        Exceptions.log(sr::await);
    }

    static <T, R> CompletableFuture<Void> execFuture(BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue, Runnable run) {
        SignalingSingleRunnable sr = SignalingSingleRunnable.of(run);

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
