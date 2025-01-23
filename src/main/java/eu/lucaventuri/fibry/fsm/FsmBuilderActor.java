package eu.lucaventuri.fibry.fsm;

import eu.lucaventuri.fibry.MessageOnlyActor;

import java.util.List;

/**
 * A builder class for creating a finite state machine (FSM) with actor-based handling of states and transitions.
 * This class extends the functionality of {@link FsmBuilderBase} by allowing behaviors to be defined via actors,
 * which process messages within the FSM context. The builder allows for defining state transitions, assigning
 * actors to states, and constructing the FSM for execution.
 *
 * @param <S> The type representing the states of the FSM. Must be an enum.
 * @param <M> The type of messages/events that trigger state transitions, used for the context. They must support equality operations.
 * @param <R> The type of the response or result returned by the actor handling a message.
 * @param <A> The type of the actor handling messages. Must extend {@link MessageOnlyActor}.
 * @param <I> The type of additional information or context passed during state transitions.
 */
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
