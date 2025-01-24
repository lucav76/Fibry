package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.fibry.fsm.FsmContext;

import java.util.function.Function;

public interface AgentNode<S extends Enum, I extends Record> extends Function<AgentState<S, I>, AgentState<S, I>> {
}
