package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.functional.Either;
import eu.lucaventuri.functional.Either3;

import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/**
 * Simple actor system, creating one thread/fiber per Actor. Each Actor can either process messages (with or without return) or execute Consumer inside its thread.
 * Receiving actors can perform a 'receive' operation and ask for specific messages.
 */
public class ActorSystem {
    private static final ConcurrentHashMap<String, BlockingDeque> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();
    private static final AtomicLong progressivePoolId = new AtomicLong();

    public static class ActorPoolCreator<S> {
        private final CreationStrategy strategy;
        private final String name;  // Can be null
        private final Supplier<S> stateSupplier;
        private final PoolParameters poolParams;

        private ActorPoolCreator(CreationStrategy strategy, String name, PoolParameters poolParams, Supplier<S> stateSupplier) {
            this.strategy = strategy;
            this.name = name != null ? name : "__pool__" + progressivePoolId.incrementAndGet() + "__" + Math.random() + "__";
            this.poolParams = poolParams;
            this.stateSupplier = stateSupplier;
        }

        // By design, group pools logic should not have access to the actor itself
        private <T, R> PoolActorLeader<T, R, S> createFixedPool(Either<Consumer<T>, Function<T, R>> actorLogic) {
            MultiExitable groupExit = new MultiExitable();
            PoolActorLeader<T, R, S> groupLeader = new PoolActorLeader<T, R, S>(getOrCreateActorQueue(registerActorName(name, false)), (S) null, groupExit);

            for (int i = 0; i < poolParams.minSize; i++)
                createNewWorkerAndAddToPool(groupLeader, actorLogic);

            return groupLeader;
        }

        private <T, R> void createNewWorkerAndAddToPool(PoolActorLeader<T, R, S> groupLeader, Either<Consumer<T>, Function<T, R>> actorLogic) {
            NamedStateActorCreator<S> creator = new NamedStateActorCreator<>(name, strategy, stateSupplier == null ? null : stateSupplier.get(), true);

            actorLogic.ifEither(logic -> {
                groupLeader.getGroupExit().add(creator.newActor(logic));
            }, logic -> {
                groupLeader.getGroupExit().add(creator.newActorWithReturn(logic));
            });
        }

        private <T, R> PoolActorLeader<T, R, S> createPool(Either<Consumer<T>, Function<T, R>> actorLogic) {
            PoolActorLeader<T, R, S> leader = createFixedPool(actorLogic);

            if (poolParams.minSize != poolParams.maxSize)
                autoScale(leader, actorLogic);

            return leader;
        }

        private <R, T> void autoScale(PoolActorLeader<T, R, S> leader, Either<Consumer<T>, Function<T, R>> actorLogic) {
            MultiExitable groupExit = leader.getGroupExit();

            Stereotypes.auto().schedule(() -> {
                long queueSize = leader.getQueueLength();

                if (queueSize >= poolParams.scalingUpThreshold) {
                    for (int i = 0; i < poolParams.scalingSpeed && groupExit.size() < poolParams.maxSize; i++)
                        createNewWorkerAndAddToPool(leader, actorLogic);
                } else if (queueSize <= poolParams.scalingDownThreshold) {
                    for (int i = 0; i < poolParams.scalingSpeed && groupExit.size() > poolParams.minSize; i++)
                        groupExit.evictRandomly(true);
                }
            }, poolParams.timePollingMs);
        }

        public <T> Actor<T, Void, S> newPool(Consumer<T> actorLogic) {
            return createPool(Either.left(actorLogic));
        }

        public <T, R> Actor<T, R, S> newPoolWithReturn(Function<T, R> actorLogic) {
            return createPool(Either.right(actorLogic));
        }
    }

    public static class NamedStateActorCreator<S> {
        final S initialState;
        final CreationStrategy strategy;
        final String name;  // Can be null
        final boolean allowReuse;

        private NamedStateActorCreator(String name, CreationStrategy strategy, S initialState, boolean allowReuse) {
            this.name = name;
            this.strategy = strategy;
            this.initialState = initialState;
            this.allowReuse = allowReuse;
        }

        public <T> Actor<T, Void, S> newActor(Consumer<T> actorLogic) {
            return (Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse)), initialState));
        }

        public <T> Actor<T, Void, S> newActor(BiConsumer<T, PartialActor<T, S>> actorBiLogic) {
            AtomicReference<Actor<T, Void, S>> ref = new AtomicReference<>();
            Consumer<T> actorLogic = message -> actorBiLogic.accept(message, ref.get());

            ref.set((Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse)), initialState)));

            return ref.get();
        }

        public <T> ReceivingActor<T, Void, S> newReceivingActor(BiConsumer<MessageReceiver<T>, T> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, Void>>, T> bag = ReceivingActor.<T, Void, S>queueToBag(getOrCreateActorQueue(registerActorName(name, allowReuse)));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, Void, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(Function<T, R> actorLogic) {
            return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse)), initialState));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(BiFunction<T, PartialActor<T, S>, R> actorBiLogic) {
            AtomicReference<Actor<T, Void, S>> ref = new AtomicReference<>();
            Function<T, R> actorLogic = message -> actorBiLogic.apply(message, ref.get());

            return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse)), initialState));
        }

        public <T, R> ReceivingActor<T, R, S> newReceivingActorWithReturn(BiFunction<MessageReceiver<T>, T, R> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag = ReceivingActor.<T, R, S>queueToBag(getOrCreateActorQueue(registerActorName(name, allowReuse)));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, R, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState));
        }
    }

    // Name and strategy as supplied, initialState: null
    public static class NamedStrategyActorCreator extends NamedStateActorCreator<Void> {
        private NamedStrategyActorCreator(String name, CreationStrategy strategy) {
            super(name, strategy, null, false);
        }

        public <S> NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(name, strategy, state, false);
        }

        public <S> ActorPoolCreator<S> poolParams(PoolParameters params, Supplier<S> stateSupplier) {
            return new ActorPoolCreator<>(strategy, name, params, stateSupplier);
        }
    }

    // Name as supplied, strategy: auto and initialState: null
    public static class NamedActorCreator extends NamedStrategyActorCreator {
        private NamedActorCreator(String name) {
            super(name, CreationStrategy.AUTO);
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

    protected static String registerActorName(String actorName, boolean allowReuse) {
        if (actorName == null)
            return null;

        boolean newActor = actorNamesInUse.add(actorName);

        if (!allowReuse) {
            if (!newActor)
                throw new IllegalArgumentException("Actors name already in use!");
        }

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
