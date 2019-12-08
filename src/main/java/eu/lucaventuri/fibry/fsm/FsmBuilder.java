package eu.lucaventuri.fibry.fsm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class StateData<S extends Enum, M, A extends Consumer<FsmContext<S, M>>> {
    final A actor;
    final List<TransitionEnum<M, S>> transtions = new ArrayList();

    StateData(A actor) {
        this.actor = actor;
    }
}

/**
 * Builder that can create a Fsm, Finite State machine
 *
 * @param <S> Type of the states, from an enum
 * @param <M> Type of the messages (they will need to support equals)
 */
class FsmBuilder<S extends Enum, M, A extends Consumer<FsmContext<S, M>>> {
    final Map<S, StateData<S, M, A>> mapStatesEnum = new HashMap<>();

    public class InState {
        final List<TransitionEnum<M, S>> transitions;

        public InState(List<TransitionEnum<M, S>> transitions) {
            this.transitions = transitions;
        }

        public FsmBuilder<S, M, A>.InState goTo(S targetState, M message) {
            transitions.add(new TransitionEnum<>(message, targetState));

            return this;
        }

        public InState addState(S state, A actor) {
            return FsmBuilder.this.addState(state, actor);
        }

        public FsmTemplate<S, M, ? extends Consumer<FsmContext<S, M>>> build() {
            return FsmBuilder.this.build();
        }
    }

    public InState addState(S state, A actor) {
        if (mapStatesEnum.containsKey(state))
            throw new IllegalArgumentException("State " + state + "already defined!");

        mapStatesEnum.putIfAbsent(state, new StateData<S, M, A>(actor));

        return new InState(mapStatesEnum.get(state).transtions);
    }

    public FsmTemplate<S, M, A> build() {
        return new FsmTemplate<S, M, A>(mapStatesEnum);
    }
}
