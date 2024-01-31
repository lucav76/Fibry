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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ServerSocketChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static eu.lucaventuri.fibry.CreationStrategy.*;

/** Utility class used for batch processing */
class SingleTracker<T> {
    long lastBatchSentTime = 0;
    int lastNumMessages = 0;

    boolean readyToProcess(long now, long batchMaxSize, long batchMs) {
        if (lastBatchSentTime == 0)
            lastBatchSentTime = now;

        if (lastNumMessages >= batchMaxSize || now >= lastBatchSentTime + batchMs) {
            lastBatchSentTime = now;

            if (lastNumMessages > 0)
                return true;
        }

        return false;
    }

    boolean isEmpty() {
        return lastNumMessages == 0;
    }

    void setLastBatchSentTime(long time) {
        lastBatchSentTime = time;
    }

    void setLastNumMessages(int numMessages) {
        this.lastNumMessages = numMessages;
    }
}

/** Utility class used for batch processing */
class MultiTracker<T extends MergeableParallelBatches> {
    Map<String, SingleTracker<T>> mapTrackers = new ConcurrentHashMap<>();

    private SingleTracker<T> tracker(T message) {
        assert message != null;

        return mapTrackers.computeIfAbsent(message.getKey(), key -> new SingleTracker());
    }

    boolean readyToProcess(T message, long now, long batchMaxSize, long batchMs) {
        if (message == null)
            return false;

        return tracker(message).readyToProcess(now, batchMaxSize, batchMs);
    }

    boolean isEmpty(T message) {
        if (message == null)
            return false;

        return tracker(message).isEmpty();
    }

    void setLastBatchSentTime(T message, long time) {
        if (message == null)
            return;

        tracker(message).setLastBatchSentTime(time);
    }

    void setLastNumMessages(T message, int numMessages) {
        if (message == null)
            return;

        tracker(message).setLastNumMessages(numMessages);
    }

    void visitTrackers(BiConsumer<String, SingleTracker> visitor) {
        for (Map.Entry<String, SingleTracker<T>> entry : mapTrackers.entrySet())
            visitor.accept(entry.getKey(), entry.getValue());
    }
}

/**
 * Class providing functions for common use cases
 */
public class Stereotypes {
    private static AtomicBoolean debug = new AtomicBoolean(false);
    private static AtomicInteger defaultHttpBacklog = new AtomicInteger(100);

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

    public static class HttpUrlDownload<T> {
        public final URI uri;
        public final Consumer<HttpResult<T>> actorResponse;
        private final int numRetries;
        private final int secondsBeforeRetry;

        public HttpUrlDownload(URI uri, Consumer<HttpResult<T>> actorResponse, int numRetries, int secondsBeforeRetry) {
            this.uri = uri;
            this.actorResponse = actorResponse;
            this.numRetries = numRetries;
            this.secondsBeforeRetry = secondsBeforeRetry;
        }

        public HttpUrlDownload(String uri, Consumer<HttpResult<T>> actorResponse, int numRetries, int secondsBeforeRetry) {
            this(URI.create(uri), actorResponse, numRetries, secondsBeforeRetry);
        }

        public HttpUrlDownload<T> decreaseRetry() {
            assert numRetries > 0;

            return new HttpUrlDownload<>(uri, actorResponse, numRetries, secondsBeforeRetry);
        }

        HttpRequest toGetRequest() {
            return HttpRequest.newBuilder().uri(uri).GET().build();
        }
    }

    public static class HttpResult<T> {
        public HttpResult(HttpUrlDownload<T> downloadRequest, HttpResponse<T> response, Throwable exception, Reason reason) {
            this.downloadRequest = downloadRequest;
            this.response = response;
            this.exception = exception;
            this.reason = reason;
        }

        public enum Reason {OK, /** Failed after retries */ FAILED, EXCEPTION}

