package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.Stateful;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.functional.Either3;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class BaseActor<T, R, S> extends Exitable implements Function<T, R>, PartialActor<T, S>, SinkActor<S>, MessageOnlyActor<T, R, S> {
    protected final MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue;
    protected S state;
    protected final Consumer<S> initializer;
    protected final Consumer<S> finalizer;
    protected final List<AutoCloseable> closeOnExit = new Vector<>();
    protected volatile boolean drainMessagesOnExit = true;
    protected final int pollTimeoutMs;


    BaseActor(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> initializer, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        this.queue = queue;
        this.initializer = initializer;
        this.finalizer = finalizer;
        this.sendPoisonPillWhenExiting = true;
        this.pollTimeoutMs = pollTimeoutMs;

        if (closeStrategy != null)
            this.closeStrategy = closeStrategy;
    }

    /**
     * Asynchronously sends a message to the actor; the result can be accessed using the CompletableFuture
     */
    public CompletableFuture<R> sendMessageReturn(T message) {
        if (isExiting())
            return getCompletableFutureWhenExiting();

        return ActorUtils.sendMessageReturn(queue, message);
    }

    /**
     * Asynchronously sends multiple messages to the actor; the results can be accessed using the CompletableFutures provided
     */
    public CompletableFuture<R>[] sendMessagesReturn(T... messages) {
        List<CompletableFuture<R>> listFutures = new ArrayList<>();

        for (int i = 0; i < messages.length && !isExiting(); i++)
            listFutures.add(ActorUtils.sendMessageReturn(queue, messages[i]));

        CompletableFuture<R> ar[] = new CompletableFuture[listFutures.size()];

        for (int i = 0; i < listFutures.size(); i++)
            ar[i] = listFutures.get(i);

        return ar;
    }

    /**
     * Send a series of messages to an actor and collects the results (blocking until all the messages have been processed or the actor died)
     */
    public List<R> sendAndCollectSilent(R valueOnError, T... messages) {
        CompletableFuture<R> futures[] = sendMessagesReturn(messages);
        List<R> results = new ArrayList<>();

        waitForFuturesCompletionOrActorExit(futures, 5);

        for (CompletableFuture<R> future : futures)
            results.add(future.isCompletedExceptionally() || future.isCancelled() ? valueOnError : future.getNow(valueOnError));

        return results;
    }

    private void waitForFuturesCompletionOrActorExit(CompletableFuture<R>[] originalFutures, int pollingSleepMs) {
        List<CompletableFuture<R>> futuresNotCompleted = new ArrayList<>();

        for (CompletableFuture<R> future : originalFutures)
            futuresNotCompleted.add(future);

        while (!futuresNotCompleted.isEmpty() && !isFinished()) {
            for (int i = futuresNotCompleted.size() - 1; i >= 0; i--) {
                if (futuresNotCompleted.get(i).isDone())
                    futuresNotCompleted.remove(i);
            }
            SystemUtils.sleep(pollingSleepMs);
        }
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

    /**
     * Synchronously sends a message and waits for the result. This can take some time because of the context switch and of possible messages in the queue
     */
    public R sendMessageReturnWait(T message, R valueOnError) {
        try {
            if (isExiting())
                return valueOnError;

            return ActorUtils.sendMessageReturn(queue, message).get();
        } catch (InterruptedException | ExecutionException e) {
            return valueOnError;
        }
    }

    /**
     * Asynchronously executes some logic in the actor; the parameter supplied is the actor itself.
     */
    public void execAsync(Consumer<PartialActor<T, S>> worker) {
        if (!isExiting())
            ActorUtils.execAsync(queue, worker);
    }

    public void execAsyncStateful(Consumer<Stateful<S>> worker) {
        execAsync(worker::accept);
    }

    /**
     * Synchronously executes some logic in the actor; the parameter supplied is the actor itself.
     */
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

    /**
     * Asynchronously executes some logic in the actor; the parameter supplied is the actor itself. The returned Future can be used to check if the task has been completed.
     */
    public CompletableFuture<Void> execFuture(Consumer<PartialActor<T, S>> worker) {
        if (isExiting())
            return getCompletableFutureWhenExiting();

        return ActorUtils.execFuture(queue, worker);
    }

    /**
     * Asynchronously executes some logic in the actor.
     */
    public void execAsync(Runnable worker) {
        if (!isExiting())
            ActorUtils.execAsync(queue, state -> worker.run());
    }

    /**
     * Synchronously executes some logic in the actor.
     */
    public void execAndWait(Runnable worker) {
        if (!isExiting())
            ActorUtils.execAndWait(queue, state -> worker.run());
    }

    /**
     * Asynchronously executes some logic in the actor. The returned Future can be used to check if the task has been completed.
     */
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

    /**
     * @return the state of the actor
     */
    public S getState() {
        return state;
    }

    /**
     * @return the length of the queue
     */
    public long getQueueLength() {
        return queue.size();
    }

    public final void processMessages() {
        if (initializer != null)
            initializer.accept(state);

        if (pollTimeoutMs == Integer.MAX_VALUE) {
            while (!isExiting())
                Exceptions.log(this::takeAndProcessSingleMessage);
        } else {
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
