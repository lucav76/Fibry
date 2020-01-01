package eu.lucaventuri.fibry.fsm;

import java.util.function.Consumer;

public class FsmBuilderConsumer<S extends Enum, M, I> extends FsmBuilderBase<S, M, Consumer<FsmContext<S, M, I>>, I> {
}
