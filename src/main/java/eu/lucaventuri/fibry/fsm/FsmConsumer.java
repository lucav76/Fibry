package eu.lucaventuri.fibry.fsm;

import java.util.Map;
import java.util.function.Consumer;

public class FsmConsumer<S extends Enum, M, A extends Consumer<FsmContext<S, M, I>>, I> extends FsmBase<S, M, A, I> {
    FsmConsumer(Map<S, State<S, M, A, I>> mapStates, S currentState) {
        super(mapStates, currentState);
    }

    public S onEvent(M event, I userData) {
        return onEvent(event, userData, true, true);
    }

    public S onEvent(M event, I userData, boolean notifyConsumer, boolean exceptionOnUnexpectedEvent) {
        var prevState = mapStates.get(currentState);

        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);
        currentState = nextState.state;

        if (notifyConsumer && nextState.actor != null)
            nextState.actor.accept(new FsmContext<>(prevState.state, nextState.state, event, userData));

        return nextState.state;
    }
}
