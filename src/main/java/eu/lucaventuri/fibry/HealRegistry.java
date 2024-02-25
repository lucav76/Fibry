package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ConcurrentHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

class HealthState {
    // After this time, the thread will be interrupted
    private final long deadlineInterruption;
    private final Thread actorTthread;
    private final Runnable onNewThread;

    HealthState(long deadlineInterruption, Thread actorTthread, Runnable onNewThread) {
        this.deadlineInterruption = deadlineInterruption;
        this.actorTthread = actorTthread;
        this.onNewThread = onNewThread;
    }

    long deadlineRecreation() {
        return deadlineInterruption + HealRegistry.INSTANCE.gracePeriodInterruptionMs;
    }

    long deadlineTimeout() {
        return deadlineInterruption;
    }

    Thread thread() {
        return actorTthread;
    }

    Runnable onNewThread() {
        return onNewThread;
    }
}

public enum HealRegistry {
    INSTANCE;

    volatile int gracePeriodInterruptionMs = 10_000;
    volatile int checkFrequencyMs = 10_000;


    private final ConcurrentHashMap<BaseActor, HealthState> actors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MiniQueue, Integer> threadsLeft = new ConcurrentHashMap<>();
    private final AtomicReference<ScheduledExecutorService> sched = new AtomicReference<>();
    private final Set<BaseActor> threadsToKill = ConcurrentHashSet.build();

    private static final Logger logger = Logger.getLogger(HealRegistry.class.getName());

    void put(BaseActor actor, ActorSystem.AutoHealingSettings autoHealing, Thread actorTthread) {
        long now = System.currentTimeMillis();

        threadsLeft.putIfAbsent(actor.queue, autoHealing.maxThreads);

        actors.put(actor, new HealthState(now + autoHealing.executionTimeoutSeconds * 1000L, actorTthread, autoHealing.onNewThread));
    }

    void addThread(BaseActor actor) {
        threadsLeft.merge(actor.queue, 1, Integer::sum);
    }

    void startThread() {
        if (sched.get() == null) {
            synchronized (this) {
                if (sched.get() == null) {
                    var newScheduler = Executors.newSingleThreadScheduledExecutor(run -> new Thread(run, "Fibry Heal Registry"));

                    if (sched.compareAndSet(null, newScheduler)) {
                        newScheduler.scheduleWithFixedDelay(logic(), checkFrequencyMs, checkFrequencyMs, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
    }

    public void shutdown() {
        if (sched.get() != null)
            sched.get().shutdownNow();
    }

    void remove(BaseActor actor, Thread actorTthread, AtomicBoolean threadShouldDie) {
        var status = actors.get(actor);

        if (status != null) {
            if (status.thread() == actorTthread) {
                actors.remove(actor, status);
                return;
            }
        }

        threadShouldDie.set(threadsToKill.contains(actor));
    }

    Runnable logic() {
        return () -> {
            long now = System.currentTimeMillis();
            List<Map.Entry<BaseActor, HealthState>> entriesToRemove = new ArrayList<>();

            for (var entry : actors.entrySet()) {
                if (entry.getValue().deadlineRecreation() <= now) {
                    if (threadsLeft.get(entry.getKey().queue) > 0) {
                        threadsLeft.compute(entry.getKey().queue, (queue, threads) -> threads - 1);
                        logger.log(Level.FINEST, "Fibry AutoHealing - Recreating " + entry.getValue().thread().getName() + " - attempts left: " + threadsLeft.get(entry.getKey().queue));
                        entriesToRemove.add(entry);
                        threadsToKill.add(entry.getKey());
                        entry.getKey().recreate();
                        if (entry.getValue().onNewThread() != null)
                            entry.getValue().onNewThread().run();
                    }
                } else if (entry.getValue().deadlineTimeout() <= now) {
                    logger.log(Level.FINEST, "Fibry AutoHealing - Interrupting " + entry.getValue().thread().getName());
                    entry.getValue().thread().interrupt();
                }
            }

            for (var entry : entriesToRemove)
                actors.remove(entry.getKey(), entry.getValue());
        };
    }

    public void setFrequency(int frequency, TimeUnit unit) {
        checkFrequencyMs = (int) unit.toMillis(frequency);
    }

    public void setGracePeriod(int gracePeriod, TimeUnit unit) {
        gracePeriodInterruptionMs = (int) unit.toMillis(gracePeriod);
    }
}
