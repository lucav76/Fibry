package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.functional.Either;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/** Super simple actor, that can process messages of type T, or execute Runnables inside its thread */
public class Actor<T> extends Exitable {
    protected final BlockingDeque<Either<Runnable, T>> queue;

    Actor(Consumer<T> actorLogic, boolean useFibers) {
        this(actorLogic, new LinkedBlockingDeque<Either<Runnable, T>>(), useFibers);
    }

    Actor(Consumer<T> actorLogic, BlockingDeque<Either<Runnable, T>> queue, boolean useFibers) {
        this.queue = queue;

        new Thread(processMessage(queue, actorLogic)).start();
    }

    private Runnable processMessage(BlockingDeque<Either<Runnable, T>> queue, Consumer<T> actorLogic) {
        return () -> {
            while (!isExiting()) {
                Exceptions.log(() -> {
                    var message = queue.takeFirst();

                    message.ifEither(Runnable::run, actorLogic::accept);
                });
            }

            notifyFinished();
        };
    }


    public void execAsync(Runnable run) {
        ActorUtils.execAsync(queue, run);
    }

    public void execAndWait(Runnable run) {
        ActorUtils.execAndWait(queue, run);
    }

    public CompletableFuture<Void> execFuture(Runnable run) {
        return ActorUtils.execFuture(queue, run);
    }
}
