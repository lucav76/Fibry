package eu.lucaventuri.fibry.ai;

import eu.lucaventuri.fibry.fsm.FsmContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public interface AgentNode<S extends Enum, I extends Record> extends Function<AgentState<S, I>, AgentState<S, I>> {
    static <S extends Enum, T extends Record> AgentNode<S, T> combineSerial(List<AgentNode<S, T>> actorLogics, boolean blockAfterOverride) {
        return state -> {
            for (var actorLogic: actorLogics) {
                if (!blockAfterOverride || state.getStateOverride() == null)
                    state = actorLogic.apply(state);
            }

            return state;
        };
    }

    static <S extends Enum, T extends Record> AgentNode<S, T> combineParallel(List<AgentNode<S, T>> actorLogics, Executor executor) {
        return state -> {
            if (actorLogics.isEmpty())
                return state;

            Executor selectedExecutor = executor != null ? executor : command -> Thread.startVirtualThread(command);
            var results = actorLogics.stream()
                    .map(logic -> CompletableFuture.supplyAsync(() -> logic.apply(state), selectedExecutor))
                    .toList();

            CompletableFuture.allOf(results.toArray(new CompletableFuture[0])).join();

            return state;
        };
    }
}
