package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class FsmActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M>, R, ?>> extends FsmBase<S, M, A> {
    FsmActor(Map<S, State<S, M, A>> mapStates, S currentState) {
        super(mapStates, currentState);
    }

    public CompletableFuture<R> onEvent(M event, boolean exceptionOnUnexpectedEvent) {
        var prevState = mapStates.get(currentState);

        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);
        currentState = nextState.state;

        if (nextState.actor != null)
            return nextState.actor.sendMessageReturn(new FsmContext<>(prevState.state, nextState.state, event));

        return null;
    }
}
