package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.common.Exitable.CloseStrategy;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.functional.Either;
import eu.lucaventuri.functional.Either3;

import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/**
 * Simple actor system, creating one thread/fiber per Actor. Each Actor can either process messages (with or without return) or execute Consumer inside its thread.
 * Receiving actors can perform a 'receive' operation and ask for specific messages.
 */
public class ActorSystem {
    private static final ConcurrentHashMap<String, MiniFibryQueue> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();
    private static final AtomicLong progressivePoolId = new AtomicLong();
    private static volatile int defaultQueueCapacity = Integer.MAX_VALUE;
    private static volatile int defaultPollTimeoutMs = Integer.MAX_VALUE;
    private static final MiniFibryQueue DROPPING_QUEUE = MiniFibryQueue.dropping();
    static volatile CreationStrategy defaultStrategy = CreationStrategy.AUTO;
    private static final NamedActorCreator defaultAnonymous = new NamedActorCreator(null, defaultQueueCapacity, false);

    public static class ActorPoolCreator<S> {
        private final CreationStrategy strategy;
        private final String name;  // Can be null
        private final Supplier<S> stateSupplier;
        private final PoolParameters poolParams;
        private final Consumer<S> finalizer;

        private ActorPoolCreator(CreationStrategy strategy, String name, PoolParameters poolParams, Supplier<S> stateSupplier, Consumer<S> finalizer) {
            this.strategy = strategy;
            this.name = name != null ? name : "__pool__" + progressivePoolId.incrementAndGet() + "__" + Math.random() + "__";
            this.poolParams = poolParams;
            this.stateSupplier = stateSupplier;
            this.finalizer = finalizer;
        }

        // By design, group pools logic should not have access to the actor itself
        private <T, R> PoolActorLeader<T, R, S> createFixedPool(Either<Consumer<T>, Function<T, R>> actorLogic) {
            MultiExitable groupExit = new MultiExitable();
            // As the leader has no state, it cannot run the finalizer
            // We add queue protection because it's unlikely to have millions of pools
            PoolActorLeader<T, R, S> groupLeader = new PoolActorLeader<>(getOrCreateActorQueue(registerActorName(name, false), defaultQueueCapacity), null, groupExit, getQueueFinalizer(name, null, true));

            for (int i = 0; i < poolParams.minSize; i++)
                createNewWorkerAndAddToPool(groupLeader, actorLogic);

            return groupLeader;
        }

