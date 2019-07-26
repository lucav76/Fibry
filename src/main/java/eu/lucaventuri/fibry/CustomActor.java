package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class CustomActor<T, R, S> extends BaseActor<T, R, S> {
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;

    protected CustomActor(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer, CloseStrategy closeStrategy) {
        super(queue, finalizer, closeStrategy);

        Function<T, R> tmpLogicReturn = ActorUtils.discardingToReturning(this::onMessage);

        this.actorLogicReturn = mwr -> mwr.answer.complete(tmpLogicReturn.apply(mwr.message));
    }

    protected CustomActor(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> finalizer) {
        this(queue, finalizer, null);
    }

    protected abstract void onMessage(T message);

    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.take();

        message.ifEither(cns -> cns.accept(this), this::onMessage, actorLogicReturn::accept);
    }
}
