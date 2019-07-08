package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.function.*;

/** Special actor that is also able to "receive messages", asking for them. As this has potential performance implications, this feature is kept separated from normal actors */
public class ReceivingActor<T, R, S> extends BaseActor<T, R, S> {
    protected final MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag;
    protected final MessageReceiver<T> bagConverter;
    protected final BiConsumer<MessageReceiver<T>, T> actorLogic;
    protected final BiConsumer<MessageReceiver<T>, MessageWithAnswer<T, R>> actorLogicReturn;

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogic   Logic associated to the actor
     * @param messageBag   Bag
     * @param initialState optional initial state
     */
    ReceivingActor(BiConsumer<MessageReceiver<T>, T> actorLogic, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> messageBag, S initialState) {
        super(messageBag);
        BiFunction<MessageReceiver<T>, T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.bag=messageBag;
        this.bagConverter = convertBag(this.bag);
        this.actorLogic = actorLogic;
        this.actorLogicReturn = (bag, mwr) -> mwr.answers.complete(tmpLogicReturn.apply(bagConverter, mwr.message));
        this.state = initialState;
    }

    public static <T, R, S> MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> queueToBag(BlockingDeque<Either3<Consumer<PartialActor<T,S>>,T, MessageWithAnswer<T,R>>> queue) {
        return new MessageBag<>(queue, e ->
                e.isOther() ? e.other().message : e.right());
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     * @param actorLogicReturn Logic associated to the actor
     * @param messageBag Bag
     * @param initialState optional initial state
     */
    ReceivingActor(BiFunction<MessageReceiver<T>,T, R> actorLogicReturn, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> messageBag, S initialState) {
        super(messageBag);

        this.bag=messageBag;
        this.bagConverter = convertBag(this.bag);
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = (bag, mwr) -> mwr.answers.complete(actorLogicReturn.apply(bag, mwr.message));
        this.state = initialState;
    }

    public static <T, R, S> MessageReceiver<T> convertBag(MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag) {
        return new MessageReceiver<T>() {
            @Override
            public T readMessage() {
                while (true) {
                    Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = bag.readMessage();

                    if (message.isRight())
                        return message.right();
                }
            }

            @Override
            public <E extends T> E receive(Class<E> clz, Predicate<E> filter) {
                return (E) bag.receiveAndConvert(clz, filter);
            }
        };
    }

    @Override
    void processMessages() {
        while (!isExiting()) {
            Exceptions.log(() -> {
                Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = bag.readMessage();

                message.ifEither(cns -> cns.accept(this), msg -> actorLogic.accept(bagConverter, msg), msg -> actorLogicReturn.accept(bagConverter, msg));
            });
        }

        notifyFinished();
    }

    public <E extends T> E receive(Class<E> clz, Predicate<E> filter){
        return bagConverter.receive(clz, filter);
    }

    public MessageReceiver<T> getMessageReceiver() {
        return bagConverter;
    }
}
