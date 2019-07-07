package eu.lucaventuri.collections;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

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
        Actor<Integer, Integer, Void> actor = ActorSystem.anonymous().newActorWithReturn(n -> n*n);

        assertEquals(1, actor.sendMessageReturn(1).get().intValue());
        assertEquals(4, actor.sendMessageReturn(2).get().intValue());
        assertEquals(4, actor.apply(2).intValue());
        assertEquals(9, actor.sendMessageReturn(3).get().intValue());
        assertEquals(16, actor.sendMessageReturn(4).get().intValue());
    }
}