        public final HttpUrlDownload<T> downloadRequest;
        public final HttpResponse<T> response;
        public final Throwable exception;
        public final Reason reason;
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
            HttpServer server = HttpServer.create(new InetSocketAddress(port), defaultHttpBacklog.get());

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
        public HttpServer embeddedHttpServer(int port, HttpStringWorker... workers) throws IOException {
            //NamedStateActorCreator<Void> config = anonymous().initialState(null);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), defaultHttpBacklog.get());
            server.setExecutor(FIBER.newExecutor());

            for (HttpStringWorker worker : workers) {
                server.createContext(worker.context, exchange -> {
                    try {
                        String answer = worker.worker.apply(exchange);

                        if (answer == null) {
                            answer = "404 - File not found";
                            exchange.sendResponseHeaders(404, answer.getBytes().length);//response code and length
                        } else {
                            exchange.sendResponseHeaders(200, answer.getBytes().length);//response code and length
                        }
                        OutputStream os = exchange.getResponseBody();
                        os.write(answer.getBytes());
                        os.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        String answer = "Internal Server error: " + e.toString();
                        exchange.sendResponseHeaders(500, answer.getBytes().length);//response code and length
                        OutputStream os = exchange.getResponseBody();
                        os.write(answer.getBytes());
                        os.close();
                    }
                });
            }
            server.start();

            return server;
        }

