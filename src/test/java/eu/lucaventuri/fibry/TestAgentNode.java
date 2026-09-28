package eu.lucaventuri.fibry;

import eu.lucaventuri.fibry.ai.AgentNode;
import eu.lucaventuri.fibry.ai.AgentState;
import eu.lucaventuri.fibry.ai.AiAgent;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestAgentNode {
    private enum Step { FIRST, SECOND }
    public record Info(int first, int second) {}

    @Test
    public void serialStopsAfterOverride() {
        var state = new AgentState<Step, Info>(new Info(0, 0));
        var calls = new AtomicInteger();
        List<AgentNode<Step, Info>> logics = List.of(
                current -> { calls.incrementAndGet(); return current.setAttribute("first", 1); },
                current -> { calls.incrementAndGet(); current.setStateOverride(Step.FIRST); return current; },
                current -> { calls.incrementAndGet(); return current.setAttribute("second", 2); });

        Assert.assertSame(state, AgentNode.combineSerial(logics, true).apply(state));
        Assert.assertEquals(2, calls.get());
        Assert.assertEquals(new Info(1, 0), state.data());
        Assert.assertEquals(List.of(Step.FIRST), state.getStateOverride());
    }

    @Test
    public void serialContinuesAfterOverrideAndPassesReturnedState() {
        var state = new AgentState<Step, Info>(new Info(0, 0));
        var replacement = new AgentState<Step, Info>(new Info(3, 0));
        List<AgentNode<Step, Info>> logics = List.of(
                current -> { current.setStateOverride(Step.FIRST); return replacement; },
                current -> { Assert.assertSame(replacement, current); return current.setAttribute("second", 2); });

        Assert.assertSame(replacement, AgentNode.combineSerial(logics, false).apply(state));
        Assert.assertEquals(new Info(3, 2), replacement.data());
    }

    @Test
    public void addStatesSerialBlocksAfterOverride() {
        Assert.assertEquals(new Info(1, 0), runSerialAgent(true));
    }

    @Test
    public void addStatesSerialContinuesAfterOverride() {
        Assert.assertEquals(new Info(1, 2), runSerialAgent(false));
    }

    private Info runSerialAgent(boolean blockAfterOverride) {
        List<AgentNode<Step, Info>> logics = List.of(
                state -> { state.setStateOverride(Step.SECOND); return state.setAttribute("first", 1); },
                state -> state.setAttribute("second", 2));
        var agent = AiAgent.<Step, Info>builder(false)
                .addStatesSerial(Step.FIRST, List.of(Step.SECOND), 1, logics, null, blockAfterOverride)
                .build(Step.FIRST, Step.SECOND, false);

        return agent.process(new Info(0, 0), 5, TimeUnit.SECONDS);
    }

    @Test
    public void parallelUsesProvidedExecutorAndWaitsForAllLogics() throws Exception {
        var state = new AgentState<Step, Info>(new Info(0, 0));
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var submissions = new AtomicInteger();
        var firstOverrideWritten = new CountDownLatch(1);
        List<AgentNode<Step, Info>> logics = List.of(
                current -> {
                    current.setStateOverride(Step.FIRST);
                    firstOverrideWritten.countDown();
                    started.countDown();
                    await(release);
                    return current.setAttribute("first", 1);
                },
                current -> {
                    await(firstOverrideWritten);
                    current.setStateOverride(Step.SECOND);
                    started.countDown();
                    await(release);
                    return current.setAttribute("second", 2);
                });

        try (var pool = Executors.newFixedThreadPool(2)) {
            var combined = AgentNode.combineParallel(logics, task -> {
                submissions.incrementAndGet();
                pool.execute(task);
            });
            var result = CompletableFuture.supplyAsync(() -> combined.apply(state));

            try {
                Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
                Assert.assertFalse(result.isDone());
            } finally {
                release.countDown();
            }

            Assert.assertSame(state, result.get(5, TimeUnit.SECONDS));
        }

        Assert.assertEquals(2, submissions.get());
        Assert.assertEquals(new Info(1, 2), state.data());
        Assert.assertEquals(List.of(Step.SECOND), state.getStateOverride());
    }

    @Test
    public void parallelDefaultsToVirtualThreads() throws Exception {
        var state = new AgentState<Step, Info>(new Info(0, 0));
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var virtualThreads = new AtomicBoolean(true);
        List<AgentNode<Step, Info>> logics = List.of(
                current -> { virtualThreads.set(virtualThreads.get() && Thread.currentThread().isVirtual()); started.countDown(); await(release); return current.setAttribute("first", 1); },
                current -> { virtualThreads.set(virtualThreads.get() && Thread.currentThread().isVirtual()); started.countDown(); await(release); return current.setAttribute("second", 2); });
        var result = CompletableFuture.supplyAsync(() -> AgentNode.combineParallel(logics, null).apply(state));

        try {
            Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
            Assert.assertFalse(result.isDone());
        } finally {
            release.countDown();
        }

        Assert.assertSame(state, result.get(5, TimeUnit.SECONDS));
        Assert.assertTrue(virtualThreads.get());
        Assert.assertEquals(new Info(1, 2), state.data());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Timed out waiting for test latch");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
