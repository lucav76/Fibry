package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.concurrent.SignalingSingleConsumer;
import eu.lucaventuri.fibry.receipts.CompletableReceipt;
import eu.lucaventuri.fibry.receipts.ImmutableReceipt;
import eu.lucaventuri.fibry.receipts.ReceiptFactory;
import eu.lucaventuri.functional.Either3;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ActorUtils {
    private ActorUtils() { /* Static methods only */}
    static final AtomicInteger num = new AtomicInteger(0);

    static <T, R, S> void sendMessage(MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        queue.add(Either3.right(message));
    }

    static <T, R, S> CompletableFuture<R> sendMessageReturn(MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        MessageWithAnswer<T, R> mwr = new MessageWithAnswer<>(message);
        queue.add(Either3.other(mwr));

        return mwr.answer;
    }

    static <T, R, S> CompletableReceipt<R> sendMessageReceipt(ReceiptFactory factory, MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) throws IOException {
        return sendMessageReceipt(factory.newReceipt(), queue, message);
    }

    static <T, R, S> CompletableReceipt<R> sendMessageReceipt(ImmutableReceipt receipt, MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        return sendMessageReceipt(new CompletableReceipt<R>(receipt), queue, message);
    }

    static <T, R, S> CompletableReceipt<R> sendMessageReceipt(CompletableReceipt<R> receipt, MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        MessageWithAnswer<T, R> mwr = new MessageWithAnswer<>(message, receipt);
        queue.add(Either3.other(mwr));

        assert mwr.answer == receipt;

        return receipt;
    }

    static <T, R, S> void execAsync(MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        queue.add(Either3.left(worker));
    }

    static <T, R, S> void execAndWait(MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        SignalingSingleConsumer<S> sc = SignalingSingleConsumer.of(worker);

        queue.add(Either3.left(sc));
        Exceptions.log(sc::await);
    }

    public static <T, T2> Consumer<T> convertAndSendTo(Consumer<T2> targetActor, Function<T, T2> converter) {
        return originalValue -> targetActor.accept(converter.apply(originalValue));
    }

    static <T, R, S> CompletableFuture<Void> execFuture(MiniQueue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        SignalingSingleConsumer<S> sr = SignalingSingleConsumer.of(worker);

        queue.add(Either3.left(sr));

        return new CompletableFuture<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return sr.isDone();
            }

            @Override
            public Void get() throws InterruptedException, ExecutionException {
                sr.await();
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                sr.await(timeout, unit);
                return null;
            }
        };
    }

    static <T, R> Function<T, R> discardingToReturning(Consumer<T> actorLogic) {
        return message -> {
            actorLogic.accept(message);

            return null;
        };
    }

    static <T, T2, R> BiFunction<T, T2, R> discardingToReturning(BiConsumer<T, T2> actorLogic) {
        return (param1, param2) -> {
            actorLogic.accept(param1, param2);

            return null;
        };
    }

    static <T, R> Consumer<T> returningToDiscarding(Function<T, R> actorLogic) {
        return actorLogic::apply;
    }

    static <T, T2, R> BiConsumer<T, T2> returningToDiscarding(BiFunction<T, T2, R> actorLogic) {
        return actorLogic::apply;
    }

    public static boolean areFibersAvailable() {
        return true;
    }

    /**
     * Implements a typical initialization where ac actor is created and its logic needs access to the actor address
     */
    public static <T> T initRef(Function<AtomicReference<T>, T> consumer) {
        AtomicReference<T> ref = new AtomicReference<>();

        ref.set(consumer.apply(ref));

        return ref.get();
    }

    public static <T> Consumer<T> extractEventHandlerLogic(Object messageHandler) {
        final Set<Map.Entry<Class, Method>> types = extractEventHandlers(messageHandler.getClass()).entrySet();

        return message -> Exceptions.logShort(() -> {
            Method methodToCall = findBestMethod(types, message, messageHandler.getClass().getName());

            methodToCall.invoke(messageHandler, message);
        });
    }

    public static <T, R> Function<T, R> extractEventHandlerLogicWithReturn(Object messageHandler) {
        final Set<Map.Entry<Class, Method>> types = extractEventHandlers(messageHandler.getClass()).entrySet();

        return message -> Exceptions.logShort(() -> {
            Method methodToCall = findBestMethod(types, message, messageHandler.getClass().getName());

            return (R) methodToCall.invoke(messageHandler, message);
        }, null);
    }

    private static <T> Method findBestMethod(Set<Map.Entry<Class, Method>> types, T message, String handlerClassName) {
        Class messageType = message.getClass();
        // Direct check
        for (Map.Entry<Class, Method> entry : types) {
            if (messageType == entry.getKey())
                return entry.getValue();
        }

        // Subclass check
        // Direct check
        for (Map.Entry<Class, Method> entry : types) {
            if (entry.getKey().isAssignableFrom(messageType))
                return entry.getValue();
        }

        throw new IllegalArgumentException("Message of type " + messageType.getName() + " cannot be handled by class " + handlerClassName + "!");
    }

    /**
     * @param clazz Class to analyze
     * @return a ordered map of event handlers; an event handler is a public method with name starting with "onXXX()" and a single parameter; methods of subclasses are returned first, to give them priority
     */
    public static LinkedHashMap<Class, Method> extractEventHandlers(Class clazz) {
        Map<Class, Method> map = extractUnorderedEventHandlers(clazz);
        List<Class> orderedClasses = new ArrayList<>(sortSubClasses(map.keySet()));
        Collections.reverse(orderedClasses);
        LinkedHashMap<Class, Method> orderedMap = new LinkedHashMap<>();

        for (Class cl : orderedClasses)
            orderedMap.put(cl, map.get(cl));

        return orderedMap;
    }

    private static Map<Class, Method> extractUnorderedEventHandlers(Class clazz) {
        Map<Class, Method> map = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            if (isMethodHandler(method)) {
                Class<?> type = method.getParameterTypes()[0];

                if (map.containsKey(type))
                    throw new IllegalArgumentException("Handler for type " + type.getName() + " has already been defined: " + map.get(type).getName());

                map.put(type, method);
            }
        }
        return map;
    }

    public static Collection<Class> sortSubClasses(Collection<Class> types) {
        List<Class> independentTypes = new ArrayList<>();
        List<Class> dependentTypes = new ArrayList<>();

        for (Class type : types) {
            if (isSubclass(type, types))
                dependentTypes.add(type);
            else
                independentTypes.add(type);
        }

        // Ensure that the recursion will end
        assert independentTypes.size() > 0 || types.size() == 0;

        return mergeClasses(independentTypes, dependentTypes);
    }

    private static boolean isSubclass(Class type, Collection<Class> types) {
        for (Class t : types) {
            if (t != type && t.isAssignableFrom(type))
                return true;
        }

        return false;
    }

    private static Collection<Class> mergeClasses(Collection<Class> independentTypes, Collection<Class> dependentTypes) {
        if (dependentTypes.size() == 1)
            independentTypes.add(dependentTypes.iterator().next());
        else if (dependentTypes.size() > 1)
            independentTypes.addAll(sortSubClasses(dependentTypes));

        return independentTypes;
    }

    public static void runAsFiber(Runnable... runnables) {
        ThreadFactory threadFactory = Thread.ofVirtual().factory();
        for (Runnable run : runnables) {
            threadFactory.newThread(run).start();
        }
    }

    private static boolean isMethodHandler(Method method) {
        return method.getName().length() >= 3 && method.getName().startsWith("on") && Character.isUpperCase(method.getName().charAt(2)) && method.getParameterCount() == 1;
    }

    public static ExecutorService newVirtualThreadsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * @return A new virtualThreadsExecutor
     * @deprecated Use {@link #newVirtualThreadsExecutor()} instead
     */
    @Deprecated(since = "3", forRemoval = true)
    public static ExecutorService newFibersExecutor() {
        return newVirtualThreadsExecutor();
    }
}
