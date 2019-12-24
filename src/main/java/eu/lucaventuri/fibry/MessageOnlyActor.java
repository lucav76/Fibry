package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Limited actor, that can only deal with messages; this could useful for remote actors or for pipelines
 */
public interface MessageOnlyActor<T, R, S> extends MessageSendOnlyActor<T, S>, Function<T, R> {
    MessageOnlyActor<T, R, S> sendMessage(T message);

    CompletableFuture<R> sendMessageReturn(T message);

    @Override
    default S getState() {
        return null;
    }

    @Override
    default void setState(S state) {
    }

    @Override
    default R apply(T message) {
        try {
            return sendMessageReturn(message).get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @Override
    default void close() throws Exception {
        sendPoisonPill();
    }
}
