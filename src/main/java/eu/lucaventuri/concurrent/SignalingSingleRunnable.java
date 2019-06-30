package eu.lucaventuri.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runnable that can run only once, and that can signal when it has run */
public class SignalingSingleRunnable implements Runnable {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Runnable runnable;

    private SignalingSingleRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        assert runnable != null;

        if (runnable != null)
            runnable.run();

        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        latch.await(timeout, unit);
    }

    public boolean isDone() {
        return latch.getCount() == 0;
    }

    public static SignalingSingleRunnable of(Runnable runnable) {
        if (runnable instanceof SignalingSingleRunnable)
            return (SignalingSingleRunnable) runnable;

        return new SignalingSingleRunnable(runnable);
    }
}
