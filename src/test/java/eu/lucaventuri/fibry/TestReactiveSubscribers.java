package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class PublisherTest implements Flow.Publisher<Integer> {
    private final AtomicInteger numSent = new AtomicInteger();
    private final int numMax = 1000;

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            private final AtomicBoolean completed = new AtomicBoolean(false);
            private final AtomicInteger numMessagesToSend = new AtomicInteger();
            private final Actor<Flow.Subscriber<? super Integer>, Void, Void> actorRefill = ActorSystem.anonymous().newActor(sub -> {
                while (numSent.get() < numMax && numMessagesToSend.get() > 0) {
                    subscriber.onNext(numSent.incrementAndGet());
                    numMessagesToSend.decrementAndGet();
                }

                if (numSent.get() >= numMax) {
                    if (completed.compareAndSet(false, true))
                        subscriber.onComplete();
                }
            });

            @Override
            public void request(long n) {
                if (numSent.get() >= numMax)
                    return;

                numMessagesToSend.accumulateAndGet((int) n, Math::max);

                actorRefill.sendMessage(subscriber);
            }

            @Override
            public void cancel() {
                numSent.set(numMax);
            }
        });
    }
}

public class TestReactiveSubscribers {
    @Test
    public void testReactive() throws InterruptedException {
        final AtomicLong maxQueueSize = new AtomicLong();
        final AtomicLong maxNum = new AtomicLong();
        final AtomicInteger numReceived = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);

        var pub = new PublisherTest();
        var sub = ActorSystem.anonymous().initialState(new AtomicInteger()).<Integer>newActor((num, actor) -> {
            numReceived.incrementAndGet();
            actor.getState().addAndGet(num);
            if (num>maxNum.get())
                maxNum.set(num);
            long queueLen = ((Actor) actor).getQueueLength();
            if (maxQueueSize.get() < queueLen)
                maxQueueSize.set(queueLen);
            SystemUtils.sleep(1);
        }).asReactiveSubscriber(100, null, actor -> {
            System.out.println("Total: " + actor.getState().get());
            System.out.println("Max Queue size: " + maxQueueSize.get());
            System.out.println("Messages received: " + numReceived.get());
            System.out.println("Max Num: " + maxNum.get());
            latch.countDown();
        });

        pub.subscribe(sub);

        latch.await();
    }
}
