package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.function.Consumer;

public abstract class CustomActorWithResult<T, R, S> extends BaseActor<T, R, S> {
    protected final Consumer<T> actorLogic;

    protected CustomActorWithResult(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer) {
        this(queue, finalizer, null);
    }

    protected CustomActorWithResult(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, CloseStrategy closeStrategy) {
        super(queue, finalizer, closeStrategy);

        this.actorLogic = ActorUtils.returningToDiscarding(this::onMessage);
    }

    protected abstract R onMessage(T message);

    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.take();

        message.ifEither(cns -> cns.accept(this), actorLogic::accept, mwr -> mwr.answer.complete(onMessage(mwr.message)));
    }
}
