package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public abstract class CustomActorWithResult<T, R, S> extends BaseActor<T, R, S> {
    protected final Consumer<T> actorLogic;

    protected CustomActorWithResult(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, int pollTimeoutMs) {
        this(queue, finalizer, null, pollTimeoutMs);
    }

    protected CustomActorWithResult(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        super(queue, finalizer, closeStrategy, pollTimeoutMs);

        this.actorLogic = ActorUtils.returningToDiscarding(this::onMessage);
    }

    protected abstract R onMessage(T message);

    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.take();

        message.ifEither(cns -> cns.accept(this), actorLogic::accept, mwr -> mwr.answer.complete(onMessage(mwr.message)));
    }

    @Override
    protected void takeAndProcessSingleMessageTimeout() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);

        if (message != null)
            message.ifEither(cns -> cns.accept(this), this::onMessage, mwr -> mwr.answer.complete(onMessage(mwr.message)));
    }
}
