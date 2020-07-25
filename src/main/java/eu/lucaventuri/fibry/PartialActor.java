package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ExtendedClosable;
import eu.lucaventuri.common.Stateful;

import java.util.function.Consumer;

/** Limited actor */
public interface PartialActor<T, S> extends Stateful<S>, Consumer<T>, ExtendedClosable {
    PartialActor<T, S> sendMessage(T message);

    default PartialActor<T, S> sendMessages(T... messages) {
        for (T message : messages)
            sendMessage(message);

        return this;
    }

    void execAsync(Consumer<PartialActor<T, S>> worker);

    void execAsync(Runnable worker);

    boolean sendPoisonPill();

    void askExit();

    boolean isExiting();

    @Override
    default void accept(T message) {
        sendMessage(message);
    }
}
