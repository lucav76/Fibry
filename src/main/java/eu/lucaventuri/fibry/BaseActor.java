package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.Stateful;
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
    protected volatile boolean drainMessagesOnExit = true;
    protected final int pollTimeoutMs;

    BaseActor(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        this.queue = queue;
        this.finalizer = finalizer;
        this.sendPoisonPillWhenExiting = true;
        this.pollTimeoutMs = pollTimeoutMs;

        if (closeStrategy != null)
            this.closeStrategy = closeStrategy;
    }

    /** Asynchronously sends a message to the actor; theresult can be accessed using the CompletableFuture */
    public CompletableFuture<R> sendMessageReturn(T message) {
        if (isExiting())
            return getCompletableFutureWhenExiting();

        return ActorUtils.sendMessageReturn(queue, message);
    }

    private CompletableFuture getCompletableFutureWhenExiting() {
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

    public void execAsyncStateful(Consumer<Stateful<S>> worker) {
        execAsync(worker::accept);
    }

    /** Synchronously executes some logic in the actor; the parameter supplied is the actor itself. */
    public void execAndWait(Consumer<PartialActor<T, S>> worker) {
        if (!isExiting())
            ActorUtils.execAndWait(queue, worker);
    }

    public void execAsyncState(Consumer<S> worker) {
        execAsync(actor -> worker.accept(actor.getState()));
    }

    public void execAndWaitState(Consumer<S> worker) {
        execAndWait(actor -> worker.accept(actor.getState()));
    }

    /** Asynchronously executes some logic in the actor; the parameter supplied is the actor itself. The returned Future can be used to check if the task has been completed. */
    public CompletableFuture<Void> execFuture(Consumer<PartialActor<T, S>> worker) {
        if (isExiting())
            return getCompletableFutureWhenExiting();

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
            return getCompletableFutureWhenExiting();

        return ActorUtils.execFuture(queue, state -> worker.run());
    }

    @Override
    public Exitable setExitSendsPoisonPill(boolean send) {
        return super.setExitSendsPoisonPill(send);
    }

    @Override
    public void askExit() {
        super.askExit();
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
        if (pollTimeoutMs==Integer.MAX_VALUE) {
            while (!isExiting())
                Exceptions.log(this::takeAndProcessSingleMessage);
        }
        else {
            while (!isExiting())
                Exceptions.log(this::takeAndProcessSingleMessageTimeout);
        }

        finalOperations();
    }

    protected abstract void takeAndProcessSingleMessage() throws InterruptedException;

    protected abstract void takeAndProcessSingleMessageTimeout() throws InterruptedException;

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

    @Override
    public void setState(S state) {
        this.state = state;
    }

    public BaseActor<T, R, S> setDrainMessagesOnExit(boolean drainMessagesOnExit) {
        this.drainMessagesOnExit = drainMessagesOnExit;

        return this;
    }

}
