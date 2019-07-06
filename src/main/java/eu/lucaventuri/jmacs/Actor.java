package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME: Use MessageSilo

/** Super simple actor, that can process messages of type T, or execute Runnables inside its thread */
public class Actor<T, R, S> extends BaseActor<T, R> implements PartialActor<T, S>, SinkActor<S>, Consumer<T>, Function<T, R> {
    protected final BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue;
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;
    protected S state;

    /**
     * Constructor creating an acotor that process messages without returning any value
     * @param actorLogic Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     */
    Actor(Consumer<T> actorLogic, BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        Function<T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);
        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answers.complete(tmpLogicReturn.apply(mwr.message));
        this.queue = queue;
        this.state = initialState;
    }

    /**
     * Constructor creating an acotor that process messages without returning any value
     * @param actorLogicReturn Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     */
    Actor(Function<T, R> actorLogicReturn, BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = mwr -> mwr.answers.complete(actorLogicReturn.apply(mwr.message));
        this.queue = queue;
        this.state = initialState;
    }


    void processMessages() {
        while (!isExiting()) {
            Exceptions.log(() -> {
                var message = queue.takeFirst();

                message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
            });
        }

        notifyFinished();
    }

    /** Asynchronously sends a message to the actor */
    public void sendMessage(T message) {
        ActorUtils.sendMessage(queue, message);
    }

    /** Asynchronously sends a message to the actor; theresult can be accessed using the CompletableFuture */
    public CompletableFuture<R> sendMessageReturn(T message) {
        return ActorUtils.sendMessageReturn(queue, message);
    }

    /** Synchronously sends a message and waits for the result. This can take some time because of the context switch and of possible messages in the queue  */
    public R sendMessageReturnWait(T message, R valueOnError) {
        try {
            return ActorUtils.sendMessageReturn(queue, message).get();
        } catch (InterruptedException | ExecutionException e) {
            return valueOnError;
        }
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

    /** Asynchronously executes some logic in the actor; the parameter supplied is the actor itself. */
    public void execAsync(Consumer<PartialActor<T, S>> worker) {
        ActorUtils.execAsync(queue, worker);
    }

    /** Synchronously executes some logic in the actor; the parameter supplied is the actor itself. */
    public void execAndWait(Consumer<PartialActor<T, S>> worker) {
        ActorUtils.execAndWait(queue, worker);
    }

    /** Asynchronously executes some logic in the actor; the parameter supplied is the actor itself. The returned Future can be used to check if the task has been completed. */
    public CompletableFuture<Void> execFuture(Consumer<PartialActor<T, S>> worker) {
        return ActorUtils.execFuture(queue, worker);
    }

    /** Asynchronously executes some logic in the actor. */
    public void execAsync(Runnable worker) {
        ActorUtils.execAsync(queue, state -> worker.run());
    }

    /** Synchronously executes some logic in the actor. */
    public void execAndWait(Runnable worker) {
        ActorUtils.execAndWait(queue, state -> worker.run());
    }

    /** Asynchronously executes some logic in the actor. The returned Future can be used to check if the task has been completed. */
    public CompletableFuture<Void> execFuture(Runnable worker) {
        return ActorUtils.execFuture(queue, state -> worker.run());
    }

    /** Queue a request to exit, that will be processed after all the messages corrently in the queue */
    public void sendPoisonPill() {
        execAsync( state -> askExit());
    }

    /** @return the state of the actor */
    public S getState() {
        return state;
    }

    /** @return the length of the queue */
    public long getQueueLength() { return queue.size(); }
}
