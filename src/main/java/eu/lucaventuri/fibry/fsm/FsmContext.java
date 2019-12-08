package eu.lucaventuri.fibry.fsm;

public class FsmContext<S extends Enum, M> {
    public final S previousState;
    public final S newState;
    public final M message;

    public FsmContext(S previousState, S newState, M message) {
        this.previousState = previousState;
        this.newState = newState;
        this.message = message;
    }
}
