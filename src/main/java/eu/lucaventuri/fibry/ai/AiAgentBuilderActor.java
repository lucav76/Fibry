package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.fibry.*;
import eu.lucaventuri.fibry.fsm.FsmBuilderActor;
import eu.lucaventuri.fibry.fsm.FsmContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class AiAgentBuilderActor<S extends Enum, I extends Record> {
    final FsmBuilderActor<S, S, AgentState<S, I>, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>, AgentState<S, I>> builder = new FsmBuilderActor<>();
    Map<S, List<S>> defaultStates = new ConcurrentHashMap<>();
    Map<S, Set<S>> incomingStates = new ConcurrentHashMap<>();
    private final boolean autoGuards;

    public AiAgentBuilderActor(boolean autoGuards) {
        this.autoGuards = autoGuards;
    }

    private AiAgentBuilderActor<S, I> addStateActor(S state, List<S> defaultNextStates, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void> actor) {
        if (defaultStates.containsKey(state))
            throw new IllegalArgumentException("State " + state + " already defined");

        goToAll(builder.addState(state, actor), state.getDeclaringClass());
        defaultStates.put(state, defaultNextStates);
        for (var nextState: defaultNextStates)
            incomingStates.computeIfAbsent(nextState, s -> ConcurrentHashSet.build()).add(state);

        return this;
    }

    private AiAgentBuilderActor<S, I> addStateActorAsync(S state, List<S> defaultNextStates, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, CompletableFuture<AgentState<S, I>>, Void> actor) {
        if (defaultStates.containsKey(state))
            throw new IllegalArgumentException("State " + state + " already defined");

        goToAll(builder.addState(state, MessageOnlyActor.fromAsync(actor)), state.getDeclaringClass());
        defaultStates.put(state, defaultNextStates);
        for (var nextState: defaultNextStates)
            incomingStates.computeIfAbsent(nextState, s -> ConcurrentHashSet.build()).add(state);

        return this;
    }

    public AiAgentBuilderActor<S, I> addState(S state, S defaultNextState, int parallelism, Function<AgentState<S, I>, AgentState<S, I>> actorLogic, GuardLogic<S, I> guard) {
        return addStateMultiInternal(state, defaultNextState == null? List.of() : List.of(defaultNextState),parallelism, ctx -> actorLogic.apply(ctx.info), guard);
    }

    public AiAgentBuilderActor<S, I> addStateMulti(S state, List<S> defaultNextStates, int parallelism, Function<AgentState<S, I>, AgentState<S, I>> actorLogic, GuardLogic<S, I> guard) {
        return addStateMultiInternal(state, defaultNextStates,parallelism, ctx -> actorLogic.apply(ctx.info), guard);
    }

    private AiAgentBuilderActor<S, I> addStateMultiInternal(S state, List<S> defaultNextStates, int parallelism, Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> actorLogic, GuardLogic<S, I> guard) {
        actorLogic = logicWithGuard(state, actorLogic, guard);

        if (parallelism <= 0)
            return addStateActorAsync(state, defaultNextStates, ActorSystem.anonymous().newActorWithReturn(Stereotypes.auto().workersAsFunctionCreator(actorLogic)));
        return addStateActor(state, defaultNextStates, actorWithParallelism(parallelism, actorLogic));
    }




    /*public AiAgentBuilderActor<S, I> addStateMulti(S state, List<S> defaultNextStates, int parallelism, Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> actorLogic, GuardLogic<S, I> guard) {
        actorLogic = logicWithGuard(state, actorLogic, guard);

        if (parallelism <= 0)
            return addStateActorAsync(state, defaultNextStates, ActorSystem.anonymous().newActorWithReturn(Stereotypes.auto().workersAsFunctionCreator(actorLogic)));
        return addStateActor(state, defaultNextStates, actorWithParallelism(parallelism, actorLogic));
    }*/

    private static <S extends Enum, I extends Record> MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void> actorWithParallelism(int parallelism, Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> actorLogic) {
        return parallelism > 1 ? ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(parallelism), () -> (Void) null).newPoolWithReturn(actorLogic) : ActorSystem.anonymous().newActorWithReturn(actorLogic);
    }

    public AiAgentBuilderActor<S, I> addStateSerial(S state, S defaultNextState, int parallelism, List<AgentNode<S, I>> actorLogics, GuardLogic<S, I> guard) {
        return addStateSerial(state, defaultNextState == null? List.of() : List.of(defaultNextState), parallelism, actorLogics, guard);
    }

    public AiAgentBuilderActor<S, I> addStateSerial(S state, List<S> defaultNextStates, int parallelism, List<AgentNode<S, I>> actorLogics, GuardLogic<S, I> guard) {
        Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> combinedLogic = ctx -> {
            actorLogics.forEach(actorLogic -> actorLogic.apply(ctx.info));

            return ctx.info;
        };

        return addStateMultiInternal(state, defaultNextStates, parallelism, combinedLogic, guard);
    }

    public AiAgentBuilderActor<S, I> addStateParallel(S state, S defaultNextState, int parallelism, List<AgentNode<S, I>> actorLogics, GuardLogic<S, I> guard) {
        return addStateParallel(state, defaultNextState == null? List.of() : List.of(defaultNextState), parallelism, actorLogics, guard);
    }

    public AiAgentBuilderActor<S, I> addStateParallel(S state, List<S> defaultNextStates, int parallelism, List<AgentNode<S, I>> actorLogics, GuardLogic<S, I> guard) {
        List<Actor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>> actorsStatic = parallelism <= 0 ? null : actorLogics.stream().map(logic -> ActorSystem.anonymous().newActorWithReturn((FsmContext<S, S, AgentState<S, I>> ctx) -> logic.apply(ctx.info))).toList();

        Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> combinedLogic = ctx -> {
            CompletableFuture<AgentState<S, I>>[] fut = new CompletableFuture[actorLogics.size()];

            var actors = parallelism <= 0 ? actorLogics.stream().map(logic -> actorWithParallelism(parallelism, (FsmContext<S, S, AgentState<S, I>> it) -> logic.apply(it.info))).toList() : actorsStatic;

            for (int i = 0; i < actorLogics.size(); i++) {
                fut[i] = actors.get(i).sendMessageReturn(ctx);
            }

            try {
                CompletableFuture.allOf(fut).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            return ctx.info;
        };

        return addStateMultiInternal(state, defaultNextStates, parallelism, combinedLogic, guard);
    }

    private void goToAll(FsmBuilderActor<S, S, AgentState<S, I>, MessageOnlyActor<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>, Void>, AgentState<S, I>>.InStateActor inState, Class<S> clazz) {
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException("Provided class is not an Enum type.");
        }

        // Get all values of the Enum and iterate over them
        Arrays.stream(clazz.getEnumConstants())
                .forEach(it -> inState.goTo(it, it));
    }

    public AiAgent<S, I> build(S initialState, S finalState, boolean parallelStatesProcessing) {
        return build(initialState, finalState, parallelStatesProcessing, null, Integer.MAX_VALUE);
    }

    public AiAgent<S, I> build(S initialState, S finalState, boolean parallelStatesProcessing, String actorName, int queueCapacity) {
        if (initialState == null)
            throw new IllegalArgumentException("The initial state cannot be null!");

        if (finalState != null && !defaultStates.containsKey(finalState))
            addState(finalState, finalState, 1, it -> it, null);

        defaultStates.forEach((k, v) -> {
                    if (k == v && k != finalState)
                        throw new IllegalArgumentException("State " + k + " by default creates a loop");
                }
        );

        return new AiAgent<>(builder.build(), initialState, finalState, defaultStates, actorName, queueCapacity, parallelStatesProcessing, autoGuards);
    }

    Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> logicWithGuard(S state, Function<FsmContext<S, S, AgentState<S, I>>, AgentState<S, I>> actorLogic, GuardLogic<S, I> guard) {
        if (guard == null) {
            if (autoGuards)
              guard = GuardLogic.waitStates(incomingStates.computeIfAbsent(state, s -> ConcurrentHashSet.build()));
            else
              guard = GuardLogic.always();
        }

        var finalGuard = guard;

        return ctx -> {
            if (finalGuard.accept(ctx.previousState, ctx.newState, ctx.info)) {
                ctx.info.setStateOverride((S) null);
                ctx.info.setProcessed(true);
                return actorLogic.apply(ctx);
            }

            ctx.info.setProcessed(false);
            ctx.info.setStateOverride((S) null); // It will be re-processed, and hopefully at some point the guard will pass

            return ctx.info;
        };
    }
}
