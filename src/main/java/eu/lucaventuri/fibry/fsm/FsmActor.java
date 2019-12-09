package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FsmActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmBase<S, M, A, I> {
    FsmActor(Map<S, State<S, M, A, I>> mapStates, S currentState) {
        super(mapStates, currentState);
    }

    public CompletableFuture<R> onEvent(M event, I userData, boolean exceptionOnUnexpectedEvent) {
        var prevState = mapStates.get(currentState);

        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);
        currentState = nextState.state;

        if (nextState.actor != null)
            return nextState.actor.sendMessageReturn(new FsmContext<>(prevState.state, nextState.state, event, userData));

        return null;
    }
}
