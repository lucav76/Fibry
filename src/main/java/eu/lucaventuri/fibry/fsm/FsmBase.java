package eu.lucaventuri.fibry.fsm;

import java.util.Map;
import java.util.function.Consumer;

public class FsmBase<S extends Enum, M, A extends Consumer<FsmContext<S, M, I>>, I> {
    protected final Map<S, State<S, M, A, I>> mapStates;
    protected volatile S currentState;

    public FsmBase(Map<S, State<S, M, A, I>> mapStates, S currentState) {
        this.mapStates = mapStates;
        this.currentState = currentState;

        if (!mapStates.containsKey(currentState))
            throw new IllegalArgumentException("Unknown state" + currentState);
    }

    public S getCurrentState() {
        return currentState;
    }

    public A getActor() {
        return mapStates.get(currentState).actor;
    }

    /** Change the state directly, bypassing any actors */
    public void jumpToState(S newState) {
        currentState = newState;
    }
}
