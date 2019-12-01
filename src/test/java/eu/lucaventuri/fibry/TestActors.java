package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static eu.lucaventuri.common.SystemUtils.*;
import static org.junit.Assert.*;


class TestHandler {
    final AtomicInteger numString = new AtomicInteger();
    final AtomicInteger numInteger = new AtomicInteger();
    final AtomicInteger numNumber = new AtomicInteger();

    public void onString(String str) {
        numString.incrementAndGet();
    }

    public void onInt(Integer i) {
        numInteger.incrementAndGet();
    }

    public void onNumber(Number n) {
        numNumber.incrementAndGet();
    }

    public void check(int expectedString, int expectedInt, int expectedNumber) {
        Assert.assertEquals(expectedString, numString.get());
        Assert.assertEquals(expectedInt, numInteger.get());
        Assert.assertEquals(expectedNumber, numNumber.get());
    }
}

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
            sleep(1);
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
            sleep(1);
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
        CountDownLatch latch = new CountDownLatch(1);

        Actor<Object, Void, Void> actor = ActorSystem.anonymous(2).newActor(message -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(message);
        });

        actor.sendMessage("1");
        actor.sendMessage("2");

        try {
            actor.sendMessage("3");
            fail();
        } catch (IllegalStateException e) {
            /** Expected */
        } finally {
            latch.countDown();
        }

        actor.askExitAndWait();
    }

    @Test
    public void testFinalizersWithProtection() throws InterruptedException, ExecutionException {
        final AtomicInteger num = new AtomicInteger();
        String actorName = "testFinalizersWithProtection";
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latchStart = new CountDownLatch(1);

        Actor<Integer, Void, AtomicInteger> actor = ActorSystem.anonymous().initialState(num, null, n -> n.set(-1)).newActor((message, thisActor) -> {
            thisActor.getState().addAndGet(message);
        });

        assertEquals(false, ActorSystem.isActorAvailable(actorName));

        Actor<Integer, Void, AtomicInteger> actor2 = ActorSystem.named(actorName, true).initialState(num, null, n -> n.set(-1)).newActor((message, thisActor) -> {
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

        Actor<Integer, Void, AtomicInteger> actor = ActorSystem.anonymous().initialState(num, null, n -> n.set(-1)).newActor((message, thisActor) -> {
            thisActor.getState().addAndGet(message);
        });

        assertEquals(false, ActorSystem.isActorAvailable(actorName));

        Actor<Integer, Void, AtomicInteger> actor2 = ActorSystem.named(actorName, false).initialState(num, null, n -> n.set(-1)).newActor((message, thisActor) -> {
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

    @Test
    public void testInizializer() throws ExecutionException, InterruptedException {
        AtomicInteger i = new AtomicInteger();
        var actor = ActorSystem.anonymous().initialState(null, s -> i.set(100), null).newActor(m -> {
        });

        actor.sendMessageReturn(1).get();
        assertEquals(100, i.get());
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
    public void testThreadsActors2() throws InterruptedException {
        int numThreads = 100;
        int num = 10_000;
        AtomicInteger numSent = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(numThreads);
        SinkActor<Integer> actor = Stereotypes.def().sink(0);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                for (int j = 0; j < num; j++) {
                    actor.execAsyncStateful(s -> s.setState(s.getState() + 1));
                    numSent.incrementAndGet();
                }

                latch.countDown();
            }).start();
        }

        while (latch.getCount() > 0) {
            latch.await(250, TimeUnit.MILLISECONDS);
            System.out.println(latch.getCount() + " - " + actor.getState() + " - " + numSent.get());
        }
        actor.sendPoisonPill();
        actor.waitForExit();

        assertEquals(numThreads * num, actor.getState().intValue());
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

    @Test
    public void testCollectSingle() {
        Actor<Integer, Double, Void> actor = ActorSystem.anonymous().newActorWithReturn((n, thisActor) -> {
            if (n < 0)
                thisActor.askExit();

            return (double) ((int) 100 / (Math.abs(n)));
        });

        assertEquals(Double.valueOf(50.0), actor.sendMessageReturnWait(2, -1.0));
        Integer values1[] = {10, 20, 25, 50, 100};
        Integer values2[] = {10, 20, 0, 50, 100};
        Integer values3[] = {0, 20, 0, 50, 0};
        Integer values4[] = {0, 20, 25, 25, 50, 100};
        Integer values5[] = {0, 20, 25, -25, 50, 100};

        assertEquals(22.0, sum(5, actor.sendAndCollectSilent(-1.0, values1)), 0.000001);
        assertEquals(18.0, sum(5, actor.sendAndCollectSilent(0.0, values2)), 0.000001);
        assertEquals(7.0, sum(5, actor.sendAndCollectSilent(0.0, values3)), 0.000001);
        assertEquals(16, sum(6, actor.sendAndCollectSilent(0.0, values4)), 0.000001);
        assertEquals(13, sum(6, actor.sendAndCollectSilent(0.0, values5)), 0.000001);
    }

    private double sum(int size, List<Double> list) {
        assertEquals(size, list.size());

        return list.stream().mapToDouble(v -> v).sum();
    }

    @Test
    public void testExtractEventHandlers() {
        class C {
            public void onA(String str) {
            }

            public void onInt(Integer i) {
            }

            public void onFloat(Double d) {
            }

            public void onNumber(Number n) {
            }

            public void onFloat(Float d) {
            }

            private void onB(String str) {
            }

            public void test(String str) {
            }

            public void onMap(Map a) {
            }

            public void onMap(HashMap a) {
            }

            public void onError() {
            }

            private void test2(String str) {
            }

            private void onError2() {
            }
        }

        LinkedHashMap<Class, Method> map = ActorUtils.extractEventHandlers(C.class);

        System.out.println(map.keySet());
        Assert.assertEquals(7, map.size());
        Class[] ar = map.keySet().toArray(new Class[0]);
        Assert.assertTrue(ar[4] == String.class || ar[4] == Number.class || ar[4] == Map.class);
        Assert.assertTrue(ar[5] == String.class || ar[5] == Number.class || ar[5] == Map.class);
        Assert.assertTrue(ar[6] == String.class || ar[6] == Number.class || ar[6] == Map.class);

        Assert.assertTrue(map.containsKey(String.class));
        Assert.assertTrue(map.containsKey(Number.class));
        Assert.assertTrue(map.containsKey(Map.class));
        Assert.assertTrue(map.containsKey(HashMap.class));
        Assert.assertTrue(map.containsKey(Integer.class));
        Assert.assertTrue(map.containsKey(Float.class));
        Assert.assertTrue(map.containsKey(Double.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractEventHandlersException() {
        class C {
            public void onA(String str) {
            }

            public void onB(String str) {
            }
        }

        ActorUtils.extractEventHandlers(C.class);
    }

    @Test
    public void testEventHandleConsumer() {
        TestHandler th = new TestHandler();
        Consumer<Object> consumer = ActorUtils.extractEventHandlerLogic(th);

        th.check(0, 0, 0);
        consumer.accept("Test");
        th.check(1, 0, 0);
        consumer.accept("Test");
        th.check(2, 0, 0);
        consumer.accept(3);
        th.check(2, 1, 0);
        consumer.accept(4);
        th.check(2, 2, 0);
        consumer.accept(4.0);
        th.check(2, 2, 1);
        consumer.accept(4L);
        th.check(2, 2, 2);
    }

    @Test
    public void testEventHandleActor() {
        TestHandler th = new TestHandler();
        Actor<Object, Void, Void> actor = ActorSystem.anonymous().newActorMultiMessages(th);

        th.check(0, 0, 0);
        actor.sendMessageReturnWait("Test", null);
        th.check(1, 0, 0);
        actor.sendMessageReturnWait("Test", null);
        th.check(2, 0, 0);
        actor.sendMessageReturnWait(3, null);
        th.check(2, 1, 0);
        actor.sendMessageReturnWait(4, null);
        th.check(2, 2, 0);
        actor.sendMessageReturnWait(4.0, null);
        th.check(2, 2, 1);
        actor.sendMessageReturnWait(4L, null);
        th.check(2, 2, 2);
    }

    @Test
    public void testEventHandleActorWithReturn() throws ExecutionException, InterruptedException {
        class TestHandlerReturn {
            public String onString(String str) {
                return "String: " + str;
            }

            public String onNumber(Integer n) {
                return "Int: " + n;
            }

            public String onNumber(Long n) {
                return "Long: " + n;
            }
        }
        TestHandlerReturn th = new TestHandlerReturn();
        Actor<Object, String, Void> actor = ActorSystem.anonymous().newActorMultiMessagesWithReturn(th);

        assertEquals("String: test", actor.sendMessageReturn("test").get());
        assertEquals("Int: 4", actor.sendMessageReturn(4).get());
        assertEquals("Long: 5", actor.sendMessageReturn(5L).get());
    }

    @Test
    public void testSynchronousActor() throws ExecutionException, InterruptedException {
        AtomicInteger num = new AtomicInteger();
        Consumer<String> logic = s -> {
            sleep(10);
            num.incrementAndGet();
        };
        var act1 = ActorSystem.anonymous().newActor(logic);
        var act2 = ActorSystem.anonymous().newSynchronousActor(logic);
        var act3 = ActorSystem.anonymous().<String, String>newSynchronousActorWithReturn(s -> {
            sleep(10);
            num.incrementAndGet();

            return s + num.get();
        });

        act1.sendMessage("A");
        assertEquals(num.get(), 0);

        act1.sendPoisonPill();
        act1.waitForExit();
        assertEquals(num.get(), 1);

        act2.sendMessage("A");
        assertEquals(num.get(), 2);
        act2.sendMessage("B");
        assertEquals(num.get(), 3);
        act2.sendMessage("C");
        assertEquals(num.get(), 4);
        var s = act3.sendMessageReturn("E").get();
        assertEquals("E5", s);
    }

    @Test
    public void testSynchronousActor2() {
        Thread curThread = Thread.currentThread();
        AtomicBoolean error = new AtomicBoolean();
        try (
                var act1 = ActorSystem.anonymous().newActor(x -> {
                    if (curThread.equals(Thread.currentThread()))
                        error.set(true);
                    assertNotEquals(curThread, Thread.currentThread());
                });
                var act2 = ActorSystem.anonymous().newSynchronousActor(x -> {
                    if (!curThread.equals(Thread.currentThread()))
                        error.set(true);

                    assertEquals(curThread, Thread.currentThread());
                });
                var act3 = ActorSystem.anonymous().newSynchronousActorWithReturn(x -> {
                    if (!curThread.equals(Thread.currentThread()))
                        error.set(true);
                    assertNotEquals(curThread, Thread.currentThread());

                    return "OK";
                })) {
            act1.sendMessageReturnWait("A", null);
            act2.sendMessageReturnWait("A", null);
            act3.sendMessageReturnWait("A", null);
        }

        assertFalse(error.get());
    }
}

