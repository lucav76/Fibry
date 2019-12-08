package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class FsmActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmBase<S, M, A, I> {
    FsmActor(Map<S, State<S, M, A, I>> mapStates, S currentState) {
        super(mapStates, currentState);
    }

    /** The state is changed, then the actor is called asynchronously. So the actor will complete after the state change. */
    public CompletableFuture<R> onEventAfter(M event, I userData, boolean exceptionOnUnexpectedEvent) {
        var prevState = mapStates.get(currentState);
        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);

        return moveToStateAfter(nextState.state, userData, event);
    }

    /** The the actor is called synchronously, then the state changes. */
    public R onEventBefore(M event, I userData, boolean exceptionOnUnexpectedEvent) throws ExecutionException, InterruptedException {
        var prevState = mapStates.get(currentState);
        var nextState = prevState.onEvent(event, exceptionOnUnexpectedEvent);

        return moveToStateBefore(nextState.state, userData, event);
    }

    /** Change the state without event,but calling the actor */
    public R moveToStateBefore(S newState, I userData) throws ExecutionException, InterruptedException {
        return moveToStateBefore(newState, userData, null);
    }

    /** Change the state without event,but calling the actor */
    public CompletableFuture<R> moveToStateAfter(S newState, I userData) {
        return moveToStateAfter(newState, userData, null);
    }

    /** Unconditionally change the state, notifying the actor */
    private R moveToStateBefore(S newState, I userData, M event) throws ExecutionException, InterruptedException {
        var prevState = mapStates.get(currentState);
        var nextState = mapStates.get(newState);
        final R res = nextState.actor == null ? null : nextState.actor.sendMessageReturn(new FsmContext<>(prevState.state, nextState.state, event, userData)).get();

        currentState = newState;

        return res;
    }

    /** Unconditionally change the state, notifying the actor */
    private CompletableFuture<R> moveToStateAfter(S newState, I userData, M event) {
        var prevState = mapStates.get(currentState);
        var nextState = mapStates.get(newState);

        currentState = newState;

        return nextState.actor == null ? null : nextState.actor.sendMessageReturn(new FsmContext<>(prevState.state, nextState.state, event, userData));
    }
}
