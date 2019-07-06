package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME: Use MessageBag

/** Super simple actor, that can process messages of type T, or execute Runnables inside its thread */
public class ReceivingActor<T, R, S> extends BaseActor<T, R, S> {
    protected final MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> bag;
    protected final BiConsumer<MessageReceiver<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>>, T> actorLogic;
    protected final BiConsumer<MessageReceiver<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>>, MessageWithAnswer<T, R>> actorLogicReturn;

    /**
     * Constructor creating an acotor that process messages without returning any value
     * @param actorLogic Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     */
    ReceivingActor(BiConsumer<MessageReceiver<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>>, T> actorLogic, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        super(queue);

        BiFunction<MessageReceiver<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>>, T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.bag=queue;
        this.actorLogic = actorLogic;
        this.actorLogicReturn = (bag, mwr) -> mwr.answers.complete(tmpLogicReturn.apply(bag, mwr.message));
        this.state = initialState;
    }

    /**
     * Constructor creating an acotor that process messages without returning any value
     * @param actorLogicReturn Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     */
    ReceivingActor(BiFunction<MessageReceiver<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>>,T, R> actorLogicReturn, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        super(queue);

        this.bag=queue;
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = (bag, mwr) -> mwr.answers.complete(actorLogicReturn.apply(bag, mwr.message));
        this.state = initialState;
    }


    void processMessages() {
        while (!isExiting()) {
            Exceptions.log(() -> {
                Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = bag.readMessage();

                message.ifEither(cns -> cns.accept(this), msg -> actorLogic.accept(bag, msg), msg -> actorLogicReturn.accept(bag, msg));
            });
        }

        notifyFinished();
    }
}
