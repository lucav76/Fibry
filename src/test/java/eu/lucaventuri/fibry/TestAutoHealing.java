package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestAutoHealing {
    @BeforeClass
    public static void setup() {
        HealRegistry.INSTANCE.setFrequency(50, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testInterruption() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10_000, TimeUnit.MILLISECONDS);
        CountDownLatch latchInterruption = new CountDownLatch(1);

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 2, latchInterruption::countDown, () -> Assert.fail("Unexpected Notification by AutoHealing - New Thread"))).newActor(SystemUtils::sleep);

        actor.sendMessage(30_000L);

        latchInterruption.await();
        System.out.println("Latch counted down");
        actor.sendMessageReturn(10L).get();
    }


    @Test
    public void testRecreate() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        CountDownLatch latchRecreate = new CountDownLatch(1);

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 2, () -> Assert.fail("Unexpected Notification by AutoHealing - Thread interrupted"), latchRecreate::countDown)).newActor(SystemUtils::sleepEnsure);

        actor.sendMessage(30_000L);

        latchRecreate.await();
        System.out.println("Latch counted down");
        actor.sendMessageReturn(10L).get();
    }

    @Test
    public void testRecreate3() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        CountDownLatch latchRecreate = new CountDownLatch(3);

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 5, () -> Assert.fail("Unexpected Notification by AutoHealing - Thread interrupted"), latchRecreate::countDown)).newActor(SystemUtils::sleepEnsure);

        actor.sendMessage(30_000L);
        actor.sendMessage(30_000L);
        actor.sendMessage(30_000L);

        latchRecreate.await();
        System.out.println("Latch counted down");
        actor.sendMessageReturn(10L).get();
    }

    @Test
    public void testWait() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        AtomicInteger actions = new AtomicInteger();

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 2, actions::incrementAndGet, actions::incrementAndGet)).newActor(SystemUtils::sleep);

        actor.sendMessage(50L);
        SystemUtils.sleep(1500);
        actor.sendMessageReturn(50L).get();

        Assert.assertEquals(0, actions.get());
    }

    @Test
    public void testMax() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        CountDownLatch latchRecreate = new CountDownLatch(2);
        long start = System.currentTimeMillis();

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 1, () -> Assert.fail("Unexpected Notification by AutoHealing - Thread interrupted"), latchRecreate::countDown)).newActor(SystemUtils::sleepEnsure);

        actor.sendMessage(3_000L);
        actor.sendMessage(3_000L);
        actor.sendMessage(3_000L);
        actor.sendMessage(3_000L);

        latchRecreate.await();
        System.out.println("Latch counted down");
        long partial = System.currentTimeMillis();
        actor.sendMessageReturn(10L).get();
        long end = System.currentTimeMillis();
        System.out.println((partial - start) + " - " + (end - partial) + ": " + (end - start));
        Assert.assertTrue(end - start < 9000);
        Assert.assertTrue(end - partial >= 3000);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void tesFailOnFibers() {
        ActorSystem.anonymous().strategy(CreationStrategy.FIBER).autoHealing(new ActorSystem.AutoHealingSettings(1, 2, null, null)).newActor(SystemUtils::sleepEnsure);
    }

    @Test
    public void testMaxRecovering() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        CountDownLatch latchRecreate = new CountDownLatch(2);
        long start = System.currentTimeMillis();

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 2, () -> Assert.fail("Unexpected Notification by AutoHealing - Thread interrupted"), latchRecreate::countDown)).newActor(SystemUtils::sleepEnsure);

        actor.sendMessage(2_000L);
        actor.sendMessage(2_000L);
        actor.sendMessage(2_000L);
        actor.sendMessage(2_000L);

        latchRecreate.await();
        System.out.println("Latch counted down");
        long partial = System.currentTimeMillis();
        actor.sendMessageReturn(10L).get();
        long end = System.currentTimeMillis();
        System.out.println((partial - start) + " - " + (end - partial) + ": " + (end - start));
        Assert.assertTrue(end - start < 6000);
        Assert.assertTrue(end - partial >= -4000);
    }

    @Test
    public void testSchedulingNoHealing() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);

        try (SinkActorSingleTask<Void> actor = Stereotypes.threads().scheduleWithFixedDelay(latch::countDown, 0, 1, TimeUnit.MILLISECONDS, null)) {
            latch.await();
        }
    }

    @Test
    public void testSchedulingHealing() throws Exception {
        HealRegistry.INSTANCE.setGracePeriod(200, TimeUnit.MILLISECONDS);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger interruptions = new AtomicInteger();
        AtomicInteger recreations = new AtomicInteger();

        try (SinkActorSingleTask<Void> actor = Stereotypes.threads().scheduleWithFixedDelay(() -> {
            calls.incrementAndGet();
            if (count.get() < 3) {
                long ms = 2000 / calls.get();
                System.out.println("Waiting for " + ms);
                SystemUtils.sleep(ms);
                latch.countDown();
                count.incrementAndGet();
            }
        }, 0, 1, TimeUnit.MILLISECONDS, new ActorSystem.AutoHealingSettings(1, 5, interruptions::incrementAndGet, recreations::incrementAndGet))) {
            latch.await();
            actor.askExit();
        }

        Assert.assertEquals(4, calls.get());
        Assert.assertEquals(3, count.get());
        Assert.assertEquals(1, interruptions.get());
        Assert.assertEquals(0, recreations.get());
    }

    @Test
    public void testSchedulingHealing2() throws Exception {
        HealRegistry.INSTANCE.setGracePeriod(200, TimeUnit.MILLISECONDS);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger interruptions = new AtomicInteger();
        AtomicInteger recreations = new AtomicInteger();

        try (SinkActorSingleTask<Void> actor = Stereotypes.threads().scheduleWithFixedDelay(() -> {
            calls.incrementAndGet();
            if (count.get() < 3) {
                long ms = 2000 / calls.get();
                System.out.println("Waiting for " + ms);
                SystemUtils.sleepEnsure(ms);
                latch.countDown();
                count.incrementAndGet();
            }
        }, 0, 1, TimeUnit.MILLISECONDS, new ActorSystem.AutoHealingSettings(1, 5, interruptions::incrementAndGet, recreations::incrementAndGet))) {
            latch.await();
            actor.askExit();
        }

        Assert.assertEquals(3, calls.get());
        Assert.assertEquals(3, count.get());
        Assert.assertEquals(0, interruptions.get());
        Assert.assertEquals(1, recreations.get());

        SystemUtils.sleep(1200);

        Assert.assertEquals(4, calls.get());
        Assert.assertEquals(3, count.get());
        Assert.assertEquals(0, interruptions.get());
        Assert.assertEquals(1, recreations.get());
    }

}
