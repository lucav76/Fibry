package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    public void testMax() throws InterruptedException, ExecutionException {
        HealRegistry.INSTANCE.setGracePeriod(10, TimeUnit.MILLISECONDS);
        CountDownLatch latchRecreate = new CountDownLatch(2);
        long start = System.currentTimeMillis();

        Actor<Long, Void, Void> actor = ActorSystem.anonymous().autoHealing(new ActorSystem.AutoHealingSettings(1, 2, () -> Assert.fail("Unexpected Notification by AutoHealing - Thread interrupted"), latchRecreate::countDown)).newActor(SystemUtils::sleepEnsure);

        actor.sendMessage(3_000L);
        actor.sendMessage(3_000L);
        actor.sendMessage(3_000L);

        latchRecreate.await();
        System.out.println("Latch counted down");
        long partial = System.currentTimeMillis();
        actor.sendMessageReturn(10L).get();
        long end = System.currentTimeMillis();
        System.out.println((partial-start) + " - " + (end-partial) + ": " + (end-start));
        Assert.assertTrue(end - start < 9000);
        Assert.assertTrue(end - partial >=-  3000);
    }
}