        private <T, R> void createNewWorkerAndAddToPool(PoolActorLeader<T, R, S> groupLeader, Either<Consumer<T>, Function<T, R>> actorLogic) {
            NamedStateActorCreator<S> creator = new NamedStateActorCreator<>(name, strategy, stateSupplier == null ? null : stateSupplier.get(), true, finalizer, null, defaultQueueCapacity, true, 50);
            actorLogic.ifEither(logic -> groupLeader.getGroupExit().add(creator.newActor(logic).setDrainMessagesOnExit(false).setExitSendsPoisonPill(false)),
                    logic -> groupLeader.getGroupExit().add(creator.newActorWithReturn(logic).setDrainMessagesOnExit(false).setExitSendsPoisonPill(false)));
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
                //System.out.println("Queue size: " + queueSize);

                if (queueSize >= poolParams.scalingUpThreshold) {
                    for (int i = 0; i < poolParams.scalingSpeed && groupExit.size() < poolParams.maxSize; i++)
                        createNewWorkerAndAddToPool(leader, actorLogic);
                } else if (queueSize <= poolParams.scalingDownThreshold) {
                    for (int i = 0; i < poolParams.scalingSpeed && groupExit.size() > poolParams.minSize; i++)
                        groupExit.evictRandomly(true);
                }
            }, poolParams.timePollingMs);
        }

        public <T> PoolActorLeader<T, Void, S> newPool(Consumer<T> actorLogic) {
            return createPool(Either.left(actorLogic));
        }

        public <T, R> PoolActorLeader<T, R, S> newPoolWithReturn(Function<T, R> actorLogic) {
            return createPool(Either.right(actorLogic));
        }
    }

    public static class NamedStateActorCreator<S> {
        final S initialState;
        final CreationStrategy strategy;
        final String name;  // Can be null
        final boolean allowReuse;
        final Consumer<S> finalizer;
        final CloseStrategy closeStrategy;
        final int queueCapacity;
        final int pollTimeoutMs;

        public NamedStateActorCreator(String name, CreationStrategy strategy, S initialState, boolean allowReuse, Consumer<S> finalizer, CloseStrategy closeStrategy, int queueCapacity, int pollTimeoutMs) {
            this.initialState = initialState;
            this.strategy = strategy;
            this.name = name;
            this.allowReuse = allowReuse;
            this.finalizer = finalizer;
            this.closeStrategy = closeStrategy;
            this.queueCapacity = queueCapacity;
            this.pollTimeoutMs = pollTimeoutMs;
        }

        private NamedStateActorCreator(String name, CreationStrategy strategy, S initialState, boolean allowReuse, Consumer<S> finalizer, CloseStrategy closeStrategy, int queueCapacity, boolean queueProtection) {
            this.name = name;
            this.strategy = strategy;
            this.initialState = initialState;
            this.allowReuse = allowReuse;
            this.finalizer = getQueueFinalizer(name, finalizer, queueProtection);
            this.closeStrategy = closeStrategy;
            this.queueCapacity = queueCapacity;
            this.pollTimeoutMs = defaultPollTimeoutMs;
        }

        private NamedStateActorCreator(String name, CreationStrategy strategy, S initialState, boolean allowReuse, Consumer<S> finalizer, CloseStrategy closeStrategy, int queueCapacity, boolean queueProtection, int pollTimeoutMs) {
            this.name = name;
            this.strategy = strategy;
            this.initialState = initialState;
            this.allowReuse = allowReuse;
            this.finalizer = getQueueFinalizer(name, finalizer, queueProtection);
            this.closeStrategy = closeStrategy;
            this.queueCapacity = queueCapacity;
            this.pollTimeoutMs = pollTimeoutMs;
        }

        public NamedStateActorCreator pollTimeout(int newPollTimeoutMs) {
            return new NamedStateActorCreator<S>(name, strategy, initialState, allowReuse, finalizer, closeStrategy, queueCapacity, newPollTimeoutMs);
        }

        public <T> Actor<T, Void, S> newActor(Consumer<T> actorLogic) {
            return (Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity), initialState, finalizer, closeStrategy, pollTimeoutMs));
        }

        public <T> Actor<T, Void, S> newActor(BiConsumer<T, PartialActor<T, S>> actorBiLogic) {
            return ActorUtils.initRef(ref -> {
                Consumer<T> actorLogic = message -> actorBiLogic.accept(message, ref.get());

                return (Actor<T, Void, S>) strategy.<T, Void, S>start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity), initialState, finalizer, closeStrategy, pollTimeoutMs));
            });
        }

        public <T> ReceivingActor<T, Void, S> newReceivingActor(BiConsumer<MessageReceiver<T>, T> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, Void>>, T> bag = ReceivingActor.<T, Void, S>queueToBag(getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, Void, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState, finalizer, closeStrategy, pollTimeoutMs));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(Function<T, R> actorLogic) {
            return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity), initialState, finalizer, closeStrategy, pollTimeoutMs));
        }

        public <T, R> Actor<T, R, S> newActorWithReturn(BiFunction<T, PartialActor<T, S>, R> actorBiLogic) {
            return ActorUtils.initRef(ref -> {
                Function<T, R> actorLogic = message -> actorBiLogic.apply(message, ref.get());

                return (Actor<T, R, S>) strategy.start(new Actor<>(actorLogic, getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity), initialState, finalizer, closeStrategy, pollTimeoutMs));
            });
        }

        public <T, R> ReceivingActor<T, R, S> newReceivingActorWithReturn(BiFunction<MessageReceiver<T>, T, R> actorBiLogic) {
            MessageBag<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>, T> bag = ReceivingActor.<T, R, S>queueToBag(getOrCreateActorQueue(registerActorName(name, allowReuse), queueCapacity));
            MessageReceiver<T> receiver = ReceivingActor.convertBag(bag);

            return (ReceivingActor<T, R, S>) strategy.start(new ReceivingActor<>(actorBiLogic, bag, initialState, finalizer, closeStrategy, pollTimeoutMs));
        }
    }

    // Name and strategy as supplied, initialState: null
    public static class NamedStrategyActorCreator extends NamedStateActorCreator<Void> {
        final boolean queueProtection;

        private NamedStrategyActorCreator(String name, CreationStrategy strategy, CloseStrategy closeStrategy, int queueCapacity, boolean queueProtection) {
            super(name, strategy, null, false, null, closeStrategy, queueCapacity, queueProtection);

            this.queueProtection = queueProtection;
        }

        public <S> NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(name, strategy, state, false, null, closeStrategy, queueCapacity, queueProtection);
        }

        /**
         * @param state Initial state
         * @param finalizer Finalizer called after the actor finished to process all its message
         * @return an object part of the fluent interface
         */
        public <S> NamedStateActorCreator<S> initialState(S state, Consumer<S> finalizer) {
            return new NamedStateActorCreator<>(name, strategy, state, false, finalizer, closeStrategy, queueCapacity, queueProtection);
        }

        public <S> ActorPoolCreator<S> poolParams(PoolParameters params, Supplier<S> stateSupplier) {
            return new ActorPoolCreator<>(strategy, name, params, stateSupplier, null);
        }

        /**
         * @param params Parameters to create the pool
         * @param stateSupplier Supplier of states, as the pool will probably need more than one
         * @param finalizer Finalizer called after the actor finished to process all its message
         * @return an object part of the fluent interface
         */

        public <S> ActorPoolCreator<S> poolParams(PoolParameters params, Supplier<S> stateSupplier, Consumer<S> finalizer) {
            return new ActorPoolCreator<>(strategy, name, params, stateSupplier, finalizer);
        }
    }

    // Name as supplied, strategy: auto and initialState: null
    public static class NamedActorCreator extends NamedStrategyActorCreator {
        private NamedActorCreator(String name, int queueCapacity, boolean queueProtection) {
            super(name, defaultStrategy, null, queueCapacity, queueProtection);
        }

        public NamedStrategyActorCreator strategy(CreationStrategy strategy) {
            return new NamedStrategyActorCreator(name, strategy, null, queueCapacity, queueProtection);
        }

        public NamedStrategyActorCreator strategy(CreationStrategy strategy, CloseStrategy closeStrategy) {
            return new NamedStrategyActorCreator(name, strategy, closeStrategy, queueCapacity, queueProtection);
        }

        public NamedStrategyActorCreator strategy(CloseStrategy closeStrategy) {
            return new NamedStrategyActorCreator(name, CreationStrategy.AUTO, closeStrategy, queueCapacity, queueProtection);
        }
    }

    public static NamedActorCreator named(String name) {
        return new NamedActorCreator(name, defaultQueueCapacity, false);
    }

    public static NamedActorCreator named(String name, boolean queueProtection) {
        return new NamedActorCreator(name, defaultQueueCapacity, queueProtection);
    }

    public static NamedActorCreator named(String name, int queueCapacity) {
        return new NamedActorCreator(name, queueCapacity, false);
    }

    public static NamedActorCreator named(String name, int queueCapacity, boolean queueProtection) {
        return new NamedActorCreator(name, queueCapacity, queueProtection);
    }

    public static NamedActorCreator anonymous() {
        return defaultAnonymous;
        //return new NamedActorCreator(null, defaultQueueCapacity, false);
    }

    public static NamedActorCreator anonymous(int queueCapacity) {
        return new NamedActorCreator(null, queueCapacity, false);
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

    private static <T, R, S> MiniFibryQueue<T, R, S> getOrCreateActorQueue(String actorName, int capacity) {
        if (actorName == null)
            return new FibryQueue<>(capacity);

        return namedQueues.computeIfAbsent(actorName, name -> new FibryQueue(capacity));
    }

    private static void requireNameNotNull(String actorName) {
        if (actorName == null)
            throw new IllegalArgumentException("The actor name cannot be null as this method cannot support anonymous actors");
    }

    /**
     * Sends a message to a named actor using only its name
     *
     * @param actorName Name of the actor
     * @param message Message to be sent
     * @param forceDelivery True to
     * @param <T>
     */
    public static <T> void sendMessage(String actorName, T message, boolean forceDelivery) {
        requireNameNotNull(actorName);

        if (!forceDelivery && !isActorAvailable(actorName))
            return;

        ActorUtils.sendMessage(getOrCreateActorQueue(actorName, defaultQueueCapacity), message);
    }

    public static <T, R> CompletableFuture<R> sendMessageReturn(String actorName, T message, boolean forceDelivery) {
        requireNameNotNull(actorName);

        if (!forceDelivery && !isActorAvailable(actorName)) {
            CompletableFuture<R> r = new CompletableFuture<>();
            r.completeExceptionally(new RuntimeException("Actors not existing and force delivery not enabled"));

            return r;
        }

        return ActorUtils.sendMessageReturn(getOrCreateActorQueue(actorName, defaultQueueCapacity), message);
    }

    public static <S> void execAsync(String actorName, Consumer<S> worker) {
        requireNameNotNull(actorName);
        ActorUtils.execAsync(getOrCreateActorQueue(actorName, defaultQueueCapacity), worker);
    }

    public static <S> void execAndWait(String actorName, Consumer<S> worker) {
        requireNameNotNull(actorName);
        ActorUtils.execAndWait(getOrCreateActorQueue(actorName, defaultQueueCapacity), worker);
    }

    public static <T, S> CompletableFuture<Void> execFuture(String actorName, Consumer<S> worker) {
        requireNameNotNull(actorName);
        return ActorUtils.execFuture(getOrCreateActorQueue(actorName, defaultQueueCapacity), worker);
    }

    /**
     * Return true if the actor is potentially available, as this method just checks if the queue is present;
     */
    public static boolean isActorAvailable(String name) {
        return namedQueues.containsKey(name);
    }

    static int getActorQueueSize(String name) {
        MiniQueue queue = namedQueues.get(name);

        if (queue == null)
            return -1;

        return queue.size();
    }

    private static <S> Consumer<S> getQueueFinalizer(String queueToDelete, Consumer<S> finalizer, boolean queueRecreationProtection) {
        if (queueToDelete == null)
            return finalizer;

        Consumer<S> consumer = state -> {
            if (queueRecreationProtection)
                namedQueues.put(queueToDelete, DROPPING_QUEUE);
            else
                namedQueues.remove(queueToDelete);
        };

        return finalizer == null ? consumer : consumer.andThen(finalizer);
    }

    public static CreationStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public static void setDefaultStrategy(CreationStrategy defaulStrategy) {
        ActorSystem.defaultStrategy = defaulStrategy;
    }

    /**
     * Visits all the names of the named actors
     */
    public void visitNamedActors(Consumer<String> visitor) {
        Enumeration<String> names = namedQueues.keys();

        while (names.hasMoreElements())
            visitor.accept(names.nextElement());
    }

    public static void setDefaultQueueCapacity(int defaultQueueCapacity) {
        ActorSystem.defaultQueueCapacity = defaultQueueCapacity;
    }

    public static void setDefaultPollTimeoutMs(int defaultPollTimeoutMs) {
        ActorSystem.defaultPollTimeoutMs = defaultPollTimeoutMs;
    }
}
