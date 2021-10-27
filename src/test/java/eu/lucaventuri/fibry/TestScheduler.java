package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.common.TimeProvider;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestScheduler {
    @Test
    public void testScheduler() {
        long time = System.currentTimeMillis();
        int wait = 25;

        var actor = ActorSystem.anonymous().<String>newActor(message ->
                assertTrue(System.currentTimeMillis() >= time + wait)
        );

        new Scheduler().schedule(actor, "test", wait, TimeUnit.MILLISECONDS);
        new Scheduler().schedule(actor, "test2", wait, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testSchedulerOrder() {
        long time = System.currentTimeMillis();
        int wait = 25;
        int numMessageToTest = 5;
        AtomicInteger num = new AtomicInteger(numMessageToTest);

        var actor = ActorSystem.anonymous().<String>newActor(message -> {
                    assertEquals(message, "msg" + (num.decrementAndGet() + 1));
                    assertTrue(System.currentTimeMillis() >= time + wait);
                }
        );

        new Scheduler().schedule(actor, "msg1", wait + 20, TimeUnit.MILLISECONDS);
        new Scheduler().schedule(actor, "msg2", wait + 15, TimeUnit.MILLISECONDS);
        new Scheduler().schedule(actor, "msg4", wait + 5, TimeUnit.MILLISECONDS);
        new Scheduler().schedule(actor, "msg5", wait, TimeUnit.MILLISECONDS);
        new Scheduler().schedule(actor, "msg3", wait + 10, TimeUnit.MILLISECONDS);

        while (num.get() != 0) {
            SystemUtils.sleep(1);
        }
    }

    @Test
    public void testSchedulerRate() {
        int numMessageToTest = 7;
        AtomicInteger num = new AtomicInteger(numMessageToTest);
        AtomicInteger num2 = new AtomicInteger(1);
        AtomicInteger num3 = new AtomicInteger(numMessageToTest + 3);
        var timeProvider = TimeProvider.flexible();

        var actor = ActorSystem.anonymous().<String>newActor(message -> {
                    assertEquals(message, "msg");
                    num.decrementAndGet();
                    long curTime = timeProvider.get();
                    //System.out.println(message + ": " + curTime);
                    assertEquals(0, curTime % 5);
                }
        );

        var actor2 = ActorSystem.anonymous().<String>newActor(message -> {
                    assertEquals(message, "msg2");
                    long curTime = timeProvider.get();
                    //System.out.println(message + ": " + curTime);
                    assertEquals(11, curTime);
                    num2.decrementAndGet();
                }
        );

        var actor3 = ActorSystem.anonymous().<String>newActor(message -> {
                    assertEquals(message, "msg3");
                    long curTime = timeProvider.get();
                    //System.out.println(message + ": " + curTime);
                    num3.decrementAndGet();
                    timeProvider.sleepUntil(curTime + 7);
                    assertEquals((curTime - 34) % 10, 0);
                }
        );

        Scheduler scheduler = new Scheduler(0, timeProvider);

        scheduler.schedule(actor2, "msg2", 11, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(actor, "msg", 5, 5, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(actor3, "msg3", 34, 3, TimeUnit.MILLISECONDS);

        while (num.get() != 0) {
            SystemUtils.sleep(5);
            timeProvider.inc();
        }

        while (num2.get() != 0) {
            SystemUtils.sleep(5);
            timeProvider.inc();
        }

        while (num3.get() != 0) {
            SystemUtils.sleep(5);
            timeProvider.inc();
        }

        scheduler.askExit();
        scheduler.waitForExit();
    }

    @Test
    public void testSchedulerMAx() {
        long time = System.currentTimeMillis();
        int numMessageToTest = 7;
        AtomicInteger num = new AtomicInteger(numMessageToTest);
        int maxMessages = 3;

        var actor = ActorSystem.anonymous().<String>newActor(message -> {
                    assertEquals(message, "msg");
                    num.decrementAndGet();
                    System.out.println(message + ": " + (System.currentTimeMillis() - time));
                }
        );

        Scheduler scheduler = new Scheduler(0);

        scheduler.scheduleAtFixedRate(actor, "msg", 5, 5, TimeUnit.MILLISECONDS, maxMessages);

        SystemUtils.sleep(100);

        assertEquals(num.get(), 4);
        scheduler.askExit();
        scheduler.waitForExit();
    }
}
