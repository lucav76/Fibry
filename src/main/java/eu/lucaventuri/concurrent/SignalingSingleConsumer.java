package eu.lucaventuri.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runnable that can run only once, and that can signal when it has run */
public class SignalingSingleConsumer<S> implements Consumer<S> {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final Consumer worker;

    private SignalingSingleConsumer(Consumer worker) {
        this.worker = worker;
    }

    @Override
    public void accept(S object) {
        assert worker != null;

        if (worker != null)
            worker.accept(object);

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

    public static <S> SignalingSingleConsumer<S> of(Consumer<S> worker) {
        if (worker instanceof SignalingSingleConsumer)
            return (SignalingSingleConsumer<S>) worker;

        return new SignalingSingleConsumer<S>(worker);
    }
}
