package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME: Use MessageBag

/**
 * Actor that can process messages of type T, or execute code (supplied by a caller) inside its thread/fiber
 */
public class Actor<T, R, S> extends BaseActor<T, R, S> {
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;

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
    protected Actor(Consumer<T> actorLogic, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        super(queue, finalizer, closeStrategy, pollTimeoutMs);

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
    protected Actor(Function<T, R> actorLogicReturn, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        super(queue, finalizer, closeStrategy, pollTimeoutMs);
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

        if (message != null)
            message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
    }

    @Override
    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.take();

        message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
    }

    @Override
    // Just return a more specific type
    public Actor<T, R, S> sendMessage(T message) {
        if (!isExiting())
            ActorUtils.sendMessage(queue, message);
        return this;
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

}
