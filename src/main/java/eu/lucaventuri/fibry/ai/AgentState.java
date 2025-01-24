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
    private final AtomicReference<T> data = new AtomicReference<>();
    private final AtomicReference<List<S>> stateOverride = new AtomicReference<>();
    private final Set<S> visitedStates = ConcurrentHashSet.build();

    public AgentState(T initialState) {
        data.set(initialState);
    }

    public T data() {
        return data.get();
    }

    public void setData(T newState) {
        data.set(newState);
    }

    /** Set the value of an attribute in the data() object */
    public <TO>  AgentState<S, T> setAttribute(String stateAttributeName, TO newValue) {
        data.set(RecordUtils.with(data.get(), stateAttributeName, newValue));

        return this;
    }

    /** Merge the value of an attribute in the data() object with the new value */
    public <TO> AgentState<S, T> mergeAttribute(String attributeName, UnaryOperator<TO> merger) {
        data.getAndUpdate(s -> RecordUtils.merge(s, attributeName, merger));

        return this;
    }

    /** Adds values to a list present in an attribute of the data() object  */
    public <TO> AgentState<S, T> addToList(String attributeName, List<TO> valuesToAdd) {
        data.getAndUpdate(s -> RecordUtils.addToList(s, attributeName, valuesToAdd));

        return this;
    }

    /** Adds values to a set present in an attribute of the data() object  */
    public <TO> AgentState<S, T> addToSet(String attributeName, Set<TO> valuesToAdd) {
        data.getAndUpdate(s -> RecordUtils.addToSet(s, attributeName, valuesToAdd));

        return this;
    }

    /** Add/Put values into a map present in an attribute of the data() object  */
    public <K, V> AgentState<S, T> addToMap(String attributeName, Map<K, V> valuesToAdd) {
        data.getAndUpdate(s -> RecordUtils.addToMap(s, attributeName, valuesToAdd));

        return this;
    }

    /** Set the value of several attributes in the data() object */
    public AgentState<S, T> setAttributes(Map<String, Object> newAttributes) {
        data.set(RecordUtils.with(data.get(), newAttributes));

        return this;
    }

    public List<S> getStateOverride() {
        return stateOverride.get();
    }

    /** Can override the state that will be processed after this node */
    public void setStateOverride(S newStateOverride) {
        stateOverride.set(newStateOverride == null ? null : List.of(newStateOverride));
    }

    /** Can override the states that will be processed after this node */
    public void setStateOverride(List<S> newStatesOverride) {
        stateOverride.set(newStatesOverride);
    }

    void visit(S state) {
        visitedStates.add(state);
    }

    public Set<S> getVisitedStates() {
        return new HashSet<>(visitedStates);
    }
}
