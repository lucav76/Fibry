package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.functional.Either3;

import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Simple actor system, creating one thread/fiber per Actor. Each Actor can either process messages (with or without return) or execute Consumer inside its thread.
 * Receiving actors can perform a 'receive' operation and ask for specific messages.
 */
public class ActorSystem {
    private static final ConcurrentHashMap<String, BlockingDeque> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();

    public static class NamedStateActorCreator<S> {
        private final S initialState;
        private final CreationStrategy strategy;
        private final String name;  // Can be null

        public NamedStateActorCreator(String name, CreationStrategy strategy, S initialState) {
            this.name = name;
            this.strategy = strategy;
            this.initialState = initialState;
        }

        public <T> Actor<T, Void, S> newActor(Consumer<T> actorLogic) {
            return (Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name)), initialState));
        }

        public <T> Actor<T, Void, S> newActor(BiConsumer<T, PartialActor<T, S>> actorBiLogic) {
            AtomicReference<Actor<T, Void, S>> ref = new AtomicReference<>();
            Consumer<T> actorLogic = message -> actorBiLogic.accept(message, ref.get());

            ref.set((Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name)), initialState)));

            return ref.get();
        }

        public <T> ReceivingActor<T, Void, S> newReceivingActor(BiConsumer<MessageReceiver<T>, T> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, Void>>, T> bag = ReceivingActor.<T, Void, S>queueToBag(getOrCreateActorQueue(registerActorName(name)));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, Void, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(Function<T, R> actorLogic) {
            return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name)), initialState));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(BiFunction<T, PartialActor<T, S>, R> actorBiLogic) {
            AtomicReference<Actor<T, Void, S>> ref = new AtomicReference<>();
            Function<T, R> actorLogic = message -> actorBiLogic.apply(message, ref.get());

            return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name)), initialState));
        }

        public <T, R> ReceivingActor<T, R, S> newReceivingActorWithReturn(BiFunction<MessageReceiver<T>, T, R> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag = ReceivingActor.<T, R, S>queueToBag(getOrCreateActorQueue(registerActorName(name)));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, R, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState));
        }
    }

    // Name and strategy as supplied, initialState: null
    public static class NamedStrategyActorCreator extends NamedStateActorCreator<Void> {
        private final String name;  // Can be null
        private final CreationStrategy strategy;  // Can be null

        private NamedStrategyActorCreator(String name, CreationStrategy strategy) {
            super(name, strategy, null);
            this.name = name;
            this.strategy = strategy;
        }

        public <S> NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(name, CreationStrategy.AUTO, state);
        }
    }

    // Name as supplied, strategy: auto and initialState: null
    public static class NamedActorCreator extends NamedStateActorCreator<Void> {
        private final String name;  // Can be null

        private NamedActorCreator(String name) {
            super(name, CreationStrategy.AUTO, null);
            this.name = name;
        }

        public <S> NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(name, CreationStrategy.AUTO, state);
        }

        public NamedStrategyActorCreator strategy(CreationStrategy strategy) {
            return new NamedStrategyActorCreator(name, strategy);
        }
    }

    public static NamedActorCreator named(String name) {
        return new NamedActorCreator(name);
    }

    public static NamedActorCreator anonymous() {
        return new NamedActorCreator(null);
    }

    protected static String registerActorName(String actorName) {
        if (actorName == null)
            return null;

        boolean newActor = actorNamesInUse.add(actorName);

        if (!newActor)
            throw new IllegalArgumentException("Actors name already in use!");

        return actorName;
    }

    private static <T, R, S> LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> getOrCreateActorQueue(String actorName) {
        if (actorName == null)
            return new LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>>();

        return (LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>>) namedQueues.computeIfAbsent(actorName, name -> new LinkedBlockingDeque<Either3<Consumer, T, MessageWithAnswer<T, R>>>());
    }

    private static void enforceName(String actorName) {
        if (actorName == null)
            throw new IllegalArgumentException("The actor name cannot be null as this method cannot support anonymous actors");
    }

    public static <T> void sendMessage(String actorName, T message) {
        enforceName(actorName);
        ActorUtils.sendMessage(getOrCreateActorQueue(actorName), message);
    }

    public static <T, R> CompletableFuture<R> sendMessageReturn(String actorName, T message) {
        enforceName(actorName);
        return ActorUtils.sendMessageReturn(getOrCreateActorQueue(actorName), message);
    }

    public static <S> void execAsync(String actorName, Consumer<S> worker) {
        enforceName(actorName);
        ActorUtils.execAsync(getOrCreateActorQueue(actorName), worker);
    }

    public static <S> void execAndWait(String actorName, Consumer<S> worker) {
        enforceName(actorName);
        ActorUtils.execAndWait(getOrCreateActorQueue(actorName), worker);
    }

    public static <T, S> CompletableFuture<Void> execFuture(String actorName, Consumer<S> worker) {
        enforceName(actorName);
        return ActorUtils.execFuture(getOrCreateActorQueue(actorName), worker);
    }
}
