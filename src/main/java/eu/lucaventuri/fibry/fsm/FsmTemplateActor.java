package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.HashMap;
import java.util.Map;

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
