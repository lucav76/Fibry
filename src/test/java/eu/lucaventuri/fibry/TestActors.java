package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TestActors {
    private static final Consumer<String> lazy = str -> {
    };

    @Test
    public void testSyncExec() {
        Actor<String, Void, StringBuilder> actor = ActorSystem.anonymous().initialState(new StringBuilder()).newActor(lazy);

        actor.execAndWait(act -> act.getState().append("A"));
        actor.execAndWait(act -> act.getState().append("B"));
        actor.execAndWait(act -> act.getState().append("C"));

        actor.execAndWait(act -> {
            System.out.println(act.getState());

            assertEquals("ABC", act.getState().toString());
        });
    }

    @Test
    public void testSyncExec2() throws InterruptedException {
        int numExpectedCalls = 1_000;
        AtomicInteger callsExecuted = new AtomicInteger();
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = ActorSystem.anonymous().initialState(new State()).newActor(lazy);

        for (int i = 0; i < numExpectedCalls; i++)
            actor.execAndWait(act -> {
                act.getState().numCalls++;
                callsExecuted.incrementAndGet();
            });

        while (callsExecuted.get() < numExpectedCalls) {
            SystemUtils.sleep(1);
        }

        actor.execAndWait(act -> {
            System.out.println(act.getState());

            assertEquals(numExpectedCalls, act.getState().numCalls);
        });
    }

    @Test
    public void testAsyncExec() throws InterruptedException {
        int numExpectedCalls = 100_000;
        AtomicInteger callsExecuted = new AtomicInteger();
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = ActorSystem.anonymous().initialState(new State()).newActor(lazy);

        for (int i = 0; i < numExpectedCalls; i++)
            actor.execAsync(act -> {
                act.getState().numCalls++;
                callsExecuted.incrementAndGet();
            });

        while (callsExecuted.get() < numExpectedCalls) {
            SystemUtils.sleep(1);
        }

        actor.execAndWait(act -> {
            System.out.println(act.getState());

            assertEquals(numExpectedCalls, act.getState().numCalls);
        });
    }

    @Test
    public void testExecFuture() throws InterruptedException, ExecutionException {
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = ActorSystem.anonymous().initialState(new State()).newActor(lazy);

        actor.execFuture(act -> {
            act.getState().numCalls++;
        }).get();
        actor.execFuture(act -> {
            act.getState().numCalls++;
        }).get();

        actor.execAndWait(act -> {
            System.out.println(act.getState());

            assertEquals(2, act.getState().numCalls);
        });
    }

    @Test
    public void testSendMessage() throws InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(4);
        class State {
            int numCalls;
        }
        State state = new State();

        Actor<Integer, Void, State> actor = ActorSystem.anonymous().initialState(state).newActor(n -> {
            state.numCalls += n;
            latch.countDown();
        });

        actor.sendMessage(1);
        actor.sendMessage(2);
        actor.sendMessage(3);
        actor.sendMessage(4);

        latch.await();

        assertEquals(10, state.numCalls);
    }

    @Test
    public void testSendMessageReturn() throws InterruptedException, ExecutionException {
        Actor<Integer, Integer, Void> actor = ActorSystem.anonymous().newActorWithReturn(n -> n * n);

        assertEquals(1, actor.sendMessageReturn(1).get().intValue());
        assertEquals(4, actor.sendMessageReturn(2).get().intValue());
        assertEquals(4, actor.apply(2).intValue());
        assertEquals(9, actor.sendMessageReturn(3).get().intValue());
        assertEquals(16, actor.sendMessageReturn(4).get().intValue());
    }

    @Test
    public void testCapacity() {
        Actor<Object, Void, Void> actor = ActorSystem.anonymous(2).newActor(message -> {
            SystemUtils.sleep(50);
            System.out.println(message);
        });

        actor.sendMessage("1");
        actor.sendMessage("2");

        try {
            actor.sendMessage("3");
            fail();
        } catch (IllegalStateException e) {
            /** Expected */
        }

        actor.askExitAndWait();
    }

    @Test
    public void testFinalizersWithProtection() throws InterruptedException, ExecutionException {
        final AtomicInteger num = new AtomicInteger();
        String actorName = "testFinalizersWithProtection";
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latchStart = new CountDownLatch(1);

        Actor<Integer, Void, AtomicInteger> actor = ActorSystem.anonymous().initialState(num, n -> n.set(-1)).newActor((message, thisActor) -> {
            thisActor.getState().addAndGet(message);
        });

        assertEquals(false, ActorSystem.isActorAvailable(actorName));

        Actor<Integer, Void, AtomicInteger> actor2 = ActorSystem.named(actorName, true).initialState(num, n -> n.set(-1)).newActor((message, thisActor) -> {
            latchStart.countDown();
            thisActor.getState().addAndGet(message);
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        assertTrue(ActorSystem.isActorAvailable(actorName));
        assertEquals(0, num.get());
        actor.sendMessageReturn(3).get();
        assertEquals(3, num.get());
        actor.askExitAndWait();
        assertEquals(-1, num.get());

        System.out.println("2");

        CompletableFuture<Void> completable = actor2.sendMessageReturn(3);
        ActorSystem.sendMessage(actorName, 0, true);
        ActorSystem.sendMessage(actorName, 0, true);
        latchStart.await();
        assertEquals(2, ActorSystem.getActorQueueSize(actorName));
        latch.countDown();
        completable.get();
        assertEquals(2, num.get());

        actor2.askExitAndWait();
        assertEquals(0, ActorSystem.getActorQueueSize(actorName));
        assertEquals(-1, num.get());

        // The actor is dead. Let's check the queu is not growing
        ActorSystem.sendMessage(actorName, 0, true);
        ActorSystem.sendMessage(actorName, 0, true);
        assertEquals(0, ActorSystem.getActorQueueSize(actorName));

        // We cannot delete the name to avoid that messages sent to it creates an OOM
        assertTrue(ActorSystem.isActorAvailable(actorName));
    }

    @Test
    public void testFinalizersWithoutProtection() throws InterruptedException, ExecutionException {
        final AtomicInteger num = new AtomicInteger();
        String actorName = "testFinalizersWithoutProtection";
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latchStart = new CountDownLatch(1);

        Actor<Integer, Void, AtomicInteger> actor = ActorSystem.anonymous().initialState(num, n -> n.set(-1)).newActor((message, thisActor) -> {
            thisActor.getState().addAndGet(message);
        });

        assertEquals(false, ActorSystem.isActorAvailable(actorName));

        Actor<Integer, Void, AtomicInteger> actor2 = ActorSystem.named(actorName, false).initialState(num, n -> n.set(-1)).newActor((message, thisActor) -> {
            latchStart.countDown();
            thisActor.getState().addAndGet(message);
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        assertTrue(ActorSystem.isActorAvailable(actorName));
        assertEquals(0, num.get());
        actor.sendMessageReturn(3).get();
        assertEquals(3, num.get());
        actor.askExitAndWait();
        assertEquals(-1, num.get());

        System.out.println("2");

        CompletableFuture<Void> completable = actor2.sendMessageReturn(3);
        ActorSystem.sendMessage(actorName, 0, true);
        ActorSystem.sendMessage(actorName, 0, true);
        latchStart.await();
        assertEquals(2, ActorSystem.getActorQueueSize(actorName));
        latch.countDown();
        completable.get();
        assertEquals(2, num.get());

        actor2.askExitAndWait();
        assertEquals(-1, ActorSystem.getActorQueueSize(actorName));
        assertEquals(-1, num.get());
        assertFalse(ActorSystem.isActorAvailable(actorName));

        // The actor is dead. Let's check the queu is not growing
        ActorSystem.sendMessage(actorName, 0, true);
        ActorSystem.sendMessage(actorName, 0, true);
        assertEquals(2, ActorSystem.getActorQueueSize(actorName));

        // We cannot delete the name to avoid that messages sent to it creates an OOM
        assertTrue(ActorSystem.isActorAvailable(actorName));
    }
}
