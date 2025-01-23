package eu.lucaventuri.fibry.fsm;

/**
 * Represents the context of a state transition in a Finite State Machine (FSM).
 *
 * This class encapsulates the details of a state transition, including the previous state,
 * the new state, the associated message/event that triggered the transition,
 * and any additional information relevant to the transition.
 *
 * @param <S> Type representing the states of the FSM. Must be an Enum.
 * @param <M> Type of the message or event that triggers state transitions.
 * @param <I> Type of the additional information passed during state transitions.
 */
public class FsmContext<S extends Enum, M, I> {
    public final S previousState;
    public final S newState;
    public final M message;
    public final I info;

    public FsmContext(S previousState, S newState, M message, I info) {
        this.previousState = previousState;
        this.newState = newState;
        this.message = message;
        this.info = info;
    }
}
