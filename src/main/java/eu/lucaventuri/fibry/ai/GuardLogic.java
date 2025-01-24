package eu.lucaventuri.fibry.ai;

import java.util.Arrays;
import java.util.Collection;

public interface GuardLogic<S extends Enum, I extends Record> {
    boolean accept(S prevState, S nextState, AgentState<S, I> agentState);

    static <S extends Enum, I extends Record> GuardLogic<S, I> waitStates(Collection<S> requiredStates) {
        return (prevState, nextState, gentState) -> {
            return gentState.getVisitedStates().containsAll(requiredStates);
        };
    }

    static <S extends Enum, I extends Record> GuardLogic<S, I> waitStates(S... requiredStates) {
        return (prevState, nextState, gentState) -> {
            return gentState.getVisitedStates().containsAll(Arrays.stream(requiredStates).toList());
        };
    }

    static <S extends Enum, I extends Record> GuardLogic<S, I> always() {
        return (prevState, nextState, gentState) -> true;
    }

    static <S extends Enum, I extends Record> GuardLogic<S, I> never() {
        return (prevState, nextState, gentState) -> false;
    }
}
