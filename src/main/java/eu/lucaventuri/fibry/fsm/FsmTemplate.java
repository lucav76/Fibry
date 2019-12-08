package eu.lucaventuri.fibry.fsm;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Finite State machine
 *
 * @param <S> Type of the states, from an enum
 * @param <M> Type of the messages (they will need to support equals)
 */
public class FsmTemplate<S extends Enum, M, A extends Consumer<FsmContext<S, M>>> {
    final Map<S, State<S, M, A>> mapStates;

    public FsmTemplate(Map<S, StateData<S, M, A>> mapEnums) {
        this.mapStates = new HashMap<>();

        ingestKeys(mapEnums);
        ingestTransitions(mapEnums);
    }

    public FsmConsumer<S, M, A> newFsmConsumer(S state) {
        return new FsmConsumer<>(mapStates, state);
    }

   /* public <R> FsmActor<S,M,R,A> newFsmActor(S state) {
        return new FsmActor<>(mapStates, state);
    }*/

    private void ingestTransitions(Map<S, StateData<S, M, A>> mapEnums) {
        for (var st : mapEnums.entrySet()) {
            var state = mapStates.get(st.getKey());
            for (var tr : st.getValue().transtions) {
                state.addTransition(convert(tr));
            }
        }

    }

    private TransitionState<M, S, A> convert(TransitionEnum<M, S> tr) {
        var targetState = mapStates.get(tr.targetState);

        return new TransitionState<>(tr.event, targetState);
    }

    private void ingestKeys(Map<S, StateData<S, M, A>> mapEnums) {
        for (var state : mapEnums.entrySet()) {
            mapStates.put(state.getKey(), new State(state.getKey(), state.getValue().actor));
        }

        if (mapStates.size() < 2)
            throw new IllegalArgumentException("You need at least 2 states!");

        var usedStates = mapEnums.values().stream().flatMap(state -> state.transtions.stream().map(transition -> transition.targetState)).distinct().collect(Collectors.toList());

        for (var st : usedStates) {
            if (!mapStates.keySet().contains(st))
                throw new IllegalArgumentException("Transition to unknown state " + st);
        }
    }
}
