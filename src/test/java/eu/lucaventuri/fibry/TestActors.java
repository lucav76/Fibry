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

    static class State {
        private int n = 0;
        private AtomicInteger in = new AtomicInteger();

        public void inc() {
            n++;
            in.incrementAndGet();
        }
    }

    @Test
    public void testThreadsBroken() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        CountDownLatch latch2 = new CountDownLatch(2);

        State s = new State();

        new Thread(() -> {
            for (int i = 0; i < 10000; i++)
                s.inc();

            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 10000; i++)
                s.inc();

            latch2.countDown();
        }).start();

        new Thread(() -> {
            for (int i = 0; i < 10000; i++)
                s.inc();

            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 10000; i++)
                s.inc();

            latch2.countDown();
        }).start();

        latch2.await();
        System.out.println(s.n + " vs " + s.in.get());
        assertEquals(40000, s.in.get());
        assertNotEquals(s.n, s.in.get());
    }

    @Test
    public void testThreadsActors() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        CountDownLatch latch2 = new CountDownLatch(2);

        SinkActor<State> actor = Stereotypes.auto().sink(new State());

        new Thread(() -> {
            for (int i = 0; i < 10000; i++)
                actor.execAsyncState(State::inc);

            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 10000; i++)
                actor.execAsyncState(State::inc);

            latch2.countDown();
        }).start();

        new Thread(() -> {
            for (int i = 0; i < 10000; i++)
                actor.execAsyncState(State::inc);

            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < 10000; i++)
                actor.execAsyncState(State::inc);

            latch2.countDown();
        }).start();

        latch2.await();
        actor.execAndWaitState(s -> {
            System.out.println(s.n + " vs " + s.in.get());
            assertEquals(s.n, s.in.get());
        });
    }

    @Test
    public void testNamedActorForcedDelivery() throws ExecutionException, InterruptedException {
        String actorName = "testActor" + System.currentTimeMillis() + Math.random();

        assertFalse(ActorSystem.isActorAvailable(actorName));
        // Messages queued, waiting for the actor
        ActorSystem.sendMessage(actorName, 1, true);
        assertTrue(ActorSystem.isActorAvailable(actorName));
        ActorSystem.sendMessage(actorName, "b", true);

        CompletableFuture<Object> future = ActorSystem.sendMessageReturn(actorName, "b2", false);
        assertFalse(future.isCompletedExceptionally()); // Can't call get now or it will hang until the actor is created

        // Create the actor, that will process previous message
        ActorSystem.named(actorName).initialState(new AtomicInteger()).newActorWithReturn((message, thisActor) -> thisActor.getState().incrementAndGet());

        assertEquals(4, ActorSystem.sendMessageReturn(actorName, "c", true).get());
        ActorSystem.sendMessage(actorName, 5, true);
        assertEquals(6, ActorSystem.sendMessageReturn(actorName, "c", true).get());
        assertEquals(3, future.get());
    }

    @Test
    public void testNamedActorWithoutForcedDelivery() throws ExecutionException, InterruptedException {
        String actorName = "testActor" + System.currentTimeMillis() + Math.random();

        assertFalse(ActorSystem.isActorAvailable(actorName));
        // Message dropped
        ActorSystem.sendMessage(actorName, 1, false);
        ActorSystem.sendMessage(actorName, "b", false);
        assertFalse(ActorSystem.isActorAvailable(actorName));

        assertTrue(ActorSystem.sendMessageReturn(actorName, "b", false).isCompletedExceptionally());

        // Create teh actor and process only new messages
        ActorSystem.named(actorName).initialState(new AtomicInteger()).newActorWithReturn((message, thisActor) ->
                thisActor.getState().incrementAndGet());
        assertTrue(ActorSystem.isActorAvailable(actorName));

        assertEquals(1, ActorSystem.sendMessageReturn(actorName, "c", false).get());
        ActorSystem.sendMessage(actorName, 2, false);
        assertEquals(3, ActorSystem.sendMessageReturn(actorName, "c", false).get());
    }
}

