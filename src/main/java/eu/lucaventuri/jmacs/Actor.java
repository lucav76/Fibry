package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

/** Super simple actor, that can process messages of type T, or execute Runnables inside its thread */
public class Actor<T, R> extends Exitable implements Consumer<T>, Function<T, R> {
    protected final BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue;
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;

    Actor(Consumer<T> actorLogic, Function<T, R> actorLogicReturn) {
        this(actorLogic, actorLogicReturn, new LinkedBlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>>());
    }

    Actor(Consumer<T> actorLogic, Function<T, R> actorLogicReturn, BlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> queue) {
        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answers.complete(actorLogicReturn.apply(mwr.message));
        this.queue = queue;
    }

    Runnable processMessages() {
        return () -> {
            while (!isExiting()) {
                Exceptions.log(() -> {
                    var message = queue.takeFirst();

                    message.ifEither(Runnable::run, actorLogic::accept, actorLogicReturn::accept);
                });
            }

            notifyFinished();
        };
    }

    public void sendMessage(T message) {
        ActorUtils.sendMessage(queue, message);
    }

    public CompletableFuture<R> sendMessageReturn(T message) {
        return ActorUtils.sendMessageReturn(queue, message);
    }

    @Override
    public void accept(T message) {
        sendMessage(message);
    }

    @Override
    public R apply(T message) {
        try {
            return sendMessageReturn(message).get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
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
