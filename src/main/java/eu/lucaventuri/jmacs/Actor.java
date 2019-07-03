package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/** Super simple actor, that can process messages of type T, or execute Runnables inside its thread */
public class Actor<T, R, S> extends Exitable implements Consumer<T>, Function<T, R> {
    protected final BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue;
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;
    protected S state;

    Actor(Consumer<T> actorLogic, Function<T, R> actorLogicReturn, BlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answers.complete(actorLogicReturn.apply(mwr.message));
        this.queue = queue;
        this.state = initialState;
    }

    void processMessages() {
        while (!isExiting()) {
            Exceptions.log(() -> {
                var message = queue.takeFirst();

                message.ifEither(cns -> cns.accept(state), actorLogic::accept, actorLogicReturn::accept);
            });
        }

        notifyFinished();
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

    public void execAsync(Consumer<S> worker) {
        ActorUtils.execAsync(queue, worker);
    }

    public void execAndWait(Consumer<S> worker) {
        ActorUtils.execAndWait(queue, worker);
    }

    public CompletableFuture<Void> execFuture(Consumer<S> worker) {
        return ActorUtils.execFuture(queue, worker);
    }
}
