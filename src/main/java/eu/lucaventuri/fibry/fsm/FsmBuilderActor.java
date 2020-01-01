package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.List;

public class FsmBuilderActor<S extends Enum, M, R, A extends MessageOnlyActor<FsmContext<S, M, I>, R, ?>, I> extends FsmBuilderBase<S, M, A, I> {
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

        public InStateActor addState(S state, A consumer) {
            return FsmBuilderActor.this.addState(state, consumer);
        }

        public InStateActor addState(S state) {
            return FsmBuilderActor.this.addState(state, null);
        }

        public FsmTemplateActor<S, M, R, A, I> build() {
            return FsmBuilderActor.this.build();
        }
    }

    public InStateActor addState(S state, A consumer) {
        if (mapStatesEnum.containsKey(state))
            throw new IllegalArgumentException("State " + state + "already defined!");

        mapStatesEnum.putIfAbsent(state, new StateData<S, M, A, I>(consumer));

        return new InStateActor(mapStatesEnum.get(state).transtions);
    }
}
