package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.fibry.*;
import eu.lucaventuri.fibry.fsm.FsmContext;
import eu.lucaventuri.fibry.fsm.FsmTemplateActor;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Override
    protected CompletableFuture<AiAgent.AgentResult<I>> onMessage(AiAgent.AgentExecutionRequest<S, I> message) {
        return CompletableFuture.supplyAsync(() -> executeInCurrentThread(message.input, message.stateListener), executor);
    }

    public record AgentResult<I>(int statesProcessed, I result) {}
    public record AgentExecutionRequest<S extends Enum, I>(I input, BiConsumer<S, I> stateListener) {}

    record States<S>(S prevState, S curState) {
    }

    public AiAgent(FsmTemplateActor<S, S, AgentState<S, I>, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>, AgentState<S, I>> fsm, S initialState, S finalState, Map<S, List<S>> defaultStates) {
        super(new FibryQueue<>(), null, null, Integer.MAX_VALUE);
        this.fsm = fsm;
        this.initialState = initialState;
        this.finalState = finalState;
        this.defaultStates = defaultStates;

        closeStrategy = CloseStrategy.SEND_POISON_PILL;
        CreationStrategy.AUTO.start(this);
    }

    private AgentResult<I> executeInCurrentThread(I initialContext, BiConsumer<S, I> stateListener) {
        AtomicInteger statesProcessed = new AtomicInteger();

        return Exceptions.rethrowRuntime(() -> {
            AgentState<S, I> agentState = new AgentState<>(initialContext);
            Queue<States<S>> queuesStates = new ArrayDeque<>();

            queuesStates.add(new States<>(null, initialState));

            while (!queuesStates.isEmpty()) {
                statesProcessed.incrementAndGet();

                var states = queuesStates.poll();
                var actor = fsm.newFsmActor(states.curState);
                var result = actor.getActor().sendMessageReturn(new FsmContext<>(states.prevState, states.curState, null, agentState)).get();

                agentState.visit(states.curState);
                if (stateListener != null)
                    stateListener.accept(states.curState, result.data());

                final List<S> nextStates;
                if (result.getStateOverride() != null)
                    nextStates = result.getStateOverride();
                else
                    nextStates = defaultStates.get(states.curState);

                if (nextStates != null) {
                    for (var state : nextStates) {
                        if (state != finalState && state != null) {
                            queuesStates.add(new States<>(states.curState, state));
                        }
                    }
                }
            }

            return new AgentResult<>(statesProcessed.get(), agentState.data());
        });
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
}
