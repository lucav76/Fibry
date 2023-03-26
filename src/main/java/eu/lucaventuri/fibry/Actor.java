package eu.lucaventuri.fibry;

import eu.lucaventuri.common.CanExit;
import eu.lucaventuri.functional.Either3;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME: Use MessageBag

/**
 * Actor that can process messages of type T, or execute code (supplied by a caller) inside its thread/fiber
 */
public class Actor<T, R, S> extends BaseActor<T, R, S> {
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;

    public static class RollbackException extends RuntimeException {
    }

    public static class Transaction {
        public void rollback() throws RollbackException {
            throw new RollbackException();
        }
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogic Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     * @param finalizer Code to execute when the actor is finishing its operations
     * @param closeStrategy What to do when close() is called
     * @param pollTimeoutMs Poll timeout (to allow the actor to exit without a poison pill); Integer.MAX_VALUE == no timeout
     */
    protected Actor(Consumer<T> actorLogic, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> initializer, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs, ActorSystem.AutoHealingSettings autoHealing, CreationStrategy strategy) {
        super(queue, initializer, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);
        Function<T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answer.complete(tmpLogicReturn.apply(mwr.message));
        this.state = initialState;
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogicReturn Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     * @param finalizer Code to execute when the actor is finishing its operations
     * @param closeStrategy What to do when close() is called
     * @param pollTimeoutMs Poll timeout (to allow the actor to exit without a poison pill); Integer.MAX_VALUE == no timeout
     */
    protected Actor(Function<T, R> actorLogicReturn, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> initializer, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs, ActorSystem.AutoHealingSettings autoHealing, CreationStrategy strategy) {
        super(queue, initializer, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = mwr -> {
            try {
                mwr.answer.complete(actorLogicReturn.apply(mwr.message));
            } catch (Throwable t) {
                mwr.answer.completeExceptionally(t);
            }
        };
        this.state = initialState;
    }

    @Override
    // Just return a more specific type
    public Actor<T, R, S> closeOnExit(AutoCloseable... closeables) {
        return (Actor<T, R, S>) super.closeOnExit(closeables);
    }

    @Override
    protected void takeAndProcessSingleMessageTimeout() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);

        if (message != null) {
            if (autoHealing != null && autoHealing.executionTimeoutSeconds > 0)
                HealRegistry.INSTANCE.put(this, autoHealing, Thread.currentThread());
            message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
        }
    }

    @Override
    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.take();

        if (autoHealing != null && autoHealing.executionTimeoutSeconds > 0)
            HealRegistry.INSTANCE.put(this, autoHealing, Thread.currentThread());
        message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
    }

    protected void processSingleMessage(Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message) throws InterruptedException {
        message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
    }

    @Override
    // Just return a more specific type
    public Actor<T, R, S> sendMessage(T message) {
        if (!isExiting())
            ActorUtils.sendMessage(queue, message);
        return this;
    }

    @Override
    protected Actor<T, R, S> recreate() {
        var newActor = new Actor<>(actorLogic, queue, state, initializer, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);

        strategy.start(newActor);

        return newActor;
    }

    Flow.Subscriber<T> asReactiveSubscriber(int optimalQueueLength, Consumer<Throwable> onErrorHandler, Consumer<PartialActor<T, S>> onCompleteHandler) {
        AtomicReference<Flow.Subscription> sub = new AtomicReference<>();
        return new Flow.Subscriber<T>() {
            private void askRefill() {
                int messagesRequired = optimalQueueLength - queue.size();

                if (messagesRequired > 0)
                    sub.get().request(messagesRequired);
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (sub.get() != null)
                    subscription.cancel();
                else {
                    sub.set(subscription);
                    askRefill();
                }
            }

            @Override
            public void onNext(T item) {
                Objects.requireNonNull(item);

                execAsync(() -> {
                    actorLogic.accept(item);
                    askRefill();
                });
            }

            @Override
            public void onError(Throwable throwable) {
                Objects.requireNonNull(throwable);
                execAsync(() -> {
                    if (onErrorHandler != null)
                        onErrorHandler.accept(throwable);
                });

                sendPoisonPill();
            }

            @Override
            public void onComplete() {
                execAsync(() -> {
                    if (onCompleteHandler != null)
                        onCompleteHandler.accept(Actor.this);
                });
                sendPoisonPill();
            }
        };
    }

    @Override
    public void askExit() {
        if (actorLogic instanceof CanExit)
            ((CanExit) actorLogic).askExit();

        super.askExit();
    }

    /**
     * Allows a transaction that cannot be rolled back (e.g. no attempt is made to restore the original state of the actor);
     * please consider that:
     * - The transaction is executed asynchronously, when possible
     * - It should be fast, because it will block all the other messages
     * - The worker needs to use the supplied MessageOnlyActor, which does not use the queue (as it si blocked)
     * Please use with care.
     * - If there is an error, the status will not be changed, so it could be inconsistent
     *
     * @return a CompletableFuture that can be used to detect when the transaction has been completed
     */
    public CompletableFuture<Void> transactionWithoutRollback(Consumer<MessageOnlyActor<T, R, S>> worker) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        execAsync(() -> {
            worker.accept(asDirectActor());
            future.complete(null);
        });

        return future;
    }

    /**
     * Allows a transaction that can be rolled back;
     * please consider that:
     * - The cloner should be able to clone the state, which is used for rolling back the transaction
     * - The actor should not have other mutable state, but only the one accessible with getState()
     * - The transaction is executed asynchronously, when possible
     * - It should be fast, because it will block all the other messages
     * - The worker needs to use the supplied MessageOnlyActor, which does not use the queue (as it si blocked)
     * Please use with care
     *
     * @return a CompletableFuture that can be used to detect when the transaction has been completed; it will be true if the transaction has been completed, false in case of a rollback.
     */
    public CompletableFuture<Boolean> transaction(BiConsumer<MessageOnlyActor<T, R, S>, Transaction> worker, Function<S, S> cloner) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        S originalStateCloned = cloner.apply(getState());
        Transaction transaction = new Transaction();

        execAsync(() -> {
            try {
                worker.accept(asDirectActor(), transaction);
                future.complete(true);
            } catch (Throwable t) {
                // Rollback
                setState(originalStateCloned);
                future.complete(false);
            }
        });

        return future;
    }

    /** Exposes the logic directly, for calls made inside a transaction */
    protected MessageOnlyActor<T, R, S> asDirectActor() {
        var that = this;

        return new MessageOnlyActor<T, R, S>() {
            @Override
            public MessageOnlyActor<T, R, S> sendMessage(T message) {
                actorLogic.accept(message);

                return this;
            }

            @Override
            public CompletableFuture<R> sendMessageReturn(T message) {
                var messageWithAnswer = new MessageWithAnswer<T, R>(message);

                actorLogicReturn.accept(messageWithAnswer);

                return messageWithAnswer.answer;
            }

            @Override
            public boolean sendPoisonPill() {
                return that.sendPoisonPill();
            }

            @Override
            public void accept(T message) {
                sendMessage(message);
            }

            @Override
            public S getState() {
                return that.getState();
            }

            @Override
            public void setState(S state) {
                that.setState(state);
            }
        };
    }
}
