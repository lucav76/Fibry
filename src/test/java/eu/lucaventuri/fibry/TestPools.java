package eu.lucaventuri.fibry;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.common.SystemUtils;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestPools {
    @Test
    // FIXME: This test should be more stable
    public void testFixedSize() throws ExecutionException, InterruptedException {
        Set<Thread> actors = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(3);
        CountDownLatch latch2 = new CountDownLatch(1);
        PoolActorLeader<String, Void, String> leader = (PoolActorLeader<String, Void, String>) ActorSystem.anonymous().<String>poolParams(PoolParameters.fixedSize(3), null).<String>newPool(msg -> {
            actors.add(Thread.currentThread());
            System.out.println(Thread.currentThread() + " - " + latch.getCount() + " - " + actors.size());
            latch.countDown();
            try {
                latch.await();
                latch2.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(Thread.currentThread() + " leaving ");
        });

        assertEquals(3, leader.getGroupExit().size());
        assertEquals(0, actors.size());

        leader.sendMessageReturn("A");
        leader.sendMessageReturn("B");
        leader.sendMessageReturn("C");

        latch.await();

        assertEquals(3, leader.getGroupExit().size());
        assertEquals(3, actors.size());

        latch2.countDown();
    }

    @Test
    public void testScaling() throws ExecutionException, InterruptedException {
        int maxActors = 10;
        Set<Thread> actors = new HashSet<>();
        PoolActorLeader<String, Void, String> leader = (PoolActorLeader<String, Void, String>) ActorSystem.anonymous().<String>poolParams(PoolParameters.scaling(3, maxActors, 10, 2, 1, 5), null).<String>newPool(msg -> {
            actors.add(Thread.currentThread());
            SystemUtils.sleep(30);
        });

        assertEquals(3, leader.getGroupExit().size());
        assertEquals(0, actors.size());

        CompletableFuture[] msgFirstRound = new CompletableFuture[maxActors];
        CompletableFuture[] msgSecondRound = new CompletableFuture[maxActors * 2];

        for (int i = 0; i < maxActors; i++)
            msgFirstRound[i] = leader.sendMessageReturn("A");

        for (int i = 0; i < maxActors * 2; i++)
            msgSecondRound[i] = leader.sendMessageReturn("A");

        CompletableFuture.allOf(msgFirstRound).get();

        assertEquals(maxActors, leader.getGroupExit().size());
        assertTrue(leader.getQueueLength() > 0);
        assertEquals(maxActors, leader.getGroupExit().size());

        int n = 0;

        // Wait for the queue to go down
        while (leader.getQueueLength() > 0) {
            SystemUtils.sleep(1);
            n++;

            if ((n % 100) == 0) {
                System.out.println("Leader queue size: " + leader.getQueueLength() + " - PoolSize: " + leader.getGroupExit().size());
            }
        }

        assertEquals(leader.getQueueLength(), 0);

        // Give time to the autoscaling to resize down the pool
        SystemUtils.sleep(50);

        // Resized down
        assertEquals(3, leader.getGroupExit().size());
    }

    @Test
    public void testAskExit() {
        fixedSink(10).askExitAndWait();
    }

    @Test
    public void testPoisonPill() {
        PoolActorLeader<Object, Void, Object> leader = fixedSink(10);

        leader.sendPoisonPill();
        leader.waitForExit();
    }

    private PoolActorLeader<Object, Void, Object> fixedSink(int numActors) {
        return ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(numActors), null).newPool(data -> {
        });
    }

    @Test
    public void testFinalizer() {
        AtomicInteger numLeaderFinished = new AtomicInteger();
        AtomicInteger numWorkersFinished = new AtomicInteger();
        AtomicInteger numMessages = new AtomicInteger();

        PoolActorLeader<Object, Void, Object> leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(3), null, null, s -> numWorkersFinished.incrementAndGet()).newPool(t -> {
            SystemUtils.sleep(25);
            numMessages.incrementAndGet();
        }, s -> {
            System.out.println("Leader finishind!");
            SystemUtils.sleep(25);
            numLeaderFinished.incrementAndGet();
        });

        leader.sendMessage("a");
        leader.sendMessage("a");
        leader.sendMessage("a");
        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(3, numMessages.get());
        assertEquals(3, numWorkersFinished.get());
        assertEquals(1, numLeaderFinished.get());
    }

    @Test
    public void testFinalizer2() {
        AtomicInteger numLeaderFinished = new AtomicInteger();
        AtomicInteger numWorkersFinished = new AtomicInteger();
        AtomicInteger numMessages = new AtomicInteger();

        PoolActorLeader<Object, Void, Object> leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(3), null, null, s -> {
            SystemUtils.sleep(25);
            numWorkersFinished.incrementAndGet();
        }).newPool(t -> {
            SystemUtils.sleep(25);
            numMessages.incrementAndGet();
        }, s -> numLeaderFinished.incrementAndGet());

        leader.sendMessage("a");
        leader.sendMessage("a");
        leader.sendMessage("a");
        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(3, numMessages.get());
        assertEquals(3, numWorkersFinished.get());
        assertEquals(1, numLeaderFinished.get());
    }

    @Test
    public void testExitable() {
        AtomicInteger numLeaderFinished = new AtomicInteger();
        AtomicInteger numWorkersFinished = new AtomicInteger();
        AtomicInteger numMessages = new AtomicInteger();

        try (PoolActorLeader<Object, Void, Object> leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(3), null, null, s -> numWorkersFinished.incrementAndGet()).newPool(t -> {
            SystemUtils.sleep(25);
            numMessages.incrementAndGet();
        }, s -> {
            System.out.println("Leader finishind!");
            SystemUtils.sleep(25);
            numLeaderFinished.incrementAndGet();
        })) {
            leader.sendMessage("a");
            leader.sendMessage("a");
            leader.sendMessage("a");
        }

        assertEquals(3, numMessages.get());
        assertEquals(3, numWorkersFinished.get());
        assertEquals(1, numLeaderFinished.get());
    }

    @Test
    public void testExitable2() {
        AtomicInteger numLeaderFinished = new AtomicInteger();
        AtomicInteger numWorkersFinished = new AtomicInteger();
        AtomicInteger numMessages = new AtomicInteger();

        try (PoolActorLeader<Object, Void, Object> leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(3), null, null, s -> {
            SystemUtils.sleep(25);
            numWorkersFinished.incrementAndGet();
        }).newPool(t -> {
            SystemUtils.sleep(25);
            numMessages.incrementAndGet();
        }, s -> numLeaderFinished.incrementAndGet())) {
            leader.sendMessage("a");
            leader.sendMessage("a");
            leader.sendMessage("a");
        }

        assertEquals(3, numMessages.get());
        assertEquals(3, numWorkersFinished.get());
        assertEquals(1, numLeaderFinished.get());
    }


    @Test
    public void testState() {
        Set<Double> numbersSeen = ConcurrentHashSet.build();
        AtomicInteger messages = new AtomicInteger();

        PoolActorLeader<Object, Void, Double> leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(3), Math::random).newPool((m, actor) ->
        {
            numbersSeen.add(actor.getState());
            messages.incrementAndGet();
        }, null);

        for (int i = 0; i < 100; i++)
            leader.sendMessage("abc");

        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(3, numbersSeen.size());
        assertEquals(100, messages.get());
    }
}
