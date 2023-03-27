package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
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
     * @param actorLogic    Logic associated to the actor
     * @param queue         queue
     * @param initialState  optional initial state
     * @param finalizer     Code to execute when the actor is finishing its operations
     * @param closeStrategy What to do when close() is called
     * @param pollTimeoutMs Poll timeout (to allow the actor to exit without a poison pill); Integer.MAX_VALUE == no timeout
     */
    protected Actor(Consumer<T> actorLogic, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs, ActorSystem.AutoHealingSettings autoHealing, CreationStrategy strategy) {
        super(queue, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);
        Function<T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answer.complete(tmpLogicReturn.apply(mwr.message));
        this.state = initialState;
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogicReturn Logic associated to the actor
     * @param queue            queue
     * @param initialState     optional initial state
     * @param finalizer        Code to execute when the actor is finishing its operations
     * @param closeStrategy    What to do when close() is called
     * @param pollTimeoutMs    Poll timeout (to allow the actor to exit without a poison pill); Integer.MAX_VALUE == no timeout
     */
    protected Actor(Function<T, R> actorLogicReturn, MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs, ActorSystem.AutoHealingSettings autoHealing, CreationStrategy strategy) {
        super(queue, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);
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

    @Override
    // Just return a more specific type
    public Actor<T, R, S> sendMessage(T message) {
        if (!isExiting())
            ActorUtils.sendMessage(queue, message);
        return this;
    }
    @Override
    protected Actor<T, R, S> recreate() {
        Actor<T, R, S> newActor = new Actor<>(actorLogic, queue, state, finalizer, closeStrategy, pollTimeoutMs, autoHealing, strategy);

        strategy.start(newActor);

        return newActor;
    }


}
