package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.fibry.fsm.FsmBuilderConsumer;
import eu.lucaventuri.fibry.fsm.FsmContext;

import java.util.Arrays;
import java.util.function.Consumer;

public class AiAgentBuilderConsumer<S extends Enum, I extends Record> {
    final FsmBuilderConsumer<S, S, AgentState<S, I>> builder = new FsmBuilderConsumer<>();

    public AiAgentBuilderConsumer<S, I> addState(S state, Consumer<FsmContext<S, S, AgentState<S, I>>> actor) {
        goToAll(builder.addState(state, actor), state.getDeclaringClass());

        return this;
    }

    private void goToAll(FsmBuilderConsumer<S, S, AgentState<S, I>>.InState inState, Class<S> clazz) {
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException("Provided class is not an Enum type.");
        }

        // Get all values of the Enum and iterate over them
        Arrays.stream(clazz.getEnumConstants())
                .forEach(it -> inState.goTo(it, it));
    }

    /*public AiAgent<S, I> build(I initialState, S finalState) {
        if (initialState == null)
            throw new IllegalArgumentException("The initial state cannot be null!");

        return new AiAgent<>(builder.build(), initialState, finalState);
    }*/
}
