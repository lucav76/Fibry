package eu.lucaventuri.fibry.fsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

class State<S extends Enum, M, A extends Consumer<FsmContext<S, M, I>>, I> {
    final S state;
    private final List<TransitionState<M, S, A, I>> transitions = new ArrayList<>();
    final A actor;

    State(S state, A actor) {
        this.state = state;
        this.actor = actor;
    }

    State(S state) {
        this.state = state;
        this.actor = null;
    }

    void addTransition(TransitionState<M, S, A, I> transition) {
        Objects.requireNonNull(transition.event);
        Objects.requireNonNull(transition.targetState);

        for (var tr : transitions) {
            if (tr.event.equals(transition.event))
                throw new IllegalArgumentException("Event " + transition.event + " already added!");
        }
        transitions.add(transition);
    }

    State<S, M, A, I> onEvent(M event, boolean exceptionOnUnexpectedEvent) {
        for (var tr : transitions) {
            if (tr.event.equals(event))
                return tr.targetState;
        }

        if (exceptionOnUnexpectedEvent)
            throw new IllegalArgumentException("Unexpected event " + event);

        return this;
    }

    State<S, M, A, I> withActor(A newActor, boolean copyTransitions) {
        var newState = new State<S, M, A, I>(state, newActor);

        if (copyTransitions)
            newState.transitions.addAll(transitions);

        return newState;
    }

    public void replaceListActors(A actor, Map<S, State<S, M, A, I>> mapStates) {
        final List<TransitionState<M, S, A, I>> newTransitions = new ArrayList<>();

        for (var tr : transitions)
            newTransitions.add(tr.withState(mapStates.get(tr.targetState.state)));

        transitions.clear();
        transitions.addAll(newTransitions);
    }
}
