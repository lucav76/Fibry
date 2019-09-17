package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Limited actor, that can only deal with messages; this could useful for remote actors or for pipelines */
public interface MessageOnlyActor<T, R, S> extends Stateful<S>, Function<T, R>, Consumer<T> {
    PartialActor<T, S> sendMessage(T message);
    CompletableFuture<R> sendMessageReturn(T message);
    boolean sendPoisonPill();
}
