package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.pubsub.PubSub;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TestPubSub {
    @Test
    public void testCurrentThread() throws InterruptedException {
        AtomicInteger numA = new AtomicInteger();
        AtomicInteger numB = new AtomicInteger();
        PubSub ps = PubSub.sameThread();
        addSubscribers(numA, numB, ps);

        verifySync(numA, numB, ps);
        verifyAsync(numA, numB, ps);
    }

    private void verifySync(AtomicInteger numA, AtomicInteger numB, PubSub ps) {
        numA.set(0);
        numB.set(0);

        assertEquals(0, numA.get());
        assertEquals(0, numB.get());

        ps.publish("a", 1);
        assertEquals(201, numA.get());
        assertEquals(0, numB.get());
        ps.publish("a", 3);
        assertEquals(804, numA.get());
        assertEquals(0, numB.get());

        ps.publish("b", 1);
        assertEquals(804, numA.get());
        assertEquals(101, numB.get());
        ps.publish("b", 9);
        assertEquals(804, numA.get());
        assertEquals(1010, numB.get());
    }

    @Test
    public void testOneActor() throws InterruptedException {
        AtomicInteger numA = new AtomicInteger();
        AtomicInteger numB = new AtomicInteger();
        PubSub ps = PubSub.oneActor();
        addSubscribers(numA, numB, ps);

        verifyAsync(numA, numB, ps);
        verifySyncFails(numA, numB, ps);
    }

    private void verifySyncFails(AtomicInteger numA, AtomicInteger numB, PubSub ps) {
        try {
            verifySync(numA, numB, ps);
            fail("Sync should not work...");
        }
        catch(AssertionError e) {
        }
    }

    @Test
    public void testOneActorPerTopic() throws InterruptedException {
        AtomicInteger numA = new AtomicInteger();
        AtomicInteger numB = new AtomicInteger();
        PubSub ps = PubSub.oneActorPerTopic();
        addSubscribers(numA, numB, ps);

        verifyAsync(numA, numB, ps);
        verifySyncFails(numA, numB, ps);
    }

    @Test
    public void testOneActorPerSubscriber() throws InterruptedException {
        AtomicInteger numA = new AtomicInteger();
        AtomicInteger numB = new AtomicInteger();
        PubSub ps = PubSub.oneActorPerSubscriber();
        addSubscribers(numA, numB, ps);

        verifyAsync(numA, numB, ps);
        verifySyncFails(numA, numB, ps);
    }

    @Test
    public void testOneActorPerMessage() throws InterruptedException {
        AtomicInteger numA = new AtomicInteger();
        AtomicInteger numB = new AtomicInteger();
        PubSub ps = PubSub.oneActorPerMessage();
        addSubscribers(numA, numB, ps);

        verifyAsync(numA, numB, ps);
        verifySyncFails(numA, numB, ps);
    }

    private void verifyAsync(AtomicInteger numA, AtomicInteger numB, PubSub ps) {
        numA.set(0);
        numB.set(0);

        assertEquals(0, numA.get());
        assertEquals(0, numB.get());

        ps.publish("a", 1);

        SystemUtils.sleepUntil(() -> numA.get() == 201, 1);
        assertEquals(201, numA.get());
        assertEquals(0, numB.get());
        ps.publish("a", 3);
        SystemUtils.sleepUntil(() -> numA.get() == 804, 1);
        assertEquals(804, numA.get());
        assertEquals(0, numB.get());

        ps.publish("b", 1);
        SystemUtils.sleepUntil(() -> numB.get() == 101, 1);
        assertEquals(804, numA.get());
        assertEquals(101, numB.get());
        ps.publish("b", 9);
        assertEquals(804, numA.get());
        SystemUtils.sleepUntil(() -> numB.get() == 1010, 1);
        assertEquals(1010, numB.get());
    }

    private void addSubscribers(AtomicInteger numA, AtomicInteger numB, PubSub ps) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);

        ps.subscribe("a", val -> numA.addAndGet((Integer) val));
        ps.subscribe("b", val -> numB.addAndGet((Integer) val));

        for (int i = 0; i < 100; i++)
            Stereotypes.def().runOnce(() -> {
                ps.subscribe("a", val -> numA.addAndGet(((Integer) val) * 2));
                ps.subscribe("b", val -> numB.addAndGet((Integer) val));

                latch.countDown();
            });

        latch.await();
    }

    @Test
    public void simpleTest() {
        PubSub<String> ps = PubSub.oneActorPerTopic();

        ps.subscribe("test", System.out::println);
        ps.publish("test", "HelloWorld!");

        SystemUtils.sleep(10);
    }
}
