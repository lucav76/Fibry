package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.RunnableEx;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.*;
import eu.lucaventuri.fibry.fsm.FsmContext;
import eu.lucaventuri.fibry.fsm.FsmTemplateActor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** Class that implements the logic to run the full AI agent.
 * It is an actor returning a CompletableFuture because every message is processed by a new virtual thread, so more tasks con go in parallel
 * */
public class AiAgent<S extends Enum, I extends Record> extends CustomActorWithResult<AiAgent.AgentExecutionRequest<S, I>, CompletableFuture<AiAgent.AgentResult<I>>, Void> {
    private final FsmTemplateActor<S, S, AgentState<S, I>, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>, AgentState<S, I>> fsm;
    private final S initialState;
    private final S finalState;
    private final Map<S, List<S>> defaultStates;
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final boolean parallelStatesProcessing;
    private final boolean skipLastStates;

    @Override
    protected CompletableFuture<AiAgent.AgentResult<I>> onMessage(AiAgent.AgentExecutionRequest<S, I> message) {
        return CompletableFuture.supplyAsync(() -> executeInCurrentThread(message.input, message.stateListener), executor);
    }

    public record AgentResult<I>(int statesProcessed, I result) {}
    public record AgentExecutionRequest<S extends Enum, I>(I input, BiConsumer<S, I> stateListener) {}

    record States<S>(S prevState, S curState) {
    }

    public AiAgent(FsmTemplateActor<S, S, AgentState<S, I>, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>, AgentState<S, I>> fsm, S initialState, S finalState, Map<S, List<S>> defaultStates, String actorName, int capacity, boolean parallelStatesProcessing, boolean skipLastStates) {
        super(ActorSystem.getOrCreateActorQueue(actorName, capacity), null, null, Integer.MAX_VALUE);
        this.fsm = fsm;
        this.initialState = initialState;
        this.finalState = finalState;
        this.defaultStates = defaultStates;
        this.parallelStatesProcessing = parallelStatesProcessing;
        this.skipLastStates = skipLastStates;

        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    private AgentResult<I> executeInCurrentThread(I initialContext, BiConsumer<S, I> stateListener) {
        AtomicInteger statesProcessed = new AtomicInteger();
        AtomicInteger pendingStates = new AtomicInteger();
        AtomicReference<S> lastStateProcessing = new AtomicReference<>();

        try {
            AgentState<S, I> agentState = new AgentState<>(initialContext);
            Queue<States<S>> queuesStates = new ConcurrentLinkedDeque<>();
            Set<S> lastStates = ConcurrentHashSet.build();

            queuesStates.add(new States<>(null, initialState));

            while (!queuesStates.isEmpty() || pendingStates.get() > 0) {
                statesProcessed.incrementAndGet();

                var states = queuesStates.poll();

                if (states == null) {
                    SystemUtils.sleep(1);
                    continue;
                }

                if (skipLastStates && lastStates.contains(states.curState)) {
                    continue;
                }

                var actor = fsm.newFsmActor(states.curState);

                RunnableEx<Exception> run = () -> {
                    try {
                        lastStateProcessing.set(states.curState);
                        var result = actor.getActor().sendMessageReturn(new FsmContext<>(states.prevState, states.curState, null, agentState)).get();

                        agentState.visit(states.curState);
                        if (stateListener != null)
                            stateListener.accept(states.curState, result.data());

                        final List<S> nextStates;
                        if (result.getStateOverride() != null)
                            nextStates = result.getStateOverride();
                        else
                            nextStates = defaultStates.get(states.curState);

                        if (nextStates != null && !nextStates.isEmpty()) {
                            for (var state : nextStates) {
                                if (state != finalState && state != null) {
                                    queuesStates.add(new States<>(states.curState, state));
                                }
                            }
                        } else {
                            // If there is no next state, it could be either a final state or a guard blocking a state
                            if (agentState.isProcessed())
                                lastStates.add(states.curState);
                        }
                    } finally {
                        pendingStates.decrementAndGet();
                    }
                };

                if (parallelStatesProcessing) {
                    pendingStates.incrementAndGet();
                    CompletableFuture.runAsync(() -> Exceptions.rethrowRuntime(run), executor);
                }
                else
                    run.run();
            }

            return new AgentResult<>(statesProcessed.get(), agentState.data());
        } catch (Exception e) {
            Throwable refEx = e;

            while (refEx.getCause() != null) {
                refEx = refEx.getCause();
            }
            throw new RuntimeException("Error during " + lastStateProcessing.get() + "State:" + refEx, refEx);
        }
    }

    public CompletableFuture<I> processAsync(I input, BiConsumer<S, I> stateListener) {
        return sendMessageReturn(new AgentExecutionRequest<>(input, stateListener)).thenCompose(innerFuture -> innerFuture).thenApply(AgentResult::result);
    }

    public I process(I input, int timeout, TimeUnit timeUnit) {
        return process(input, timeout, timeUnit, null);
    }
    public I process(I input, int timeout, TimeUnit timeUnit, BiConsumer<S, I> stateListener) {
        return Exceptions.rethrowRuntime( () -> processAsync(input, stateListener).get(timeout, timeUnit));
    }

    public I process(I input, BiConsumer<S, I> stateListener) {
        return process(input, 1, TimeUnit.HOURS, stateListener);
    }

    public static <S extends Enum, I extends Record>  AiAgentBuilderActor<S, I> builder(boolean autoGuards) {
        return new AiAgentBuilderActor<>(autoGuards);
    }
}
