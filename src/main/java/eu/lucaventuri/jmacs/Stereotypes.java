package eu.lucaventuri.jmacs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.lucaventuri.common.Exceptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Connection acceptor
// Embedded web server acceptor
// Anonymous workers
public class Stereotypes {
    public static class HttpWorker {
        public final String context;
        public final HttpHandler handler;

        public HttpWorker(String context, HttpHandler handler) {
            this.context = context;
            this.handler = handler;
        }
    }

    public static class NamedActorCreator {
        private final MiniActorSystem.Strategy strategy;

        public NamedActorCreator(MiniActorSystem.Strategy strategy) {
            this.strategy = strategy;
        }

        public <T, S> Supplier<Actor<T, Void, Void>> workersCreator(Consumer<T> actorLogic) {
            MiniActorSystem.NamedActorCreator.NamedStateActorCreator<Void> config = MiniActorSystem.anonymous().strategy(strategy).initialState(null);

            return () -> config.newActor(actorLogic);
        }

        public <T> Consumer<T> workersConsumer(Consumer<T> actorLogic) {
            MiniActorSystem.NamedActorCreator.NamedStateActorCreator<Void> config = MiniActorSystem.anonymous().strategy(strategy).initialState(null);

            return message -> config.newActor(actorLogic).sendMessage(message);
        }

        public <T, R> Supplier<Actor<T, R, Void>> workersWithReturnCreator(Function<T, R> actorLogic) {
            MiniActorSystem.NamedActorCreator.NamedStateActorCreator<Void> config = MiniActorSystem.anonymous().strategy(strategy).initialState(null);

            return () -> config.newActorWithReturn(actorLogic);
        }

        public <T, R> Function<T, CompletableFuture<R>> workersFunction(Function<T, R> actorLogic) {
            MiniActorSystem.NamedActorCreator.NamedStateActorCreator<Void> config = MiniActorSystem.anonymous().strategy(strategy).initialState(null);

            return message -> config.newActorWithReturn(actorLogic).sendMessageReturn(message);
        }

        public void embeddedHttpServer(int port, HttpWorker... workers) throws IOException {
            MiniActorSystem.NamedActorCreator.NamedStateActorCreator<Void> config = MiniActorSystem.anonymous().strategy(strategy).initialState(null);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            for (HttpWorker worker : workers) {
                server.createContext(worker.context, exchange -> {
                    final AtomicReference<Actor<HttpExchange, Void, Void>> actorRef = new AtomicReference<>();

                    actorRef.set(config.<HttpExchange>newActor(ex -> {
                        Exceptions.log(() -> worker.handler.handle(ex));
                        actorRef.get().askExit();
                    }));
                });
            }
            server.start();
        }
    }

    public static NamedActorCreator threads() {
        return new NamedActorCreator(MiniActorSystem.Strategy.THREAD);
    }

    public static NamedActorCreator auto() {
        return new NamedActorCreator(MiniActorSystem.Strategy.AUTO);
    }

    public static NamedActorCreator fibers() {
        return new NamedActorCreator(MiniActorSystem.Strategy.FIBER);
    }
}
