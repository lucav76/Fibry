package eu.lucaventuri.fibry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import eu.lucaventuri.common.CanExit;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.Stateful;

/**
 * Limited actor, that can only deal with messages; this could useful for remote actors or for pipelines.
 * Take care that if accesssed using the Consumer interface, the priority will be 1
 */
public interface WeightedActor<T, S> extends Stateful<S>, Consumer<T>, AutoCloseable, CanExit {
    WeightedActor<T, S> sendMessage(T message, int weight);

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

    static <T, S> WeightedActor<T, S> from(MessageSendOnlyActor<WeightedMessage<T>, S> backingActor) {
        return new WeightedActor<T, S>() {
            @Override
            public WeightedActor<T, S> sendMessage(T message, int weight) {
                backingActor.sendMessage(new WeightedMessage<>(message, weight));

                return this;
            }
            @Override
            public boolean sendPoisonPill() {
                return backingActor.sendPoisonPill();
            }
            @Override
            public S getState() {
                return backingActor.getState();
            }
            @Override
            public void setState(S state) {
                backingActor.setState(state);
            }
            @Override
            public void close() throws Exception {
                backingActor.close();
            }
            @Override
            public void accept(T message) {
                backingActor.accept(new WeightedMessage<>(message, 1));
            }
            @Override
            public void askExit() {
                if (backingActor instanceof CanExit)
                    ((CanExit)backingActor).askExit();
            }
            @Override
            public void waitForExit() {
                if (backingActor instanceof CanExit)
                    ((CanExit)backingActor).waitForExit();
            }
        };
    }
}
