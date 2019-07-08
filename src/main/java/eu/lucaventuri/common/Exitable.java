package eu.lucaventuri.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class meant to be used by tasks that can be interrupted.
 * Remember to call notifyFinished(), or the behavior will break
 */
public class Exitable {
    private final AtomicBoolean exiting = new AtomicBoolean(false);
    private final CountDownLatch finished = new CountDownLatch(1);

    public boolean isExiting() {
        return exiting.get();
    }

    public boolean isFinished() {
        return finished.getCount() == 0;
    }

    public void askExit() {
        exiting.set(true);
    }

    public void askExitAndWait() {
        askExit();
        waitForExit();
    }

    public void askExitAndWait(long timeout, TimeUnit unit) {
        askExit();
        waitForExit(timeout, unit);
    }

    protected void notifyFinished() {
        finished.countDown();
    }

    /** Wait for the task to finish, without asking for it */
    public void waitForExit() {
        try {
            finished.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /** Wait for the task to finish, without asking for it */
    public void waitForExit(long timeout, TimeUnit unit) {
        try {
            finished.await(timeout, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
