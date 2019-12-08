package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.Map;

public class FsmTemplateActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M>, R, ?>> extends FsmTemplate<S, M, A> {
    public FsmTemplateActor(Map<S, StateData<S, M, A>> mapEnums) {
        super(mapEnums);
    }

    public FsmActor<S, M, R, A> newFsmActor(S state) {
        return new FsmActor<>(mapStates, state);
    }
}
