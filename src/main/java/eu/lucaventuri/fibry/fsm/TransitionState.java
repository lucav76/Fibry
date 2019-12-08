package eu.lucaventuri.fibry.fsm;

import java.util.function.Consumer;

class TransitionState<E, S extends Enum, A extends Consumer<FsmContext<S, E>>> {
    final E event;
    final State<S, E, A> targetState;

    TransitionState(E event, State<S, E, A> targetState) {
        this.event = event;
        this.targetState = targetState;
    }
}
