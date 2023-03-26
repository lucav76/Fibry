package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Actor that runs in the same thread as the caller; this is achieved with the Actor being the queue as well.
 * This can be useful in particular cases.
 */
public class SynchronousActor<T, R, S> extends Actor<T, R, S> implements MiniFibryQueue<T, R, PartialActor<T, S>> {
    public SynchronousActor(Consumer<T> actorLogic, S initialState, Consumer<S> initializer, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        // TODO: Should we add support for auto healing?
        super(actorLogic, new MiniFibryQueue.MiniFibryQueueDelegate<>(), initialState, initializer, finalizer, closeStrategy, pollTimeoutMs, null, null);

        sendPoisonPillWhenExiting = false;
        ((MiniFibryQueue.MiniFibryQueueDelegate) queue).setQueue(this);
    }

    SynchronousActor(Function<T, R> actorLogicReturn, S initialState, Consumer<S> initializer, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        // TODO: Should we add support for auto healing?
        super(actorLogicReturn, new MiniFibryQueue.MiniFibryQueueDelegate<>(), initialState, initializer, finalizer, closeStrategy, pollTimeoutMs, null, null);

        sendPoisonPillWhenExiting = false;
        ((MiniFibryQueue.MiniFibryQueueDelegate) queue).setQueue(this);
    }

    @Override
    protected void takeAndProcessSingleMessage() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void takeAndProcessSingleMessageTimeout() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message) {
        message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);

        return true;
    }

    @Override
    public Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> take() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> poll(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> peek() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean sendPoisonPill() {
        sendPoisonPillWhenExiting = false;
        askExit();
        return true;
    }
}
