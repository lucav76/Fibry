package eu.lucaventuri.fibry.fsm;

import java.util.function.Consumer;

/**
 * A consumer-based builder for creating finite state machines (FSM).
 *
 * This class extends {@link FsmBuilderBase} and specializes in using consumer functions
 * to handle state transitions in FSMs. It provides functionality for defining FSM states,
 * transitions, and constructing an FSM instance based on these configurations.
 *
 * @param <S> The type of states, which must extend {@code Enum}.
 * @param <M> The type of message or event that triggers state transitions.
 * @param <I> The type of additional information passed during state transitions.
 */
public class FsmBuilderConsumer<S extends Enum, M, I> extends FsmBuilderBase<S, M, Consumer<FsmContext<S, M, I>>, I> {
}
