package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.List;
import java.util.function.Consumer;

public class FsmBuilderActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmBuilder<S, M, A, I> {
    @Override
    public FsmTemplateActor<S, M, R, A, I> build() {
        return new FsmTemplateActor<>(mapStatesEnum);
    }

    public class InStateActor extends InState {
        public InStateActor(List<TransitionEnum<M, S>> transitions) {
            super(transitions);
        }

        public FsmBuilderActor<S, M, R, A, I>.InStateActor goTo(S targetState, M message) {
            transitions.add(new TransitionEnum<>(message, targetState));

            return this;
        }

        public InStateActor addState(S state, A actor) {
            return FsmBuilderActor.this.addState(state, actor);
        }

        public FsmTemplateActor<S, M, R, A, I> build() {
            return FsmBuilderActor.this.build();
        }
    }

    public InStateActor addState(S state, A actor) {
        if (mapStatesEnum.containsKey(state))
            throw new IllegalArgumentException("State " + state + "already defined!");

        mapStatesEnum.putIfAbsent(state, new StateData<S, M, A, I>(actor));

        return new InStateActor(mapStatesEnum.get(state).transtions);
    }
}
