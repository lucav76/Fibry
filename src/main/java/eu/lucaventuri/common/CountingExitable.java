package eu.lucaventuri.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CountingExitable extends Exitable {
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger finished = new AtomicInteger();

    @Override
    public boolean isExiting() {
        int cr = created.get();

        return cr>0 && finished.get()==cr ;
    }

    @Override
    public boolean isFinished() {
        return isExiting();
    }

    @Override
    public void askExit() {
    }

    @Override
    public void askExitAndWait() {
        waitForExit();
    }

    @Override
    public void askExitAndWait(long timeout, TimeUnit unit) {
        waitForExit();
    }

    @Override
    protected void notifyFinished() {
        throw new UnsupportedOperationException("notifyFinished() is not supported ");
    }

    @Override
    public void waitForExit() {
        while(!isFinished()) {
            SystemUtils.sleep(1);
        }
    }

    @Override
    public void waitForExit(long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("waitForExit() is supported only without a timeout");
    }

    public void addCreated() { created.incrementAndGet(); }

    public void addFinished() { finished.incrementAndGet(); }
}
