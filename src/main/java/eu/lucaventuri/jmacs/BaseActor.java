package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.functional.Either3;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class BaseActor<T, R, S> extends Exitable implements Consumer<T>, Function<T, R>, PartialActor<T, S>, SinkActor<S> {
    private final Queue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue;
    protected S state;

    protected BaseActor(Queue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue) {
        this.queue = queue;
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

    abstract void processMessages();
}
