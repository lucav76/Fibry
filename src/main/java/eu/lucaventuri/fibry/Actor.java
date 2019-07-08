package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

// FIXME: Use MessageBag

/** Actor that can process messages of type T, or execute code (supplied by a caller) inside its thread/fiber */
public class Actor<T, R, S> extends BaseActor<T, R, S> {
    protected final BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue;
    protected final Consumer<T> actorLogic;
    protected final Consumer<MessageWithAnswer<T, R>> actorLogicReturn;

    /**
     * Constructor creating an actor that process messages without returning any value
     * @param actorLogic   Logic associated to the actor
     * @param queue        queue
     * @param initialState optional initial state
     */
    Actor(Consumer<T> actorLogic, BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        super(queue);

        Function<T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.actorLogic = actorLogic;
        this.actorLogicReturn = mwr -> mwr.answers.complete(tmpLogicReturn.apply(mwr.message));
        this.queue = queue;
        this.state = initialState;
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogicReturn Logic associated to the actor
     * @param queue queue
     * @param initialState optional initial state
     */
    Actor(Function<T, R> actorLogicReturn, BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState) {
        super(queue);
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = mwr -> mwr.answers.complete(actorLogicReturn.apply(mwr.message));
        this.queue = queue;
        this.state = initialState;
    }

    @Override
    void processMessages() {
        while (!isExiting()) {
            Exceptions.log(() -> {
                Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.takeFirst();

                message.ifEither(cns -> cns.accept(this), actorLogic::accept, actorLogicReturn::accept);
            });
        }

        notifyFinished();
    }
}