        /**
         * Creates a UDP server on the specified port and forwards the packets to a consumer.
         * It is recommended to use as a consumer an actor with a limited queue, to reduce memory consumption in case of problems *
         */
        public SinkActorSingleTask<Void> udpServer(int port, Consumer<DatagramPacket> consumer) throws SocketException {
            var socket = new DatagramSocket(port);

            return Stereotypes.def().runOnceWithThis(thisActor -> {
                try {
                    while (!thisActor.isExiting()) {
                        final byte[] buf = new byte[65536];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        try {
                            socket.receive(packet);
                            consumer.accept(packet);
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    }
                } finally {
                    SystemUtils.close(socket);
                }
            });
        }

        /**
         * Creates a UDP server on the specified port and forwards the packets as strings to a consumer.
         * It is recommended to use as a consumer an actor with a limited queue, to reduce memory consumption in case of problems *
         *
         * @return
         */
        public SinkActorSingleTask<Void> udpServerString(int port, Consumer<String> consumer) throws SocketException {
            return udpServer(port, datagram -> consumer.accept(new String(datagram.getData(), 0, datagram.getLength())));
        }

        /**
         * Creates a UDP Multicast server listening to the specified multicast group and forwards the packets as strings to a consumer.
         * It is recommended to use as a consumer an actor with a limited queue, to reduce memory consumption in case of problems
         */
        public SinkActorSingleTask<Void> udpMulticastServer(InetAddress multicastGroup, int multicastPort, Consumer<DatagramPacket> consumer) throws IOException {
            var socket = new MulticastSocket(multicastPort);

            socket.joinGroup(multicastGroup);

            return Stereotypes.def().runOnceWithThis(thisActor -> {
                try {
                    while (!thisActor.isExiting()) {
                        final byte[] buf = new byte[65536];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        try {
                            socket.receive(packet);
                            consumer.accept(packet);
                        } catch (IOException e) {
                            System.err.println(e);
                        }
                    }
                } finally {
                    Exceptions.log(() -> socket.leaveGroup(multicastGroup));
                    SystemUtils.close(socket);
                }
            });
        }

        /**
         * Creates a UDP Multicast server listening to the specified multicast group and forwards the packets as strings to a consumer.
         * It is recommended to use as a consumer an actor with a limited queue, to reduce memory consumption in case of problems *
         *
         * @return
         */
        public SinkActorSingleTask<Void> udpMulticastServerString(InetAddress multicastGroup, int port, Consumer<String> consumer) throws IOException {
            return udpMulticastServer(multicastGroup, port, datagram -> consumer.accept(new String(datagram.getData(), 0, datagram.getLength())));
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
            NamedStateActorCreator<Object> creator = ActorSystem.anonymous().strategy(strategy).initialState(null, null, state -> spawnerRef.get().finalizer().accept(state));
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
            Actor<Object, Void, Void> actor = createActorForScheduling(initialDelay, timeUnit, autoHealing);
            AtomicReference<Runnable> runThenWait = new AtomicReference<>();

            runThenWait.set(() -> {
                // As we are inside the event loop of the actor, we can only call execAsync
                actor.execAsync(run);
                // When this runs, run.run() has been executed
                actor.execAsyncNoHealing(() -> SystemUtils.sleep(timeUnit.toMillis(delay)));
                actor.execAsync(runThenWait.get());
            });

            actor.execAsync(runThenWait.get());

            return actor;
        }

        /**
         * Creates an actor that runs a Runnable with fixed delay; it optionally supports autoHealing
         */
        public SinkActorSingleTask<Void> scheduleWithFixedRate(Runnable run, long initialDelay, long delay, TimeUnit timeUnit, ActorSystem.AutoHealingSettings autoHealing) {
            Actor<Object, Void, Void> actor = createActorForScheduling(initialDelay, timeUnit, autoHealing);
            AtomicReference<Runnable> runThenWait = new AtomicReference<>();
            AtomicLong nextRun = new AtomicLong(System.currentTimeMillis() + timeUnit.toMillis(delay));

            runThenWait.set(() -> {
                // TODO: It will not work well if the execution time exceeds the delay (e.g. if a task scheduled once an hour uses more than one hour to run)
                // As we are inside the event loop of the actor, we can only call execAsync
                actor.execAsync(run);
                actor.execAsyncNoHealing(() -> {
                    // When this runs, run.run() has been executed

                    long wait = nextRun.get() - System.currentTimeMillis();

                    // TODO: in case of overlap, should we skip? Should we have a policy for that? CHeck Java?
                    nextRun.set(nextRun.get() + timeUnit.toMillis(delay));
                    if (wait > 0)
                        SystemUtils.sleep(timeUnit.toMillis(wait)); }
                );
                actor.execAsync(runThenWait.get());
            });

            actor.execAsync(runThenWait.get());

            return actor;
        }

        private Actor<Object, Void, Void> createActorForScheduling(long initialDelay, TimeUnit timeUnit, ActorSystem.AutoHealingSettings autoHealing) {
            Actor<Object, Void, Void> actor = (Actor<Object, Void, Void>) sinkHealing((Void) null, autoHealing);

            // Deadlock prevention
            actor.setCloseStrategy(Exitable.CloseStrategy.ASK_EXIT);

            if (initialDelay > 0)
                actor.execAsync(() -> SystemUtils.sleep(timeUnit.toMillis(initialDelay)));
            return actor;
        }

        /**
         * Creates an actor that can accept new TCP connections on the specified port and spawn a new actor for each connection.
         * This method will return after the server socket has been created. If the server socket cannot be created, it will throw an exception.
         * If the server socket can be created once, subsequent exceptions will be logged, and it will try to recreate the server socket as needed.
         * <p>
         * The contract with the workers is that:
         * - Only one message will ever be sent, and the message is the connection
         * - The workers take ownership of the connection, however the acceptor will try to close it anyway. So the worker don't need to close it.
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
            return tcpAcceptorCore(port, getTcpAcceptorWorkerCreator(workersLogic, autoCloseSocket), timeoutConnectionAcceptanceMs, false);
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

            return tcpAcceptorCore(port, workersCreator, timeoutConnectionAcceptanceMs, false);
        }

        /** Version of tcpAcceptorFromChannel() using socket channels, which is required by TcpChannel */
        // FIXME: Verify if it is really necessary to split sockets and socket channels, or of it is just a bug
        public <S> SinkActorSingleTask<Void> tcpAcceptorFromChannel(int port, Consumer<Socket> workersLogic, boolean autoCloseSocket, int timeoutConnectionAcceptanceMs) throws IOException {
            return tcpAcceptorCore(port, getTcpAcceptorWorkerCreator(workersLogic, autoCloseSocket), timeoutConnectionAcceptanceMs, true);
        }

        private <S> Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> getTcpAcceptorWorkerCreator(Consumer<Socket> workersLogic, boolean autoCloseSocket) {
            return (acceptorActor) -> anonymous().initialState((S) null).newActor((Socket socket) -> {
                assert socket != null;

                try {
                    workersLogic.accept(socket);
                } finally {
                    if (autoCloseSocket)
                        SystemUtils.close(socket);
                }
            });
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

        private <S> SinkActorSingleTask<Void> tcpAcceptorCore(int port, Function<SinkActorSingleTask<Void>, Actor<Socket, Void, S>> workersCreator, int timeoutAcceptance, boolean fromSocketChannel) throws IOException {
            final CountDownLatch latchSocketCreation = new CountDownLatch(1);
            final AtomicReference<IOException> exception = new AtomicReference<>();

            SinkActorSingleTask<Void> actor = runOnceWithThis(thisActor -> {
                while (!thisActor.isExiting()) {
                    if (debug.get())
                        System.out.println("Accepting TCP connections on port " + port);
                    Optional<ServerSocket> serverSocket = createServerSocketWithTimeout(port, latchSocketCreation, exception, timeoutAcceptance, fromSocketChannel);

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

        private Optional<ServerSocket> createServerSocketWithTimeout(int port, CountDownLatch latchSocketCreation, AtomicReference<IOException> exception, int timeoutAccept, boolean fromSocketChannel) {
            try {
                ServerSocket serverSocket = fromSocketChannel ? ServerSocketChannel.open().bind(new InetSocketAddress(port)).socket() : new ServerSocket(port);
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
         * Creates an actor that:
         * - Downloads a specified URL
         * - Send the response to another actor
         * - In case of error, it retries
         */
        public Actor<HttpUrlDownload<String>, Void, Void> downloader(Scheduler scheduler, int timeoutSeconds, boolean sendLastError) {
            var refActor = new AtomicReference<Actor<HttpUrlDownload<String>, Void, Void>>();
            HttpClient client = HttpUtil.getHttpClient(timeoutSeconds);

            refActor.set(ActorSystem.anonymous().strategy(strategy).newActor(download -> {
                client.sendAsync(download.toGetRequest(), HttpResponse.BodyHandlers.ofString()).whenComplete((response, ex) -> {
                    manageHttpClientResponse(scheduler, sendLastError, refActor, download, response, ex);
                });
            }));

            return refActor.get();
        }

        public Actor<HttpUrlDownload<String>, Void, Void> downloader() {
            var scheduler = new Scheduler();
            var actor = downloader(scheduler, 30, true);

            actor.closeOnExit(scheduler);

            return actor;
        }

        /**
         * Creates an actor that:
         * - Downloads a specified URL
         * - Send the response to another actor
         * - In case of error, it retries
         */
        public Actor<HttpUrlDownload<byte[]>, Void, Void> binaryDownloader(Scheduler scheduler, int timeoutSeconds, boolean sendLastError) {
            var refActor = new AtomicReference<Actor<HttpUrlDownload<byte[]>, Void, Void>>();
            HttpClient client = HttpUtil.getHttpClient(timeoutSeconds);

            refActor.set(ActorSystem.anonymous().strategy(strategy).newActor(download -> {
                client.sendAsync(download.toGetRequest(), HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, ex) -> {
                    manageHttpClientResponse(scheduler, sendLastError, refActor, download, response, ex);
                });
            }));

            return refActor.get();
        }

        public Actor<HttpUrlDownload<byte[]>, Void, Void> binaryDownloader() {
            return binaryDownloader(new Scheduler(), 30, true);
        }

        public <T> BaseActor<T, Void, Void> rateLimited(Consumer<T> actorLogic, int msBetweenCalls) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            Consumer<T> rateLimitedLogic = wrapWithRateLimiting(actorLogic, msBetweenCalls);

            return config.newActor(rateLimitedLogic);
        }

        public <T, R> BaseActor<T, R, Void> rateLimitedReturn(Function<T, R> actorLogic, int msBetweenCalls) {
            NamedStateActorCreator<Void> config = anonymous().initialState(null);

            Function<T, R> rateLimitedLogic = wrapWithRateLimitingReturn(actorLogic, msBetweenCalls);

            return config.newActorWithReturn(rateLimitedLogic);
        }

        /**
         * Process messages in batches, after grouping by the key and counting how many messages are received.
         * e.g. ['a', 'a', 'b', 'c', 'a'] would generate ['a':3, 'b':1, 'c':1]
         *
         * @param batchProcessor Object that can process the map of messages:num collected
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process);
         * this is the size of the groupBy labels, not of the messages grouped, so the size of ['a':3, 'b':1, 'c':1] is 3, not 5.
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
         * Process messages in batches, after putting them on a list
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
         * Process messages in batches, after merging them when possible;
         *
         * @param batchProcessor Object that can process the list of messages collected
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process);
         * this is the number of merged categories, not of the messages arrived, so the size of ['a':3, 'b':1, 'c':1] is 3, not 5.
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param skipTimeWithoutMessages It applies only if there are no messages. If true, the first message will reset the timeout of the batch (e.g. it can buffer the message for a full batchMs ms, even if it has been some time before the last batch)
         * @param <T> Type of messages
         * @return Actor
         */
        public <T extends Mergeable> BaseActor<T, Void, Void> batchProcessMerge(Consumer<Map<String, T>> batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            Map<String, T> map = new HashMap<>();

            return batchProcess(message -> {
                map.merge(message.getKey(), message, (agg, value) -> (T) agg.mergeWith(value));
                return map.size();
            }, () -> {
                if (!map.isEmpty())
                    batchProcessor.accept(map);

                // Signals (to every message) that the batch has been processed
                for (Mergeable m : map.values())
                    m.signalMessageProcessed();

                map.clear();
            }, batchMaxSize, batchMs, precisionMs, skipTimeWithoutMessages);
        }

        /**
         * Process messages in batches, after merging them when possible; the key difference with batchProcessMerge() is that we
         * Do not process all the batches when a certain number of batches is reached, but we process the single parallel batch composed by a single MergeableParallelBatches
         *
         * @param batchProcessor Object that can process the list of messages collected
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process);
         * this is the number of merged categories, not of the messages arrived, so the size of ['a':3, 'b':1, 'c':1] is 3, not 5.
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param skipTimeWithoutMessages It applies only if there are no messages. If true, the first message will reset the timeout of the batch (e.g. it can buffer the message for a full batchMs ms, even if it has been some time before the last batch)
         * @param <T> Type of messages
         * @return Actor
         */
        public <T extends MergeableParallelBatches> BaseActor<T, Void, Void> batchProcessMergeParallelBatches(Consumer<T> batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            Map<String, T> map = new HashMap<>();

            return batchProcessParallelBatch(new MultiTracker<T>(), message -> {
                AtomicInteger size = new AtomicInteger();

                map.merge(message.getKey(), message, (agg, value) -> {
                    T result = (T) agg.mergeWith(value);

                    size.set(result.elementsMerged());

                    return result;
                });
                return size.get();
            }, messageKey -> {
                T mergedMessage = map.get(messageKey);

                if (mergedMessage != null) {
                    map.remove(mergedMessage.getKey());
                    batchProcessor.accept(mergedMessage);
                    mergedMessage.signalMessageProcessed();
                }
            }, () -> {
                for (Map.Entry<String, T> entry : map.entrySet()) {
                    batchProcessor.accept(entry.getValue());
                    entry.getValue().signalMessageProcessed();
                }
                map.clear();
            }, batchMaxSize, batchMs, precisionMs, skipTimeWithoutMessages);
        }

        /**
         * Process messages in batches. Note: itemMerger and batchProcessor are working together
         *
         * @param itemMerger Object that can process a single message and collect it in a batch; it returns the number of elements in the batch (which could be different than the number of messages processed if they can be dropped or aggregated)
         * @param batchProcessor Object that can process the group of messages collected by the itemMerger
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process)
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param <T> Type of messages
         * @return an actor that can process messages in batches
         */
        public <T> BaseActor<T, Void, Void> batchProcess(Function<T, Integer> itemMerger, Runnable batchProcessor, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            SingleTracker tracker = new SingleTracker();

            BaseActor<T, Void, Void> actor = ActorUtils.initRef(ref -> {
                return new CustomActor<T, Void, Void>(new FibryQueue<>(), null, state -> Exceptions.silence(batchProcessor::run), precisionMs) {
                    @Override
                    protected void onMessage(T message) {
                        if (skipTimeWithoutMessages && tracker.isEmpty())
                            tracker.setLastBatchSentTime(System.currentTimeMillis());

                        tracker.setLastNumMessages(itemMerger.apply(message));

                        processBatchIfReady();
                    }

                    @Override
                    protected void onNoMessages() {
                        processBatchIfReady();
                    }

                    private void processBatchIfReady() {
                        if (tracker.readyToProcess(System.currentTimeMillis(), batchMaxSize, batchMs))
                            batchProcessor.run();
                    }
                };
            });

            return strategy.start(actor);
        }

        /**
         * Process messages in batches. Note: itemMerger and batchProcessorByKey are working together
         *
         * @param itemMerger Object that can process a single message and collect it in a batch; it returns the number of elements in the batch (which could be different than the number of messages processed if they can be dropped or aggregated)
         * @param batchProcessorByKey Object that can process the group of messages collected by the itemMerger, for a single key
         * @param batchMaxSize Maximum size of the batch (when reached, the batch will be process)
         * @param batchMs Maximum ms to wait before processing a batch, even if batchMaxSize has not been reached
         * @param precisionMs Tentative precision (e.g. the batch is tentatively processed between batchMs and batchMs+precisionMs milliseconds); decreasing this value will increase precision but it might affect negatively a bit the performance
         * @param <T> Type of messages
         * @return an actor that can process messages in batches
         */
        private <T extends MergeableParallelBatches> BaseActor<T, Void, Void> batchProcessParallelBatch(MultiTracker<T> tracker, Function<T, Integer> itemMerger, Consumer<String> batchProcessorByKey, Runnable batchProcessorOnShutdown, int batchMaxSize, int batchMs, int precisionMs, boolean skipTimeWithoutMessages) {
            BaseActor<T, Void, Void> actor = ActorUtils.initRef(ref -> new CustomActor<T, Void, Void>(new FibryQueue<>(), null, state -> Exceptions.silence(batchProcessorOnShutdown::run), precisionMs) {
                @Override
                protected void onMessage(T message) {
                    if (skipTimeWithoutMessages && tracker.isEmpty(message))
                        tracker.setLastBatchSentTime(message, System.currentTimeMillis());

                    tracker.setLastNumMessages(message, itemMerger.apply(message));

                    processBatchIfReady(message);
                }

                @Override
                protected void onNoMessages() {
                    tracker.visitTrackers((key, singleTracker) -> {
                        if (singleTracker.readyToProcess(System.currentTimeMillis(), batchMaxSize, batchMs))
                            batchProcessorByKey.accept(key);
                    });
                }

                private void processBatchIfReady(T message) {
                    if (tracker.readyToProcess(message, System.currentTimeMillis(), batchMaxSize, batchMs))
                        batchProcessorByKey.accept(message.getKey());
                }
            });

            return strategy.start(actor);
        }

        /** Creates an object that can postpone messages */
        public Scheduler scheduler() {
            return new Scheduler();
        }

        /** Creates an object that can postpone messages */
        public Scheduler scheduler(int waitMs) {
            return new Scheduler(waitMs);
        }

        private NamedStrategyActorCreator anonymous() {
            return ActorSystem.anonymous().strategy(strategy, closeStrategy);
        }

        private NamedStrategyActorCreator named(String name) {
            return ActorSystem.named(name).strategy(strategy, closeStrategy);
        }
    }

    public static <T> Consumer<T> wrapWithRateLimiting(Consumer<T> actorLogic, int msBetweenCalls) {
        return value -> {
            long start = System.currentTimeMillis();

            actorLogic.accept(value);

            long end = System.currentTimeMillis() - start;

            if (msBetweenCalls > (end - start))
                SystemUtils.sleep(msBetweenCalls - (end - start));
        };
    }

    public static <T, R> Function<T, R> wrapWithRateLimitingReturn(Function<T, R> actorLogic, int msBetweenCalls) {
        return value -> {
            long start = System.currentTimeMillis();

            R result = actorLogic.apply(value);

            long end = System.currentTimeMillis();

            if (msBetweenCalls > (end - start)) {
                SystemUtils.sleep(msBetweenCalls - (end - start));
            }

            return result;
        };
    }

    private static <T> void manageHttpClientResponse(Scheduler scheduler, boolean sendLastError, AtomicReference<Actor<HttpUrlDownload<T>, Void, Void>> refActor, HttpUrlDownload<T> download, HttpResponse<T> response, Throwable ex) {
        if (ex != null) {
            if (download.numRetries > 0)
                scheduler.scheduleOnce(refActor.get(), download.decreaseRetry(), download.secondsBeforeRetry, TimeUnit.SECONDS);
            else
                download.actorResponse.accept(new HttpResult<>(download, null, ex, HttpResult.Reason.EXCEPTION));

            return;
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300)
            download.actorResponse.accept(new HttpResult<>(download, response, null, HttpResult.Reason.OK));
        else {
            if (download.numRetries > 0)
                scheduler.scheduleOnce(refActor.get(), download.decreaseRetry(), download.secondsBeforeRetry, TimeUnit.SECONDS);
            else if (sendLastError)
                download.actorResponse.accept(new HttpResult<>(download, response, null, HttpResult.Reason.FAILED));
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

    public static void setDefaultHttpBacklog(int defaultHttpBacklog) {
        Stereotypes.defaultHttpBacklog.set(defaultHttpBacklog);
    }
}
