package eu.lucaventuri.jmacs;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.functional.Either;

import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Super simple actor system, creating one thread per Actor. Each Actor can either process messages or execute Runnables inside its thread */
public class MiniActorSystem {
    private static final ConcurrentHashMap<String, BlockingDeque> namedQueues = new ConcurrentHashMap<>();
    private static final Set<String> actorNamesInUse = ConcurrentHashSet.build();

    public static <T> ActorWithoutReturn<T> newActor(Consumer<T> actorLogic, boolean useFibers) {
        return new ActorWithoutReturn<>(actorLogic, useFibers);
    }

    public static <T> ActorWithoutReturn<T> newActor(String actorName, Consumer<T> actorLogic, boolean useFibers) {
        return new ActorWithoutReturn<>(actorLogic, getOrCreateActorQueue(registerActorName(actorName)), useFibers);
    }

    protected static String registerActorName(String actorName) {
        boolean newActor = actorNamesInUse.add(actorName);

        if (!newActor)
            throw new IllegalArgumentException("Actors name already in use!");

        return actorName;
    }

    public static <T, R> ActorWithReturn<T, R> newActorWithReturn(Function<T, R> actorLogic, boolean useFibers) {
        return new ActorWithReturn<>(actorLogic, useFibers);
    }

    public static <T, R> ActorWithReturn<T, R> newActorWithReturn(String actorName, Function<T, R> actorLogic, boolean useFibers) {
        return new ActorWithReturn<>(actorLogic, getOrCreateActorQueue(registerActorName(actorName)), useFibers);
    }

    private static <T> LinkedBlockingDeque<Either<Runnable, T>> getOrCreateActorQueue(String actorName) {
        return (LinkedBlockingDeque<Either<Runnable, T>>) namedQueues.computeIfAbsent(actorName, name -> new LinkedBlockingDeque<Either<Runnable, T>>());
    }

    public static <T> void sendToActor(String actorName, T message) {
        ActorUtils.sendMessage(getOrCreateActorQueue(actorName), message);
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
