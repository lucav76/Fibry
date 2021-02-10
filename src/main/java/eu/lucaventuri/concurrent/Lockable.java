package eu.lucaventuri.concurrent;

import java.util.concurrent.Semaphore;
import java.util.function.Function;

import eu.lucaventuri.fibry.ActorSystem;

public interface Lockable {
    interface Unlock extends AutoCloseable {
        void unlock();

        @Override
        default void close() throws Exception {
            unlock();
        }
    }

    Unlock acquire(int numPermits) throws InterruptedException;
    default Unlock acquire() throws InterruptedException { return acquire(1); }

    static Lockable fromSemaphore(int numPermits, boolean fair) {
        Semaphore semaphore = new Semaphore(numPermits, fair);

        return permitsRequested -> {
            semaphore.acquire(permitsRequested);

            return () -> semaphore.release(permitsRequested);
        };
    }

    static Lockable fromActor(int numPermits) {
        Semaphore semaphore = new Semaphore(numPermits, false);

        Function<Integer, Unlock> actorAsFunction = ActorSystem.anonymous().newActorWithReturn(permitsRequested -> {
            try {
                semaphore.acquire(permitsRequested);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return () -> semaphore.release(permitsRequested);
        });

        return actorAsFunction::apply;
    }
}