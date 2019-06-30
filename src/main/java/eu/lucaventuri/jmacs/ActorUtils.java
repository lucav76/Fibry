package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.concurrent.SignalingSingleRunnable;
import eu.lucaventuri.functional.Either;

import java.util.concurrent.*;

final class ActorUtils {
    private ActorUtils() { /* Static methods only */}

    static <T> void sendMessage(BlockingDeque<Either<Runnable, T>> queue, T message) {
        queue.add(Either.right(message));
    }

    static <T> void execAsync(BlockingDeque<Either<Runnable, T>> queue, Runnable run) {
        queue.add(Either.left(run));
    }

    static <T> void execAndWait(BlockingDeque<Either<Runnable, T>> queue, Runnable run) {
        SignalingSingleRunnable sr = SignalingSingleRunnable.of(run);

        queue.add(Either.left(sr));
        Exceptions.log(sr::await);
    }

    static <T> CompletableFuture<Void> execFuture(BlockingDeque<Either<Runnable, T>> queue, Runnable run) {
        SignalingSingleRunnable sr = SignalingSingleRunnable.of(run);

        queue.add(Either.left(sr));

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
}
