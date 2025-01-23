package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Finite State Machine (FSM) template with actor-based behavior.
 * This class extends the functionality of the {@link FsmTemplate} by providing integration with actors for state transitions.
 *
 * @param <S> The type of states, represented as an enum.
 * @param <M> The type of messages or events triggering state transitions.
 * @param <R> The type of the response returned by the actor.
 * @param <A> The type of actor, which extends {@link MessageOnlyActor}.
 * @param <I> The type of additional contextual information passed during state transitions.
 */
public class FsmTemplateActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmTemplate<S, M, A, I> {
    public FsmTemplateActor(Map<S, StateData<S, M, A, I>> mapEnums) {
        super(mapEnums);
    }

    public FsmActor<S, M, R, A, I> newFsmActor(S state) {
        return new FsmActor<>(mapStates, state);
    }

    public FsmActor<S, M, R, A, I> newFsmActorReplace(S state, A actor) {
        return new FsmActor<>(replaceAllActors(actor), state);
    }

    private Map<S, State<S, M, A, I>> replaceAllActors(A actor) {
        var newMapStates = new HashMap<S, State<S, M, A, I>>();

        // Replace State in the map
        for (var entry : mapStates.entrySet()) {
            newMapStates.put(entry.getKey(), entry.getValue().withActor(actor, true));
        }

        // Replace State in the lists
        for (var entry : newMapStates.entrySet()) {
            entry.getValue().replaceListActors(actor, newMapStates);
        }

        return newMapStates;
    }
}
