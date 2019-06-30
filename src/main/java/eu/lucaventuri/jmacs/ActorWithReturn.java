package eu.lucaventuri.jmacs;

import eu.lucaventuri.functional.Either;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ActorWithReturn<T, R> extends Actor<MessageWithAnswer<T, R>> implements Consumer<T>, Function<T, R> {
    ActorWithReturn(Function<T, R> actorLogic, boolean useFibers) {
        this(actorLogic, new LinkedBlockingDeque<Either<Runnable, MessageWithAnswer<T, R>>>(), useFibers);
    }

    ActorWithReturn(Function<T, R> actorLogic, BlockingDeque<Either<Runnable, MessageWithAnswer<T, R>>> queue, boolean useFibers) {
        super(messageWithAnswer -> messageWithAnswer.answers.complete(actorLogic.apply(messageWithAnswer.message)), queue, useFibers);
    }

    public CompletableFuture<R> sendMessageReturn(T message) {
        var mwr = new MessageWithAnswer<T, R>(message);

        ActorUtils.sendMessage(queue, mwr);

        return mwr.answers;
    }

    public void sendMessage(T message) {
        ActorUtils.sendMessage(queue, new MessageWithAnswer<T, R>(message));
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
}
