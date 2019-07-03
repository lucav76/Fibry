package eu.lucaventuri.collections;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.jmacs.Actor;
import eu.lucaventuri.jmacs.MiniActorSystem;
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
        Actor<String, Void, StringBuilder> actor = MiniActorSystem.anonymous().initialState(new StringBuilder()).newActor(lazy, MiniActorSystem.Strategy.AUTO);

        actor.execAndWait(sb -> sb.append("A"));
        actor.execAndWait(sb -> sb.append("B"));
        actor.execAndWait(sb -> sb.append("C"));

        actor.execAndWait(sb -> {
            System.out.println(sb);

            assertEquals("ABC", sb.toString());
        });
    }

    @Test
    public void testSyncExec2() throws InterruptedException {
        int numExpectedCalls = 1_000;
        AtomicInteger callsExecuted = new AtomicInteger();
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = MiniActorSystem.anonymous().initialState(new State()).newActor(lazy, MiniActorSystem.Strategy.AUTO);

        for (int i = 0; i < numExpectedCalls; i++)
            actor.execAndWait(s -> {
                s.numCalls++;
                callsExecuted.incrementAndGet();
            });

        while (callsExecuted.get() < numExpectedCalls) {
            SystemUtils.sleep(1);
        }

        actor.execAndWait(s -> {
            System.out.println(s);

            assertEquals(numExpectedCalls, s.numCalls);
        });
    }

    @Test
    public void testAsyncExec() throws InterruptedException {
        int numExpectedCalls = 100_000;
        AtomicInteger callsExecuted = new AtomicInteger();
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = MiniActorSystem.anonymous().initialState(new State()).newActor(lazy, MiniActorSystem.Strategy.AUTO);

        for (int i = 0; i < numExpectedCalls; i++)
            actor.execAsync(s -> {
                s.numCalls++;
                callsExecuted.incrementAndGet();
            });

        while (callsExecuted.get() < numExpectedCalls) {
            SystemUtils.sleep(1);
        }

        actor.execAndWait(s -> {
            System.out.println(s);

            assertEquals(numExpectedCalls, s.numCalls);
        });
    }

    @Test
    public void testExecFuture() throws InterruptedException, ExecutionException {
        class State {
            int numCalls;
        }

        Actor<String, Void, State> actor = MiniActorSystem.anonymous().initialState(new State()).newActor(lazy, MiniActorSystem.Strategy.AUTO);

        actor.execFuture(s -> {
            s.numCalls++;
        }).get();
        actor.execFuture(s -> {
            s.numCalls++;
        }).get();

        actor.execAndWait(s -> {
            System.out.println(s);

            assertEquals(2, s.numCalls);
        });
    }

    @Test
    public void testSendMessage() throws InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(3);
        class State {
            int numCalls;
        }
        State state = new State();

        Actor<Integer, Void, State> actor = MiniActorSystem.anonymous().initialState(state).newActor(n -> {
            state.numCalls += n;
            latch.countDown();
        }, MiniActorSystem.Strategy.AUTO);

        actor.sendMessage(1);
        actor.sendMessage(2);
        actor.sendMessage(3);
        actor.sendMessage(4);

        latch.await();

        assertEquals(10, state.numCalls);
    }

    @Test
    public void testSendMessageReturn() throws InterruptedException, ExecutionException {
        Actor<Integer, Integer, Void> actor = MiniActorSystem.anonymous().newActorWithReturn(n -> n.intValue()*n.intValue(), MiniActorSystem.Strategy.AUTO);

        assertEquals(1, actor.sendMessageReturn(1).get().intValue());
        assertEquals(4, actor.sendMessageReturn(2).get().intValue());
        assertEquals(9, actor.sendMessageReturn(3).get().intValue());
        assertEquals(16, actor.sendMessageReturn(4).get().intValue());
    }
}
