package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.functional.Either3;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class BaseActor<T, R, S> extends Exitable implements Consumer<T>, Function<T, R>, PartialActor<T, S>, SinkActor<S> {
    protected final MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue;
    protected S state;
    protected final Consumer<S> finalizer;
    protected final List<AutoCloseable> closeOnExit = new Vector<>();

    public void setDrainMessagesOnExit(boolean drainMessagesOnExit) {
        this.drainMessagesOnExit = drainMessagesOnExit;
    }

    protected volatile boolean drainMessagesOnExit = true;

    BaseActor(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, CloseStrategy closeStrategy) {
        this.queue = queue;
        this.finalizer = finalizer;
        this.sendPoisonPillWhenExiting = true;

        if (closeStrategy != null)
            this.closeStrategy = closeStrategy;
    }

    /** Asynchronously sends a message to the actor; theresult can be accessed using the CompletableFuture */
    public CompletableFuture<R> sendMessageReturn(T message) {
        if (isExiting())
            return getrCompletableFutureWhenExiting();

        return ActorUtils.sendMessageReturn(queue, message);
    }

    private CompletableFuture getrCompletableFutureWhenExiting() {
        assert isExiting();
        CompletableFuture cf = new CompletableFuture();

        if (isFinished())
            cf.completeExceptionally(new IllegalStateException("Actor terminated"));
        else
            cf.completeExceptionally(new IllegalStateException("Actor exiting"));

        return cf;
    }

    /** Synchronously sends a message and waits for the result. This can take some time because of the context switch and of possible messages in the queue */
    public R sendMessageReturnWait(T message, R valueOnError) {
        try {
            if (isExiting())
                return valueOnError;

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
        if (!isExiting())
            ActorUtils.execAsync(queue, worker);
    }

    /** Synchronously executes some logic in the actor; the parameter supplied is the actor itself. */
    public void execAndWait(Consumer<PartialActor<T, S>> worker) {
        if (!isExiting())
            ActorUtils.execAndWait(queue, worker);
    }

    /** Asynchronously executes some logic in the actor; the parameter supplied is the actor itself. The returned Future can be used to check if the task has been completed. */
    public CompletableFuture<Void> execFuture(Consumer<PartialActor<T, S>> worker) {
        if (isExiting())
            return getrCompletableFutureWhenExiting();

        return ActorUtils.execFuture(queue, worker);
    }

    /** Asynchronously executes some logic in the actor. */
    public void execAsync(Runnable worker) {
        if (!isExiting())
            ActorUtils.execAsync(queue, state -> worker.run());
    }

    /** Synchronously executes some logic in the actor. */
    public void execAndWait(Runnable worker) {
        if (!isExiting())
            ActorUtils.execAndWait(queue, state -> worker.run());
    }

    /** Asynchronously executes some logic in the actor. The returned Future can be used to check if the task has been completed. */
    public CompletableFuture<Void> execFuture(Runnable worker) {
        if (isExiting())
            return getrCompletableFutureWhenExiting();

        return ActorUtils.execFuture(queue, state -> worker.run());
    }

    /**
     * Queue a request to exit, that will be processed after all the messages currently in the queue
     *
     * @return
     */
    @Override
    public boolean sendPoisonPill() {
        try {
            execAsync(state -> askExit());

            return true;
        } catch (IllegalStateException state) {
            return false;
        }
    }

    /** @return the state of the actor */
    public S getState() {
        return state;
    }

    /** @return the length of the queue */
    public long getQueueLength() {
        return queue.size();
    }

    public final void processMessages() {
        while (!isExiting())
            Exceptions.log(this::takeAndProcessSingleMessage);

        finalOperations();
    }

    protected abstract void takeAndProcessSingleMessage() throws Exception;

    public BaseActor<T, R, S> closeOnExit(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables)
            closeOnExit.add(closeable);

        return this;
    }

    protected void finalOperations() {
        if (finalizer != null)
            finalizer.accept(state);

        if (drainMessagesOnExit)
            queue.clear();

        for (AutoCloseable closeable : closeOnExit) {
            SystemUtils.close(closeable);
        }

        notifyFinished();
    }

    @Override
    // Just return a more specific type
    public BaseActor<T, R, S> sendMessage(T message) {
        if (!isExiting())
            ActorUtils.sendMessage(queue, message);
        return this;
    }
}
