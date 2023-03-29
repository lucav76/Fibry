package eu.lucaventuri.fibry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.lucaventuri.common.*;
import eu.lucaventuri.fibry.ActorSystem.NamedStateActorCreator;
import eu.lucaventuri.fibry.ActorSystem.NamedStrategyActorCreator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static eu.lucaventuri.fibry.CreationStrategy.*;

/**
 * Class providing functions for common use cases
 */
public class Stereotypes {
    private static AtomicBoolean debug = new AtomicBoolean(false);

    public static class HttpWorker {
        final String context;
        final HttpHandler handler;

        public HttpWorker(String context, HttpHandler handler) {
            this.context = context;
            this.handler = handler;
        }
    }

    public static class HttpStringWorker {
        final String context;
        final Function<HttpExchange, String> worker;

        public HttpStringWorker(String context, Function<HttpExchange, String> worker) {
            this.context = context;
            this.worker = worker;
        }
    }

    public static class NamedStereotype {
        private final CreationStrategy strategy;
        private final Exitable.CloseStrategy closeStrategy;

        NamedStereotype(CreationStrategy strategy, Exitable.CloseStrategy closeStrategy) {
            this.strategy = strategy;
            this.closeStrategy = closeStrategy;
        }

        public CreationStrategy getStrategy() {
            return strategy;
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
         * @return a consumer that for each message accepted will create a new actor that will process it, then dies.
         * When appropriate, this is a simple way to run parallel processing, as long as you don't need to know the result
         */
        public <T> Consumer<T> workersAsConsumerCreator(Consumer<T> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return message -> config.newActor(actorLogic).sendMessage(message).sendPoisonPill();
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
         * @return a function that for each message accepted will create a new actor that will process it, then dies.
         * When appropriate, this is a simple way to run parallel processing
         */
        public <T, R> Function<T, CompletableFuture<R>> workersAsFunctionCreator(Function<T, R> actorLogic) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            return message -> {
                Actor<T, R, Void> actor = config.newActorWithReturn(actorLogic);

                CompletableFuture<R> future = actor.sendMessageReturn(message);
                actor.sendPoisonPill();

                return future;
            };
        }

        /**
         * Opens a Java embedded HTTP server (yes, com.sun.net.httpserver.HttpServer), where each request is server by an actor.
         * The workers need to implement the HttpHandler interface.
         * This method is not recommended for a real server, but if you need something simple, it can be useful.
         *
         * @param port HTTP port to open
         * @param workers pairs of context and handler, associating a path to a worker
         * @throws IOException Thrown if there are IO errors
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
         * The workers need to implement Function&lt;HttpExchange, String&gt;, therefore they can just return a string (so they are only useful on very simple cases).
         * This method is not recommended for a real server, but if you need something simple, it can be useful.
         *
         * @param port HTTP port to open
         * @param otherWorkers pairs of context and handler, associating a path to a worker
         * @throws IOException
         */
        public void embeddedHttpServer(int port, Function<HttpExchange, String> rootWorker, HttpStringWorker... otherWorkers) throws IOException {
            HttpStringWorker[] workers = new HttpStringWorker[otherWorkers.length + 1];

            workers[0] = new HttpStringWorker("/", rootWorker);
            System.arraycopy(otherWorkers, 0, workers, 1, otherWorkers.length);

            embeddedHttpServer(port, workers);
        }

        /**
         * Opens a Java embedded HTTP server (yes, com.sun.net.httpserver.HttpServer), where each request is server by an actor.
         * The workers need to implement Function&lt;HttpExchange, String&gt;, therefore they can just return a string (so they are only useful on very simple cases).
         * This method is not recommended for a real server, but if you need something simple, it can be useful.
         *
         * @param port HTTP port to open
         * @param workers pairs of context and handler, associating a path to a worker
         * @throws IOException Thrown in case of I/O errors
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

        /**
         * Creates a named actor that does not receive messages; this is useful to execute code in a remote thread
         */
        public <S> SinkActor<S> sink(String name, S state) {
            NamedStateActorCreator<S> config = named(name).initialState(state);

            return config.newActor(message -> {
            });
        }

        /**
         * Creates an actor that does not receive messages; this is useful to execute code in a remote thread
         */
        public <S> SinkActor<S> sink(S state) {
            NamedStateActorCreator<S> config = anonymous().initialState(state);

            return config.newActor(message -> {
            });
        }

        /**
         * Creates an actor that does not receive messages; this is useful to execute code in a remote thread
         */
        public <S> SinkActor<S> sinkHealing(S state, ActorSystem.AutoHealingSettings autoHealing) {
            NamedStateActorCreator<S> config = threads().anonymous().initialState(state).autoHealing(autoHealing);

            return config.newActor(message -> {
            });
        }

        /**
         * Creates an actor that does not receive messages; this is useful to execute code in a remote thread
         */
        public <S> SinkActor<S> sinkHealing(String name, S state, ActorSystem.AutoHealingSettings autoHealing) {
            NamedStateActorCreator<S> config = threads().named(name).initialState(state).autoHealing(autoHealing);

            return config.newActor(message -> {
            });
        }

        /**
         * Creates a map-reduce local system, using a pool of actors and a single reducer. This is preferred method to do a Map-reduce job.
         *
         * @param params Parameters for the creation of the pool
         * @param mapLogic Logic of the mapper (input -&gt; output)
         * @param reduceLogic Logic of the reducer (accumulator, newValue -&gt; newAccumulator)
         * @param initialReducerState Initial state of the reducer. Note: mappers should be stateless
         * @param <TM> Input type of the mapper
         * @param <RM> Output type of the mapper == Input type of the reducer
         * @param <RR> Output type of the reducer
         * @return a MapReducer
         */
        public <TM, RM, RR> MapReducer<TM, RR> mapReduce(PoolParameters params, Function<TM, RM> mapLogic, BiFunction<RR, RM, RR> reduceLogic, RR initialReducerState) {
            Actor<RM, Void, RR> reducer = ActorSystem.anonymous().strategy(strategy).initialState(initialReducerState).newActor((data, thisActor) ->
                    thisActor.setState(reduceLogic.apply(thisActor.getState() /* Accumulator */, data)));
            PoolActorLeader<TM, Void, Object> poolLeader = anonymous().poolParams(params, null).newPool(data ->
                    reducer.sendMessage(mapLogic.apply(data))
            );

            return new MapReducer<>(poolLeader, reducer);
        }

        /**
         * Creates a map-reduce local system, using one actor per request
         *
         * @param mapLogic Logic of the mapper (input -&gt; output)
         * @param reduceLogic Logic of the reducer (accumulator, newValue -&gt; newAccumulator)
         * @param initialReducerState Initial state of the reducer. Note: mappers should be stateless
         * @param <TM> Input type of the mapper
         * @param <RM> Output type of the mapper == Input type of the reducer
         * @param <RR> Output type of the reducer
         * @return a MapReducer
         */
        public <TM, RM, RR> MapReducer<TM, RR> mapReduce(Function<TM, RM> mapLogic, BiFunction<RR, RM, RR> reduceLogic, RR initialReducerState) {
            ActorSystem.NamedActorCreator c1 = ActorSystem.anonymous();
            NamedStrategyActorCreator c2 = c1.strategy(strategy);
            NamedStateActorCreator<RR> mrCreator = c2.initialState(initialReducerState);

            Actor<RM, Void, RR> reducer = mrCreator.newActor((data, thisActor) ->
                    thisActor.setState(reduceLogic.apply(thisActor.getState() /* Accumulator */, data)));
            AtomicReference<Spawner<TM, Void, Object>> spawnerRef = new AtomicReference<>();
            NamedStateActorCreator<Object> creator = ActorSystem.anonymous().strategy(strategy).initialState(null, state -> spawnerRef.get().finalizer().accept(state));
            Spawner<TM, Void, Object> spawner = new Spawner<>(creator, data -> {
                reducer.sendMessage(mapLogic.apply(data));
                return null;
            });
            spawnerRef.set(spawner);

            return new MapReducer<>(spawner, reducer);
        }

        /**
         * Creates an actor that runs a Runnable, once
         */
        public SinkActorSingleTask<Void> runOnce(Runnable run) {
            SinkActor<Void> actor = sink(null);

            actor.execAsync(() -> {
                run.run();
                actor.askExit();
            });

            return actor;
        }

        /**
         * Creates an actor that runs a RunnableEx, once, discarding any exception
         */
        public <E extends Throwable> SinkActorSingleTask<Void> runOnceSilent(RunnableEx<E> run) {
            return runOnce(Exceptions.silentRunnable(run));
        }

        /**
         * Creates an actor that runs a Consumer, once.
         * The consumer receives the actor itself, which sometimes can be useful (e.g. to check if somebody ask to exit)
         */
        public SinkActorSingleTask<Void> runOnceWithThis(Consumer<SinkActor<Void>> actorLogic) {
            SinkActor<Void> actor = sink(null);

            actor.execAsync(() -> {
                actorLogic.accept(actor);
                actor.askExit();
            });

            return actor;
        }

        /**
         * Creates an actor that runs a Runnable forever, every scheduleMs ms
         */
        public SinkActorSingleTask<Void> schedule(Runnable run, long scheduleMs) {
            return schedule(run, scheduleMs, Long.MAX_VALUE);
        }

        /**
         * Creates an actor that runs a Runnable maxTimes or until somebody asks for exit (this is controlled only in between executions); the actor is scheduled to run every scheduleMs ms
         */
        public SinkActorSingleTask<Void> schedule(Runnable run, long scheduleMs, long maxTimes) {
            Actor<Object, Void, Void> actor = (Actor<Object, Void, Void>) sink((Void) null);

            // Deadlock prevention
            actor.setCloseStrategy(Exitable.CloseStrategy.ASK_EXIT);

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
         * Creates an actor that runs a Runnable with fixed delay; it optionally supports autoHealing
         */
        public SinkActorSingleTask<Void> scheduleWithFixedDelay(Runnable run, long initialDelay, long delay, TimeUnit timeUnit, ActorSystem.AutoHealingSettings autoHealing) {
            Actor<Object, Void, Void> actor = (Actor<Object, Void, Void>) sinkHealing((Void) null, autoHealing);

            // Deadlock prevention
            actor.setCloseStrategy(Exitable.CloseStrategy.ASK_EXIT);

            if (initialDelay > 0)
              actor.execAsync(() -> SystemUtils.sleep(timeUnit.toMillis(initialDelay)));


            AtomicReference<Runnable> runThenWait = new AtomicReference<>();
            AtomicBoolean dummyThreadShouldDie  = new AtomicBoolean();

            runThenWait.set(() -> {
                actor.execAsync(() -> {
                    // Allow shorter timeout than the scheduling time
                    HealRegistry.INSTANCE.remove(actor, Thread.currentThread(), dummyThreadShouldDie);
                    SystemUtils.sleep(timeUnit.toMillis(delay)); });
                actor.execAsync(run);
                actor.execAsync(runThenWait.get());
            });

            actor.execAsync(runThenWait.get());

            return actor;
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         * This method will return after the server socket has been created. If the server socket cannot be created, it will throw an exception.
         * If the server socket can be created once, subsequent exceptions will be logged, and it will try to recreate the server socket as needed.
         * <p>
         * The contract with the workers is that:
         * - Only one message will ever be sent, and the message is the connection
         * - They workers take ownership of the connection, however the acceptor will try to close it anyway. So the worker don't need to close it.
         * - After the connection, a poison pill will be sent, so the actor will die after processing one connection
         *
         * @param port TCP port
         * @param workersLogic Logic of each worker
         * @param timeoutConnectionAcceptanceMs Timeout used to check for new connections. The shorter it is, the more responsive will be the acceptor to exit, but it might have an impact on performance. You could try 1000 as a start
         * @param <S> Type of state
         * @return the acceptor actor
         * @throws IOException this is only thrown if it happens at the beginning, when the ServerSocket is created. Other exceptions will be sent to the console, and the socket will be created again. If the exception is thrown, the actor will also be killed, else it will keep going and retry
         */
        public <S> SinkActorSingleTask<Void> tcpAcceptor(int port, Consumer<Socket> workersLogic, boolean autoCloseSocket, int timeoutConnectionAcceptanceMs) throws IOException {
            Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> workersCreator = (acceptorActor) -> anonymous().initialState((S) null).newActor((Socket socket) -> {
                workersLogic.accept(socket);
                if (autoCloseSocket)
                    SystemUtils.close(socket);
            });

            return tcpAcceptorCore(port, workersCreator, timeoutConnectionAcceptanceMs);
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         * This method will return after the server socket has been created. If the server socket cannot be created, it will throw an exception.
         * If the server socket can be created once, subsequent exceptions will be logged, and it will try to recreate the server socket as needed.
         * <p>
         * The contract with the workers is that:
         * - Only one message will ever be sent, and the message is the connection
         * - They workers take ownership of the connection, however the acceptor will try to close it anyway. So the worker don't need to close it.
         * - After the connection, a poison pill will be sent, so the actor will die after processing one connection
         *
         * @param port TCP port
         * @param workersLogic Logic of each worker
         * @param <S> Type of state
         * @return the acceptor actor
         * @throws IOException this is only thrown if it happens at the beginning, when the ServerSocket is created. Other exceptions will be sent to the console, and the socket will be created again. If the exception is thrown, the actor will also be killed, else it will keep going and retry
         */
        public <S> SinkActorSingleTask<Void> tcpAcceptor(int port, Consumer<Socket> workersLogic, boolean autoCloseSocket) throws IOException {
            return tcpAcceptor(port, workersLogic, autoCloseSocket, 1000);
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         * This method will return after the server socket has been created. If the server socket cannot be created, it will throw an exception.
         * If the server socket can be created once, subsequent exceptions will be logged, and it will try to recreate the server socket as needed.
         * <p>
         * The contract with the workers is that:
         * - Only one message will ever be sent, and the message is the connection
         * - They workers take ownership of the connection, however the acceptor will try to close it anyway. So the worker don't need to close it.
         * - After the connection, a poison pill will be sent, so the actor will die after processing one connection
         *
         * @param port TCP port
         * @param workersBiLogic Logic of each worker
         * @param stateSupplier Optional state supplier
         * @param timeoutConnectionAcceptanceMs Timeout used to check for new connections. The shorter it is, the more responsive will be the acceptor to exit, but it might have an impact on performance. You could try 1000 as a start
         * @param <S> Type of state
         * @return the acceptor actor
         * @throws IOException this is only thrown if it happens at the beginning, when the ServerSocket is created. Other exceptions will be sent to the console, and the socket will be created again. If the exception is thrown, the actor will also be killed, else it will keep going and retry
         */
        public <S> SinkActorSingleTask<Void> tcpAcceptor(int port, BiConsumer<Socket, PartialActor<Socket, S>> workersBiLogic, Function<SinkActorSingleTask<Void>, S> stateSupplier, boolean autoCloseSocket, int timeoutConnectionAcceptanceMs) throws IOException {
            Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> workersCreator = acceptorActor -> anonymous().initialState(stateSupplier == null ? null : stateSupplier.apply(acceptorActor)).newActor((Socket socket, PartialActor<Socket, S> thisActor) -> {
                workersBiLogic.accept(socket, thisActor);
                if (autoCloseSocket)
                    SystemUtils.close(socket);
            });

            return tcpAcceptorCore(port, workersCreator, timeoutConnectionAcceptanceMs);
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         * This method will return after the server socket has been created. If the server socket cannot be created, it will throw an exception.
         * If the server socket can be created once, subsequent exceptions will be logged, and it will try to recreate the server socket as needed.
         * <p>
         * The contract with the workers is that:
         * - Only one message will ever be sent, and the message is the connection
         * - They workers take ownership of the connection, however the acceptor will try to close it anyway. So the worker don't need to close it.
         * - After the connection, a poison pill will be sent, so the actor will die after processing one connection
         *
         * @param port TCP port
         * @param workersBiLogic Logic of each worker
         * @param stateSupplier Optional state supplier
         * @param <S> Type of state
         * @return the acceptor actor
         * @throws IOException this is only thrown if it happens at the beginning, when the ServerSocket is created. Other exceptions will be sent to the console, and the socket will be created again. If the exception is thrown, the actor will also be killed, else it will keep going and retry
         */
        public <S> SinkActorSingleTask<Void> tcpAcceptor(int port, BiConsumer<Socket, PartialActor<Socket, S>> workersBiLogic, Function<SinkActorSingleTask<Void>, S> stateSupplier, boolean autoCloseSocket) throws IOException {
            return tcpAcceptor(port, workersBiLogic, stateSupplier, autoCloseSocket, 1000);
        }

        private <S> SinkActorSingleTask<Void> tcpAcceptorCore(int port, Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> workersCreator, int timeoutAcceptance) throws IOException {
            final CountDownLatch latchSocketCreation = new CountDownLatch(1);
            final AtomicReference<IOException> exception = new AtomicReference<>();

            SinkActorSingleTask<Void> actor = runOnceWithThis(thisActor -> {
                while (!thisActor.isExiting()) {
                    if (debug.get())
                        System.out.println("Accepting TCP connections on port " + port);
                    Optional<ServerSocket> serverSocket = createServerSocketWithTimeout(port, latchSocketCreation, exception, timeoutAcceptance);

                    if (serverSocket.isPresent()) {
                        acceptTcpConnections(workersCreator, thisActor, serverSocket.get());
                    } else
                        SystemUtils.sleep(10); // Slows down a bit in case of continuous exceptions. A circuit breaker would also be an option
                }
                if (debug.get())
                    System.out.println("TCP acceptor actor on port " + port + " shutting down");
            });

            Exceptions.silence(latchSocketCreation::await);

            IOException e = exception.get();

            if (e != null) {
                actor.askExit();

                throw e;
            }

            if (debug.get())
                System.out.println("TCP acceptor listening on port " + port);

            return actor;
        }

        public <S, E extends Throwable> SinkActorSingleTask<Void> tcpAcceptorSilent(int port, ConsumerEx<Socket, E> workersLogic, boolean autoCloseSocket, int timeoutConnectionAcceptanceMs) throws IOException {
            return tcpAcceptor(port, Exceptions.silentConsumer(workersLogic), autoCloseSocket, timeoutConnectionAcceptanceMs);
        }

        public <S> SinkActorSingleTask<Void> forwardLocal(int tcpSourcePort, int tcpDestPort, boolean echoIn, boolean echoOut, int timeoutConnectionAcceptanceMs) throws IOException {
            return forwardRemote(tcpSourcePort, InetAddress.getLocalHost(), tcpDestPort, echoIn, echoOut, timeoutConnectionAcceptanceMs);
        }

        public <S> SinkActorSingleTask<Void> forwardRemote(int tcpSourcePort, String remoteHost, int tcpRemotePort, boolean echoIn, boolean echoOut, int timeoutConnectionAcceptanceMs) throws IOException {
            return forwardRemote(tcpSourcePort, InetAddress.getByName(remoteHost), tcpRemotePort, echoIn, echoOut, timeoutConnectionAcceptanceMs);
        }

        public <S> SinkActorSingleTask<Void> forwardRemote(int tcpLocalPort, InetAddress remoteHost, int tcpRemotePort, boolean echoIn, boolean echoOut, int timeoutConnectionAcceptanceMs) throws IOException {
            return tcpAcceptor(tcpLocalPort, localSocket -> Exceptions.silence(() -> forward(localSocket, new Socket(remoteHost, tcpRemotePort), echoIn, echoOut)), false, timeoutConnectionAcceptanceMs);
        }

        private void forward(Socket localSocket, Socket remoteSocket, boolean echoIn, boolean echoOut) {
            runOnce(() ->
                    forwardOneWay(localSocket, remoteSocket, echoIn ? "IN FROM " + localSocket.getLocalPort() + "->" + ((InetSocketAddress) remoteSocket.getRemoteSocketAddress()).getPort() : null));

            forwardOneWay(remoteSocket, localSocket, echoOut ? "OUT TO " + ((InetSocketAddress) remoteSocket.getRemoteSocketAddress()).getPort() + "->" + localSocket.getLocalPort() : null);
        }

        private static void forwardOneWay(Socket localSocket, Socket remoteSocket, String debugLabel) {
            Exceptions.silence(() -> {
                SystemUtils.transferStream(localSocket.getInputStream(), remoteSocket.getOutputStream(), debugLabel);
            }, () -> {
                SystemUtils.close(localSocket);
                SystemUtils.close(remoteSocket);
                //if (debugLabel != null)
                //  System.out.println("Socket closed " + debugLabel);
            });
        }

        private <S> void acceptTcpConnections(Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> workersCreator, SinkActor<Void> acceptorActor, ServerSocket serverSocket) {
            Exceptions.logShort(() -> {
                while (!acceptorActor.isExiting()) {
                    try {
                        Socket clientSocket = serverSocket.accept();

                        // The socket can be null by design; as consequence of the SO_TIMEOUT; this will give the actor a chance to exit even if there are no clients connecting
                        if (clientSocket == null)
                            continue;

                        Actor<Socket, Void, S> worker = workersCreator.apply(acceptorActor);

                        // Lose ownership of the socket, it will be managed by the worker
                        worker.sendMessage(clientSocket);
                        worker.sendPoisonPill();
                    } catch (SocketTimeoutException e) {
                        /* By design */
                    }
                }
            }, () -> SystemUtils.close(serverSocket));
        }

        private Optional<ServerSocket> createServerSocketWithTimeout(int port, CountDownLatch latchSocketCreation, AtomicReference<IOException> exception, int timeoutAccept) {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(timeoutAccept);

                return Optional.of(serverSocket);
            } catch (IOException e) {
                exception.set(e); // Important exception: it was not possible to create the server socket

                return Optional.empty();
            } finally {
                latchSocketCreation.countDown();
            }
        }

        /**
         * Create a mini chat system:
         *
         * @param actorsPrefix prefix assigned to the user actors (e.g. CHAT|), to prevent sending messages to other actors
         * @param persistenceLogic What to do to persist the messages
         * - T is the type of message, and it might contain info like the sender, the time and the message;
         * - The BiConsumer accept the name of the user and the message
         * - persistenceLogic, is present, can record the message on a DB; this will be done asynchronously
         * - The message will be also sent to an actor called actorsPrefix+userName (from BiConsumer); but if the actor is not present, it will be dropped. The actor should retrieve the messages from the DB
         * <p>`
         * To receive answers, messages need to be sent to the sender using the BiConsumer
         */
        public <T> BiConsumer<String, T> chatSystem(String actorsPrefix, Consumer<T> persistenceLogic) {
            Consumer<T> persistActor = persistenceLogic == null || persistenceLogic instanceof PartialActor ? persistenceLogic : workersAsConsumerCreator(persistenceLogic);

            return (user, message) -> {
                String userActorName = actorsPrefix == null ? user : actorsPrefix + user;

                ActorSystem.sendMessage(userActorName, message, false); // Online chat
                if (persistActor != null)
                    persistActor.accept(message); // Persisted on the DB
            };
        }

        /**
         * Creates an actor that pipeline its result to another actor
         * Pipelined actor are anonymous to avoid confusion and prevent code from running on the actor
         */
        public <T, T2, R> MessageOnlyActor<T, R, Void> pipelineTo(Function<T, T2> workerBiLogic, MessageOnlyActor<T2, R, ?> targetActor) {
            Actor<T, R, Void> actor = ActorSystem.anonymous().strategy(strategy).newActorWithReturn(workerBiLogic.andThen(targetActor));

            return actor.closeOnExit(targetActor::sendPoisonPill);
        }

        /**
         * Process messages in batch, after grouping by the key and counting how many messages are received.
         * e.g. ['a', 'a', 'b', 'c', 'a'] would generate ['a':3, 'b':1, 'c':1]
         *
         * @param batchProcessor Object that can process the map of messages:num collected
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process)
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param skipTimeWithoutMessages It applies only if there are no messages. If true, the first message will reset the timeout of the batch (e.g. it can buffer the message for a full batchMs ms, even if it has been some time before the last batch)
         * @param <T> Type of messages
         * @return Actor
         */
        public <T> BaseActor<T, Void, Void> batchProcessGroupBy(Consumer<Map<T, Long>> batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            Map<T, Long> map = new HashMap<>();

            return batchProcess(message -> {
                map.merge(message, 1L, Long::sum);
                return map.size();
            }, () -> {
                if (!map.isEmpty())
                    batchProcessor.accept(map);
                map.clear();
            }, batchMaxSize, batchMs, precisionMs, skipTimeWithoutMessages);
        }

        /**
         * Process messages in batch, after putting them on a list
         *
         * @param batchProcessor Object that can process the list of messages collected
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process)
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param skipTimeWithoutMessages It applies only if there are no messages. If true, the first message will reset the timeout of the batch (e.g. it can buffer the message for a full batchMs ms, even if it has been some time before the last batch)
         * @param <T> Type of messages
         * @return Actor
         */
        public <T> BaseActor<T, Void, Void> batchProcessList(Consumer<List<T>> batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            List<T> list = new ArrayList<>();

            return batchProcess(message -> {
                list.add(message);
                return list.size();
            }, () -> {
                if (!list.isEmpty())
                    batchProcessor.accept(list);
                list.clear();
            }, batchMaxSize, batchMs, precisionMs, skipTimeWithoutMessages);
        }

        /**
         * Process messages in batch
         *
         * @param itemProcessor Object that can process a single message and collect it in a batch; it returns the number of elements in the batch (which could be different than the number of messages processed if they can be dropped or aggregated)
         * @param batchProcessor Object that can process the group of messages collected by the itemProcessor
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process)
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param <T> Type of messages
         * @return an actor that can process messages in batches
         */
        public <T> BaseActor<T, Void, Void> batchProcess(Function<T, Integer> itemProcessor, Runnable batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            BaseActor<T, Void, Void> actor = ActorUtils.initRef(ref -> {
                return new CustomActor<T, Void, Void>(new FibryQueue<>(), state -> Exceptions.silence(batchProcessor::run), precisionMs) {
                    long lastBatchSentTime = 0;
                    int numMessages;

                    @Override
                    protected void onMessage(T message) {
                        if (skipTimeWithoutMessages && numMessages == 0)
                            lastBatchSentTime = System.currentTimeMillis();

                        numMessages = itemProcessor.apply(message);

                        processBatchIfReady();
                    }

                    @Override
                    protected void onNoMessages() {
                        if (lastBatchSentTime == 0)
                            lastBatchSentTime = System.currentTimeMillis();
                        processBatchIfReady();
                    }

                    private void processBatchIfReady() {
                        long now = System.currentTimeMillis();

                        if (lastBatchSentTime == 0)
                            lastBatchSentTime = now;

                        if (numMessages >= batchMaxSize || now >= lastBatchSentTime + batchMs) {
                            lastBatchSentTime = now;

                            if (numMessages > 0)
                                batchProcessor.run();
                        }
                    }
                };
            });

            return strategy.start(actor);
        }

        private NamedStrategyActorCreator anonymous() {
            return ActorSystem.anonymous().strategy(strategy, closeStrategy);
        }

        private NamedStrategyActorCreator named(String name) {
            return ActorSystem.named(name).strategy(strategy, closeStrategy);
        }
    }

    /**
     * Actors will be created using threads
     */
    public static NamedStereotype threads() {
        return new NamedStereotype(THREAD, null);
    }

    /**
     * Actors will be created using fibers if available, else with threads
     */
    public static NamedStereotype auto() {
        return new NamedStereotype(AUTO, null);
    }

    /**
     * Actors will be created using fibers
     */
    public static NamedStereotype fibers() {
        return new NamedStereotype(FIBER, null);
    }

    /**
     * Actors will be created using the default strategy, set in ActorSystem
     */
    public static NamedStereotype def() {
        return new NamedStereotype(ActorSystem.defaultStrategy, null);
    }

    /**
     * Actors will be created using threads
     *
     * @param closeStrategy What to do when close() is called
     * @return an object that can create actors with the requested characteristics
     */
    public static NamedStereotype threads(Exitable.CloseStrategy closeStrategy) {
        return new NamedStereotype(THREAD, closeStrategy);
    }

    /**
     * Actors will be created using fibers if available, else with threads
     *
     * @param closeStrategy What to do when close() is called
     * @return an object that can create actors with the requested characteristics
     */
    public static NamedStereotype auto(Exitable.CloseStrategy closeStrategy) {
        return new NamedStereotype(AUTO, closeStrategy);
    }

    /**
     * Actors will be created using fibers
     *
     * @param closeStrategy What to do when close() is called
     * @return an object that can create actors with the requested characteristics
     */
    public static NamedStereotype fibers(Exitable.CloseStrategy closeStrategy) {
        return new NamedStereotype(FIBER, closeStrategy);
    }

    /**
     * Actors will be created using the default strategy, set in ActorSystem
     *
     * @param closeStrategy What to do when close() is called
     * @return an object that can create actors with the requested characteristics
     */
    public static NamedStereotype def(Exitable.CloseStrategy closeStrategy) {
        return new NamedStereotype(ActorSystem.defaultStrategy, closeStrategy);
    }

    public static void setDebug(boolean activateDebug) {
        debug.set(activateDebug);
    }
}
