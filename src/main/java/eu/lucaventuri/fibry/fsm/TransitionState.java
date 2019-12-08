package eu.lucaventuri.fibry.fsm;

import java.util.function.Consumer;

class TransitionState<E, S extends Enum, A extends Consumer<FsmContext<S, E, I>>, I> {
    final E event;
    final State<S, E, A, I> targetState;

    TransitionState(E event, State<S, E, A, I> targetState) {
        this.event = event;
        this.targetState = targetState;
    }

    TransitionState<E, S, A, I> withState(State<S, E, A, I> newState) {
        return new TransitionState<>(event, newState);
    }
}
