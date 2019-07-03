package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.functional.Either3;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Super simple actor system, creating one thread per Actor. Each Actor can either process messages (with or without return) or execute Consumer inside its thread */
public class MiniActorSystem {
    private static final ConcurrentHashMap<String, BlockingDeque> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();

    public static enum Strategy {
        THREAD {
            @Override
            <T, R, S> Actor<T, R, S> start(Actor<T, R, S> actor) {
                new Thread(actor::processMessages).start();

                return actor;
            }
        }, FIBER {
            @Override
            <T, R, S> Actor<T, R, S> start(Actor<T, R, S> actor) {
                return THREAD.start(actor);
            }
        }, AUTO {
            @Override
            <T, R, S> Actor<T, R, S> start(Actor<T, R, S> actor) {
                return THREAD.start(actor);
            }
        };

        abstract <T, R, S> Actor<T, R, S> start(Actor<T, R, S> actor);
    }

    public static class NamedActorCreator {
        private final String name;  // Can be null
        private Strategy strategy = Strategy.AUTO;

        public class NamedStateActorCreator<S> {
            private final S initialState;

            public NamedStateActorCreator(S initialState) {
                this.initialState = initialState;
            }

            public <T> Actor<T, Void, S> newActor(Consumer<T> actorLogic) {
                return (Actor<T, Void, S>) strategy.start(new Actor<T, Void, S>(actorLogic, ActorUtils.discardingToReturning(actorLogic), getOrCreateActorQueue(registerActorName(name)), initialState));
            }

            public <T, R> Actor<T, R, S> newActorWithReturn(Function<T, R> actorLogic) {
                return (Actor<T, R, S>) strategy.start(new Actor<T, R, S>(ActorUtils.returningToDiscarding(actorLogic), actorLogic, getOrCreateActorQueue(registerActorName(name)), initialState));
            }
        }

        private NamedActorCreator(String name) {this.name = name; }

        public <T> Actor<T, Void, Void> newActor(Consumer<T> actorLogic) {
            return (Actor<T, Void, Void>) strategy.start(new Actor<T, Void, Void>(actorLogic, ActorUtils.discardingToReturning(actorLogic), getOrCreateActorQueue(registerActorName(name)), null));
        }

        public <T, R> Actor<T, R, Void> newActorWithReturn(Function<T, R> actorLogic) {
            return (Actor<T, R, Void>) strategy.start(new Actor<T, R, Void>(ActorUtils.returningToDiscarding(actorLogic), actorLogic, getOrCreateActorQueue(registerActorName(name)), null));
        }

        public <S> NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(state);
        }

        public NamedActorCreator strategy(Strategy strategy) {
            this.strategy = strategy;

            return this;
        }
    }

    public static NamedActorCreator named(String name) {
        return new NamedActorCreator(name);
    }

    public static NamedActorCreator anonymous() {
        return new NamedActorCreator(null);
    }

    protected static String registerActorName(String actorName) {
        if (actorName==null)
            return null;

        boolean newActor = actorNamesInUse.add(actorName);

        if (!newActor)
            throw new IllegalArgumentException("Actors name already in use!");

        return actorName;
    }

    private static <T, R, S> LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> getOrCreateActorQueue(String actorName) {
        if (actorName==null)
            return new LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>>();

        return (LinkedBlockingDeque<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>>) namedQueues.computeIfAbsent(actorName, name -> new LinkedBlockingDeque<Either3<Consumer, T, MessageWithAnswer<T, R>>>());
    }

    private static void enforceName(String actorName) {
        if (actorName==null)
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
