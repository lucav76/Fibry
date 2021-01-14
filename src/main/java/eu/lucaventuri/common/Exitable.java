package eu.lucaventuri.common;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class meant to be used by tasks that can be interrupted.
 * Remember to call notifyFinished(), or the behavior will break.
 * <p>
 * This class implements Closeable, however the correct behaviour to apply might be task dependent, therefore it is possible to set a CloseStrategy to customize the close() method.
 */
public class Exitable implements Closeable, CanExit {
    private final AtomicBoolean exiting;
    private final CountDownLatch finished;
    protected volatile CloseStrategy closeStrategy;
    protected volatile boolean sendPoisonPillWhenExiting;

    /** Possible strategies to use when close() is called */
    public enum CloseStrategy {
        ASK_EXIT, ASK_EXIT_AND_WAIT, SEND_POISON_PILL, SEND_POISON_PILL_AND_WAIT, WAIT_FOR_EXIT /** Only if you know something else will close is*/, NONE;

        /** Convert a ClosingStrategy to the equivalent non-blocking one */
        public CloseStrategy removeWait() {
            if (this==ASK_EXIT_AND_WAIT)
                return ASK_EXIT;

            if (this==SEND_POISON_PILL_AND_WAIT)
                return SEND_POISON_PILL;

            if (this==WAIT_FOR_EXIT)
                return NONE;

            return this;
        }
    }

    public Exitable() {
        exiting = new AtomicBoolean(false);
        finished = new CountDownLatch(1);
        closeStrategy = CloseStrategy.ASK_EXIT;
        sendPoisonPillWhenExiting = false;
    }

    public Exitable(CloseStrategy strategy) {
        exiting = new AtomicBoolean(false);
        finished = new CountDownLatch(1);
        closeStrategy = strategy;
        sendPoisonPillWhenExiting = false;
    }

    /** Use with absolute care, as this makes the two exitables working like one, but potentially with different
     * strategies. This is intended for "light transactional actors" */
    public Exitable(Exitable exitable) {
        exiting = exitable.exiting;
        finished = exitable.finished;
        closeStrategy = exitable.closeStrategy;
        sendPoisonPillWhenExiting = exitable.sendPoisonPillWhenExiting;
    }

    /** @return true if there has been a request to exit */
    public boolean isExiting() {
        return exiting.get();
    }

    /** @return true if the task has been finished for good */
    public boolean isFinished() {
        return finished.getCount() == 0;
    }

    /** Asks to exit as soon as possible; optionally this call can also send a poison pill, which can be useful on some circumstances */
    @Override
    public void askExit() {
        if (sendPoisonPillWhenExiting)
            sendPoisonPill();

        exiting.set(true);
    }

    /** Asks to exit and wait until it happens */
    public void askExitAndWait() {
        askExit();
        waitForExit();
    }

    /**
     * Asks to exit and wait until it happens
     *
     * @param timeout Timeout
     * @param unit Unit of the timeout
     */
    public void askExitAndWait(long timeout, TimeUnit unit) {
        askExit();
        waitForExit(timeout, unit);
    }

    /** Notifies that the task is now finished */
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

    /**
     * Wait for the task to finish, without asking for it.
     *
     * @param timeout Timeout
     * @param unit Unit of the timeout
     */
    public void waitForExit(long timeout, TimeUnit unit) {
        try {
            finished.await(timeout, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /** Send a poison pill; this behavior is too specific, and therefore no default implementation is possible
     * @return true if the pill has been sent*/
    public boolean sendPoisonPill() {
        return false;
    }

    /**
     * Sets if askExit() should also send a poison pill
     * @param send true to send the poison pill
     * @return
     */
    public Exitable setExitSendsPoisonPill(boolean send) {
        sendPoisonPillWhenExiting = send;

        return this;
    }

    @Override
    public void close() {
        switch (closeStrategy) {
            case SEND_POISON_PILL_AND_WAIT:
                sendPoisonPill();
                waitForExit();
                break;
            case SEND_POISON_PILL:
                sendPoisonPill();
                break;
            case ASK_EXIT:
                askExit();
                break;
            case ASK_EXIT_AND_WAIT:
                askExitAndWait();
                break;
            case WAIT_FOR_EXIT:
                waitForExit();
                break;
            case NONE:
                break;
            default:
                askExit();
        }
    }

    public void removeWaitOnClose() {
        this.closeStrategy = closeStrategy.removeWait();
    }

    public void setCloseStrategy(CloseStrategy closeStrategy) {
        this.closeStrategy = closeStrategy;
    }
}
