package eu.lucaventuri.jmacs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static eu.lucaventuri.jmacs.CreationStrategy.*;
import eu.lucaventuri.jmacs.ActorSystem.*;

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

    public static class HttpStringWorker {
        public final String context;
        public final Function<HttpExchange, String> worker;

        public HttpStringWorker(String context, Function<HttpExchange, String> worker) {
            this.context = context;
            this.worker = worker;
        }
    }

    public static class NamedStereotype {
        private final CreationStrategy strategy;

        public NamedStereotype(CreationStrategy strategy) {
            this.strategy = strategy;
        }

        /**
         * @param actorLogic Logic associated to each actor (no value returned as a result of the message)
         * @param <T> Message type
         * @return a supplier of actors that are going to use the specified logic
         */
        public <T> Supplier<Actor<T, Void, Void>> workersCreator(Consumer<T> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return () -> config.newActor(actorLogic);
        }

        /**
         * @param actorLogic Logic associated to each actor
         * @param <T> Message type
         * @return a consumer that for each message accepted will create a new actor that will process it.
         * When appropriate, this is a simple way to run parallel processing, as long as you don't need to know the result
         */
        public <T> Consumer<T> workersAsConsumerCreator(Consumer<T> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return message -> config.newActor(actorLogic).sendMessage(message);
        }

        /**
         * @param actorLogic Logic associated to each actor (a value can be returned as a result of the message)
         * @param <T> Message type
         * @param <R> Return type
         * @return a supplier of actors that are going to use the specified logic
         */
        public <T, R> Supplier<Actor<T, R, Void>> workersWithReturnCreator(Function<T, R> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return () -> config.newActorWithReturn(actorLogic);
        }

        /**
         * @param actorLogic Logic associated to each actor
         * @param <T> Message type
         * @return a function that for each message accepted will create a new actor that will process it.
         * When appropriate, this is a simple way to run parallel processing
         */
        public <T, R> Function<T, CompletableFuture<R>> workersAsFunctionCreator(Function<T, R> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return message -> config.newActorWithReturn(actorLogic).sendMessageReturn(message);
        }

        /**
         * Opens a Java embedded HTTP server (yes, com.sun.net.httpserver.HttpServer), where each request is server by an actor.
         * The workers need to implement the HttpHandler interface.
         * This method is not recommended for a real server, but if you need something simple, it can be useful.
         *
         * @param port HTTP port to open
         * @param workers pairs of context and handler, associating a path to a worker
         * @throws IOException
         */
        public void embeddedHttpServer(int port, HttpWorker... workers) throws IOException {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            for (HttpWorker worker : workers) {
                server.createContext(worker.context, exchange -> {
                    config.<HttpExchange>newActor((ex, actor) -> {
                        Exceptions.log(() -> worker.handler.handle(ex));
                        actor.askExit();
                    }).sendMessage(exchange);
                });
            }
            server.start();
        }

        /**
         * Opens a Java embedded HTTP server (yes, com.sun.net.httpserver.HttpServer), where each request is server by an actor.
         * The workers need to implement Function<HttpExchange, String>, therefore they can just return a string (so they are only useful on very simple cases).
         * This method is not recommended for a real server, but if you need something simple, it can be useful.
         *
         * @param port HTTP port to open
         * @param workers pairs of context and handler, associating a path to a worker
         * @throws IOException
         */
        public void embeddedHttpServer(int port, HttpStringWorker... workers) throws IOException {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            for (HttpStringWorker worker : workers) {
                server.createContext(worker.context, exchange -> {
                    config.<HttpExchange>newActor((ex, actor) -> {
                        Exceptions.log(() -> {
                            String answer = worker.worker.apply(ex);
                            ex.sendResponseHeaders(200, answer.getBytes().length);//response code and length
                            OutputStream os = ex.getResponseBody();
                            os.write(answer.getBytes());
                            os.close();
                        });

                        actor.askExit();
                    }).sendMessage(exchange);
                });
            }
            server.start();
        }

        /** Creates a named actor that does not receive messages; this is useful to execute code in a remote thread */
        public <S> SinkActor<S> sink(String name, S state) {
            NamedStateActorCreator<S> config = named(name).initialState(state);

            return config.newActor(message -> {
            });
        }

        /** Creates an actor that does not receive messages; this is useful to execute code in a remote thread */
        public <S> SinkActor<S> sink(S state) {
            NamedStateActorCreator<S> config = anonymous().initialState(state);

            return config.newActor(message -> {
            });
        }

        /** Creates an actor that runs a Runnable, once */
        public SinkActor<Void> runOnce(Runnable run) {
            SinkActor<Void> actor = sink(null);

            actor.execAsync(() -> {
                run.run();
                actor.askExit();
            });

            return actor;
        }

        /**
         * Creates an actor that runs a Consumer, once.
         * The consumer receives the actor itself, which sometimes can be useful (e.g. to check if somebody ask to exit)
         */
        public SinkActor<Void> runOnceWithThis(Consumer<SinkActor<Void>> actorLogic) {
            SinkActor<Void> actor = sink(null);

            actor.execAsync(() -> {
                actorLogic.accept(actor);
                actor.askExit();
            });

            return actor;
        }

        /** Creates an actor that runs a Runnable forever, every scheduleMs ms */
        public SinkActor<Void> schedule(Runnable run, long scheduleMs) {
            return schedule(run, scheduleMs, Long.MAX_VALUE);
        }

        /** Creates an actor that runs a Runnable maxTimes or until somebody asks for exit (this is controlled only in between executions); the actor is scheduled to run every scheduleMs ms */
        public SinkActor<Void> schedule(Runnable run, long scheduleMs, long maxTimes) {
            SinkActor<Void> actor = sink(null);

            actor.execAsync(() -> {
                long prev = System.currentTimeMillis();
                long times = 0;

                while (!actor.isExiting() && times < maxTimes) {
                    run.run();
                    long now = System.currentTimeMillis();
                    long diff = now - prev;

                    if (diff < scheduleMs)
                        SystemUtils.sleep(scheduleMs - diff);

                    prev = now;
                    times++;
                }
            });

            return actor;
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         *
         * @param port TCP port
         * @param workersLogic Logic of each worker
         * @param stateSupplier Supplier able to create a new state for each worker
         * @param <S> Type of state
         * @return the acceptor actor
         */
        public <S> SinkActor<Void> tcpAcceptor(int port, Consumer<Socket> workersLogic, Supplier<S> stateSupplier) {
            return runOnceWithThis(thisActor -> {
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    while (!thisActor.isExiting()) {
                        Socket clientSocket = serverSocket.accept();
                        Actor<Socket, Void, S> worker = anonymous().initialState(stateSupplier == null ? null : stateSupplier.get()).newActor(workersLogic);

                        worker.sendMessage(clientSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        private NamedStrategyActorCreator anonymous() {
            return ActorSystem.anonymous().strategy(strategy);
        }

        private NamedStrategyActorCreator named(String name) {
            return ActorSystem.named(name).strategy(strategy);
        }
    }

    public static NamedStereotype threads() {
        return new NamedStereotype(THREAD);
    }

    public static NamedStereotype auto() {
        return new NamedStereotype(AUTO);
    }

    public static NamedStereotype fibers() {

        return new NamedStereotype(FIBER);
    }
}
