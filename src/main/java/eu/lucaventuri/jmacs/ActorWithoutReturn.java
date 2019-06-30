package eu.lucaventuri.jmacs;

import eu.lucaventuri.functional.Either;

import java.util.concurrent.BlockingDeque;
import java.util.function.Consumer;

/** Actor processing messages that do not require any answer; in this case, it is more optimized than the ActorWithReturn */
public final class ActorWithoutReturn<T> extends Actor<T> implements Consumer<T> {
    ActorWithoutReturn(Consumer<T> actorLogic, boolean useFibers) {
        super(actorLogic, useFibers);
    }

    ActorWithoutReturn(Consumer<T> actorLogic, BlockingDeque<Either<Runnable, T>> queue, boolean useFibers) {
        super(actorLogic, queue, useFibers);
    }

    public void sendMessage(T message) {
        ActorUtils.sendMessage(queue, message);
    }

    @Override
    public void accept(T message) {
        sendMessage(message);
    }
}
