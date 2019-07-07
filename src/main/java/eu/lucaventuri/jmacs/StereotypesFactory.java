package eu.lucaventuri.jmacs;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Connection acceptor
// Embedded web server acceptor
// Anonymous workers
/*public class StereotypesFactory {
    public static class NamedActorCreator {
        private final String name;  // Can be null
        private CreationStrategy strategy = CreationStrategy.AUTO;

        public class NamedStateActorCreator<S> {
            private final S initialState;

            public NamedStateActorCreator(S initialState) {
                this.initialState = initialState;
            }

            public <T> Supplier<Actor<T, Void, S>> workerCreator(Consumer<T> actorLogic) {
                ActorSystem.NamedActorCreator.NamedStateActorCreator<S> config = ActorSystem.named(name).strategy(strategy).initialState(null);

                return () -> config.newActor(actorLogic);
            }

            public <T> Consumer<T> workerConsumer(Consumer<T> actorLogic) {
                ActorSystem.NamedActorCreator.NamedStateActorCreator<S> config = ActorSystem.named(name).strategy(strategy).initialState(null);

                return message -> config.newActor(actorLogic).sendMessage(message);
            }

            public <T, R> Function<T, CompletableFuture<R>> workerFunction(Function<T, R> actorLogic) {
                ActorSystem.NamedActorCreator.NamedStateActorCreator<S> config = ActorSystem.named(name).strategy(strategy).initialState(null);

                return message -> config.newActorWithReturn(actorLogic).sendMessageReturn(message);
            }

            public <T, R> Supplier<Actor<T, R, S>> workerWithReturnCreator(Function<T, R> actorLogic) {
                ActorSystem.NamedActorCreator.NamedStateActorCreator<S> config = ActorSystem.named(name).strategy(strategy).initialState(null);

                return () -> config.newActorWithReturn(actorLogic);
            }
        }

        private NamedActorCreator(String name) {
            this.name = name;
        }

        public <T> Supplier<Actor<T, Void, Void>> workerCreator(Consumer<T> actorLogic ) {
            return initialState((Void)null).workerCreator(actorLogic);
        }

        public <T, R> Supplier<Actor<T, R, Void>> workerWithReturnCreator(Function<T, R> actorLogic ) {
            return initialState((Void)null).workerWithReturnCreator(actorLogic);
        }

        public <T> Consumer<T> workerConsumer(Consumer<T> actorLogic ) {
            return initialState((Void)null).workerConsumer(actorLogic);
        }

        public <T, R> Function<T, CompletableFuture<R>> workerFunction(Function<T, R> actorLogic ) {
            return initialState((Void)null).workerFunction(actorLogic);
        }

        public <S> NamedActorCreator.NamedStateActorCreator<S> initialState(S state) {
            return new NamedStateActorCreator<>(state);
        }

        public NamedActorCreator strategy(CreationStrategy strategy) {
            this.strategy = strategy;

            return this;
        }
    }

    public static NamedActorCreator named(String name) {
        return new NamedActorCreator(name);
    }

    public static NamedActorCreator anonymous() {
        return new NamedActorCreator(null);
    }
}
*/