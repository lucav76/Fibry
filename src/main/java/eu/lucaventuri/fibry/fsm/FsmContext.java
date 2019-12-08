package eu.lucaventuri.fibry.fsm;

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
