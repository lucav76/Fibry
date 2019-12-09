package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.Map;

public class FsmTemplateActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmTemplate<S, M, A, I> {
    public FsmTemplateActor(Map<S, StateData<S, M, A, I>> mapEnums) {
        super(mapEnums);
    }

    public FsmActor<S, M, R, A, I> newFsmActor(S state) {
        return new FsmActor<>(mapStates, state);
    }
}
