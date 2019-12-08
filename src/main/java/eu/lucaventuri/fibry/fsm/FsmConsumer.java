package eu.lucaventuri.fibry.fsm;

import java.util.Map;
import java.util.function.Consumer;

public class FsmConsumer<S extends Enum, M, A extends Consumer<FsmContext<S, M>>> extends FsmBase<S, M, A> {
    FsmConsumer(Map<S, State<S, M, A>> mapStates, S currentState) {
        super(mapStates, currentState);
    }

    public S onEvent(M event) {
        return onEvent(event, true, true);
    }

    public S onEvent(M event, boolean notifyConsumer, boolean exceptionOnUnexpectedEvent) {
        var prevState = mapStates.get(currentState);

        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);
        currentState = nextState.state;

        if (notifyConsumer && nextState.actor != null)
            nextState.actor.accept(new FsmContext<>(prevState.state, nextState.state, event));

        return nextState.state;
    }
}
