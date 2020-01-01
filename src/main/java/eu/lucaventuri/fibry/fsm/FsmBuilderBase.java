package eu.lucaventuri.fibry.fsm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class StateData<S extends Enum, M, C extends Consumer<FsmContext<S, M, I>>, I> {
    final C consumer;
    final List<TransitionEnum<M, S>> transtions = new ArrayList();

    StateData(C consumer) {
        this.consumer = consumer;
    }
}

/**
 * Builder that can create a Fsm, Finite State machine
 *
 * @param <S> Type of the states, from an enum
 * @param <M> Type of the messages (they will need to support equals)
 */
abstract class FsmBuilderBase<S extends Enum, M, C extends Consumer<FsmContext<S, M, I>>, I> {
    final Map<S, StateData<S, M, C, I>> mapStatesEnum = new HashMap<>();

    public class InState {
        final List<TransitionEnum<M, S>> transitions;

        public InState(List<TransitionEnum<M, S>> transitions) {
            this.transitions = transitions;
        }

        public FsmBuilderBase<S, M, C, I>.InState goTo(S targetState, M message) {
            transitions.add(new TransitionEnum<>(message, targetState));

            return this;
        }

        public InState addState(S state, C consumer) {
            return FsmBuilderBase.this.addState(state, consumer);
        }

        public FsmTemplate<S, M, ? extends Consumer<FsmContext<S, M, I>>, I> build() {
            return FsmBuilderBase.this.build();
        }
    }

    public InState addState(S state, C consumer) {
        if (mapStatesEnum.containsKey(state))
            throw new IllegalArgumentException("State " + state + "already defined!");

        mapStatesEnum.putIfAbsent(state, new StateData<S, M, C, I>(consumer));

        return new InState(mapStatesEnum.get(state).transtions);
    }

    public FsmTemplate<S, M, C, I> build() {
        return new FsmTemplate<>(mapStatesEnum);
    }
}
