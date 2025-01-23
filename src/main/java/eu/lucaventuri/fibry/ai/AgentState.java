package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.common.ConcurrentHashSet;
import eu.lucaventuri.common.RecordUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public class AgentState<S extends Enum, T extends Record> {
    private final AtomicReference<T> state = new AtomicReference<>();
    private final AtomicReference<List<S>> stateOverride = new AtomicReference<>();
    private final Set<S> visitedStates = ConcurrentHashSet.build();

    public AgentState(T initialState) {
        state.set(initialState);
    }

    public T getState() {
        return state.get();
    }

    public void setState(T newState) {
        state.set(newState);
    }

    public <TO>  AgentState<S, T> replace(String stateAttributeName, TO newValue) {
        state.set(RecordUtils.with(state.get(), stateAttributeName, newValue));

        return this;
    }

    public <TO> AgentState<S, T> merge(String attributeName, UnaryOperator<TO> merger) {
        state.getAndUpdate( s -> RecordUtils.merge(s, attributeName, merger));

        return this;
    }

    public <TO> AgentState<S, T> mergeList(String attributeName, List<TO> valuesToAdd) {
        state.getAndUpdate( s -> RecordUtils.mergeList(s, attributeName, valuesToAdd));

        return this;
    }

    public <TO> AgentState<S, T> mergeSet(String attributeName, Set<TO> valuesToAdd) {
        state.getAndUpdate( s -> RecordUtils.mergeSet(s, attributeName, valuesToAdd));

        return this;
    }

    public <K, V> AgentState<S, T> mergeList(String attributeName, Map<K, V> valuesToAdd) {
        state.getAndUpdate( s -> RecordUtils.mergeMap(s, attributeName, valuesToAdd));

        return this;
    }

    public AgentState<S, T> replace(Map<String, Object> newAttributes) {
        state.set(RecordUtils.with(state.get(), newAttributes));

        return this;
    }

    public List<S> getStateOverride() {
        return stateOverride.get();
    }

    public void setStateOverride(S newStateOverride) {
        stateOverride.set(newStateOverride == null ? null : List.of(newStateOverride));
    }

    public void setStateOverride(List<S> newStatesOverride) {
        stateOverride.set(newStatesOverride);
    }

    public void visit(S state) {
        visitedStates.add(state);
    }

    public Set<S> getVisitedStates() {
        return new HashSet<>(visitedStates);
    }
}
