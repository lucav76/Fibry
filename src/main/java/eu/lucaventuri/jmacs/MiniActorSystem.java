package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.functional.Either3;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Super simple actor system, creating one thread per Actor. Each Actor can either process messages (with or without return) or execute Runnables inside its thread */
public class MiniActorSystem {
    private static final ConcurrentHashMap<String, BlockingDeque> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();

    public static enum Strategy {
        THREAD {
            @Override
            <T, R> Actor<T, R> start(Actor<T, R> actor) {
                new Thread(actor::processMessages).start();

                return actor;
            }
        }, FIBER {
            @Override
            <T, R> Actor<T, R> start(Actor<T, R> actor) {
                return THREAD.start(actor);
            }
        }, AUTO {
            @Override
            <T, R> Actor<T, R> start(Actor<T, R> actor) {
                return THREAD.start(actor);
            }
        };

        abstract <T, R> Actor<T, R> start(Actor<T, R> actor);
    }



    public static <T> Actor<T, Void> newActor(Consumer<T> actorLogic, Strategy strategy) {
        return (Actor<T, Void>) strategy.start(new Actor<T, Void>(actorLogic, ActorUtils.discardingToReturning(actorLogic)));
    }

    public static <T, Void> Actor<T, Void> newActor(String actorName, Consumer<T> actorLogic, Strategy strategy) {
        return (Actor<T, Void>) strategy.start(new Actor<T, Void>(actorLogic, ActorUtils.discardingToReturning(actorLogic), getOrCreateActorQueue(registerActorName(actorName))));
    }

    protected static String registerActorName(String actorName) {
        boolean newActor = actorNamesInUse.add(actorName);

        if (!newActor)
            throw new IllegalArgumentException("Actors name already in use!");

        return actorName;
    }

    public static <T, R> Actor<T, R> newActorWithReturn(Function<T, R> actorLogic, Strategy strategy) {
        return (Actor<T, R>) strategy.start(new Actor<>(ActorUtils.returningToDiscarding(actorLogic), actorLogic));
    }

    public static <T, R> Actor<T, R> newActorWithReturn(String actorName, Function<T, R> actorLogic, Strategy strategy) {
        return (Actor<T, R>) strategy.start(new Actor<>(ActorUtils.returningToDiscarding(actorLogic), actorLogic, getOrCreateActorQueue(registerActorName(actorName))));
    }

    private static <T, R> LinkedBlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>> getOrCreateActorQueue(String actorName) {
        return (LinkedBlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>>) namedQueues.computeIfAbsent(actorName, name -> new LinkedBlockingDeque<Either3<Runnable, T, MessageWithAnswer<T, R>>>());
    }

    public static <T> void sendMessage(String actorName, T message) {
        ActorUtils.sendMessage(getOrCreateActorQueue(actorName), message);
    }

    public static <T, R> CompletableFuture<R> sendMessageReturn(String actorName, T message) {
        return ActorUtils.sendMessageReturn(getOrCreateActorQueue(actorName), message);
    }

    public static void execAsync(String actorName, Runnable run) {
        ActorUtils.execAsync(getOrCreateActorQueue(actorName), run);
    }

    public static void execAndWait(String actorName, Runnable run) {
        ActorUtils.execAndWait(getOrCreateActorQueue(actorName), run);
    }

    public static <T> CompletableFuture<Void> execFuture(String actorName, Runnable run) {
        return ActorUtils.execFuture(getOrCreateActorQueue(actorName), run);
    }
}
