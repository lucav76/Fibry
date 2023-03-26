package eu.lucaventuri.fibry;

import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Special actor that is also able to "receive messages", asking for them. As this has potential performance implications, this feature is kept separated from normal actors
 */
public class ReceivingActor<T, R, S> extends BaseActor<T, R, S> {
    protected final MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag;
    protected final MessageReceiver<T> bagConverter;
    protected final BiConsumer<MessageReceiver<T>, T> actorLogic;
    protected final BiConsumer<MessageReceiver<T>, MessageWithAnswer<T, R>> actorLogicReturn;

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogic    Logic associated to the actor
     * @param messageBag    Bag
     * @param initialState  optional initial state
     * @param finalizer     Code to execute when the actor is finishing its operations
     * @param closeStrategy What to do when close() is called
     * @param pollTimeoutMs Poll timeout (to allow the actor to exit without a poison pill); Integer.MAX_VALUE == no timeout
     */
    protected ReceivingActor(BiConsumer<MessageReceiver<T>, T> actorLogic, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> messageBag, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        super(messageBag, finalizer, closeStrategy, pollTimeoutMs, null, null);
        BiFunction<MessageReceiver<T>, T, R> tmpLogicReturn = ActorUtils.discardingToReturning(actorLogic);

        this.bag = messageBag;
        this.bagConverter = convertBag(this.bag);
        this.actorLogic = actorLogic;
        this.actorLogicReturn = (bag, mwr) -> mwr.answer.complete(tmpLogicReturn.apply(bagConverter, mwr.message));
        this.state = initialState;
    }

    public static <T, R, S> MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> queueToBag(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue) {
        return new MessageBag<>(queue, e ->
                e.isOther() ? e.other().message : e.right());
    }

    /**
     * Constructor creating an actor that process messages without returning any value
     *
     * @param actorLogicReturn Logic associated to the actor
     * @param messageBag       Bag
     * @param initialState     optional initial state
     */
    ReceivingActor(BiFunction<MessageReceiver<T>, T, R> actorLogicReturn, MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> messageBag, S initialState, Consumer<S> finalizer, CloseStrategy closeStrategy, int pollTimeoutMs) {
        super(messageBag, finalizer, closeStrategy, pollTimeoutMs, null, null);

        this.bag = messageBag;
        this.bagConverter = convertBag(this.bag);
        this.actorLogic = ActorUtils.returningToDiscarding(actorLogicReturn);
        this.actorLogicReturn = (bag, mwr) -> mwr.answer.complete(actorLogicReturn.apply(bag, mwr.message));
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

    protected void takeAndProcessSingleMessage() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = bag.readMessage();

        message.ifEither(cns -> cns.accept(this), msg -> actorLogic.accept(bagConverter, msg), msg -> actorLogicReturn.accept(bagConverter, msg));
    }

    @Override
    protected void takeAndProcessSingleMessageTimeout() throws InterruptedException {
        Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>> message = queue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);

        if (message != null)
            message.ifEither(cns -> cns.accept(this), msg -> actorLogic.accept(bagConverter, msg), msg -> actorLogicReturn.accept(bagConverter, msg));
    }

    public <E extends T> E receive(Class<E> clz, Predicate<E> filter) {
        return bagConverter.receive(clz, filter);
    }

    public MessageReceiver<T> getMessageReceiver() {
        return bagConverter;
    }

    @Override
    public ReceivingActor<T, R, S> closeOnExit(AutoCloseable... closeables) {
        return (ReceivingActor) super.closeOnExit(closeables);
    }

    @Override
    public ReceivingActor<T, R, S> sendMessage(T message) {
        if (!isExiting())
            ActorUtils.sendMessage(queue, message);

        return this;
    }

    @Override
    protected BaseActor<T, R, S> recreate() {
        throw new UnsupportedOperationException("Receiving actors do not support auto healing");
    }
}
