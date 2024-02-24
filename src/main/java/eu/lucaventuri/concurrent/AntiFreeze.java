package eu.lucaventuri.concurrent;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Generator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Class that can try to un-freeze a thread stuck on waiting operations, like hanging network calls.
 * - During normal operations, the user should call notifyActivity() to prevent the activity timeout to be triggers;
 * - The user can call execute() with the logic to run.
 * - If the user does nto want to call execute(), then when task is finished, the user should call notifyFinished() to stop the scheduling of the anti-freeze
 * - At the end of the application, the user should ideally call stopScheduler(), or the scheduling thread might prevent the JVM from shutting down
 * */
public class AntiFreeze {
    private final int activityTimeoutMs;
    private final int taskTimeoutMs;
    private final long startTime = System.currentTimeMillis();
    private final AtomicLong lastActivityTime = new AtomicLong(startTime);
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Runnable onTimeout;
    private final static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AntiFreeze(int activityTimeoutMs, int taskTimeoutMs, Runnable onTimeout) {
        assert activityTimeoutMs <= taskTimeoutMs;
        Thread callingThread = Thread.currentThread();

        this.activityTimeoutMs = activityTimeoutMs;
        this.taskTimeoutMs = Math.max(activityTimeoutMs, taskTimeoutMs);
        this.onTimeout = onTimeout == null ? callingThread::interrupt : onTimeout;

        AtomicReference<Runnable> runRef = new AtomicReference<>();
        Runnable run = () -> {
            if (taskStillExecuting()) {
                scheduler.schedule(runRef.get(), activityTimeoutMs / 3, TimeUnit.MILLISECONDS);
            }
        };

        runRef.set(run);
        run.run();
    }

    public AntiFreeze(int activityTimeoutMs, int taskTimeoutMs) {
        this(activityTimeoutMs, taskTimeoutMs, null);
    }

    public AntiFreeze(Thread threadToStop, int activityTimeoutMs, int taskTimeoutMs) {
        this(activityTimeoutMs, taskTimeoutMs, threadToStop == null ? null : () -> Exceptions.rethrowRuntime(threadToStop::interrupt));
    }

    /* Return false if it is frozen, which should stop the scheduling */
    private boolean taskStillExecuting() {
        long now = System.currentTimeMillis();
        long activityTimeElapsed = now - lastActivityTime.get();
        long taskTimeElapsed = now - startTime;

        if (finished.get())
            return false;

        if (activityTimeElapsed >= activityTimeoutMs || taskTimeElapsed >= taskTimeoutMs) {
            onTimeout.run();

            return false;
        }

        return true;
    }

    public void notifyActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    public void notifyFinished() {
        finished.set(true);
    }

    public static void stopScheduler() {
        scheduler.shutdown();
    }

    public static void execute(int activityTimeoutMs, int taskTimeoutMs, Consumer<AntiFreeze> worker) {
        AntiFreeze antiFreeze = new AntiFreeze(activityTimeoutMs, taskTimeoutMs);

        try {
            worker.accept(antiFreeze);
        } finally {
            antiFreeze.notifyFinished();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();

        try {
            System.out.println("Starting...");
            Generator.fromProducer(yielder -> {
                for (int i=0; i<10; i++) {
                    SystemUtils.sleep(1000);
                    yielder.yield(i);
                }
            }, 10,false ,2_000 ,5_000 ).toStream().forEach(System.out::println);

            System.out.println("Finished...");
        } finally {
            System.out.println("Time passed: " + (System.currentTimeMillis() - start));
            AntiFreeze.stopScheduler();
        }
    }
}
