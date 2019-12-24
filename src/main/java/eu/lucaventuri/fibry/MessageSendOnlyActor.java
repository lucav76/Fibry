package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Limited actor, that can only deal with messages ina "fire and forget" mode;
 * this could useful for queues
 */
public interface MessageSendOnlyActor<T, S> extends Stateful<S>, Consumer<T>, AutoCloseable {
    MessageSendOnlyActor<T, S> sendMessage(T message);

    boolean sendPoisonPill();

    @Override
    default S getState() {
        return null;
    }

    @Override
    default void setState(S state) {
    }

    @Override
    default void close() throws Exception {
        sendPoisonPill();
    }
}
