package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.concurrent.SignalingSingleConsumer;
import eu.lucaventuri.functional.Either3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ActorUtils {
    private final static Class clzFiberScope;
    private final static Class clzFiber;
    private final static MethodHandle mhFiberScopeOpen;
    private final static MethodHandle mhFiberScopeScheduleRunnable;
    private final static MethodHandle mmhFiberScopeScheduleCallable;
    private final static Object options;
    private final static AutoCloseable globalFiberScope;

    static {
        MethodHandle tmpMethodOpen = null;
        MethodHandle tmpMethodScheduleRunnable = null;
        MethodHandle tmpMethodScheduleCallable = null;

        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        clzFiberScope = SystemUtils.findClassByName("java.lang.FiberScope");
        clzFiber = SystemUtils.findClassByName("java.lang.Fiber");
        Class clzOptionArray = SystemUtils.findClassByName("[Ljava.lang.FiberScope$Option;");
        options = clzOptionArray == null ? null : Array.newInstance(clzOptionArray, 0);

        try {
            MethodType mtOpen = clzFiberScope == null ? null : MethodType.methodType(clzFiberScope, clzOptionArray);
            tmpMethodOpen = clzFiberScope == null ? null : publicLookup.findStatic(clzFiberScope, "open", mtOpen);

            MethodType mtScheduleRunnable = clzFiberScope == null ? null : MethodType.methodType(clzFiber, Runnable.class);
            MethodType mtScheduleCallable = clzFiberScope == null ? null : MethodType.methodType(clzFiber, Callable.class);
            tmpMethodScheduleRunnable = clzFiberScope == null ? null : publicLookup.findVirtual(clzFiberScope, "schedule", mtScheduleRunnable);
            tmpMethodScheduleCallable = clzFiberScope == null ? null : publicLookup.findVirtual(clzFiberScope, "schedule", mtScheduleCallable);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }

        mhFiberScopeOpen = tmpMethodOpen;
        mhFiberScopeScheduleRunnable = tmpMethodScheduleRunnable;
        mmhFiberScopeScheduleCallable = tmpMethodScheduleCallable;

        globalFiberScope = clzFiberScope == null ? null : openFiberScope();

        //System.out.println("clzOptionArray: " + clzOptionArray);
    }

    private ActorUtils() { /* Static methods only */}

    static <T, R, S> void sendMessage(Queue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        queue.add(Either3.right(message));
    }

    static <T, R, S> CompletableFuture<R> sendMessageReturn(Queue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, T message) {
        MessageWithAnswer<T, R> mwr = new MessageWithAnswer<>(message);
        queue.add(Either3.other(mwr));

        return mwr.answers;
    }

    static <T, R, S> void execAsync(Queue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        queue.add(Either3.left(worker));
    }

    static <T, R, S> void execAndWait(Queue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
        SignalingSingleConsumer<S> sc = SignalingSingleConsumer.of(worker);

        queue.add(Either3.left(sc));
        Exceptions.log(sc::await);
    }

    static <T, R, S> CompletableFuture<Void> execFuture(Queue<Either3<Consumer<S>, T, MessageWithAnswer<T, R>>> queue, Consumer<S> worker) {
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
        return clzFiberScope != null;
    }

    public static AutoCloseable openFiberScope() {
        if (null == mhFiberScopeOpen)
            return null;

        try {
            return (AutoCloseable) mhFiberScopeOpen.invokeWithArguments();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void runAsFiberScope(Runnable... runnables) {
        if (mhFiberScopeScheduleRunnable == null)
            throw new UnsupportedOperationException("No fibers available!");

        try (AutoCloseable scope = openFiberScope()) {
            for (Runnable run : runnables)
                mhFiberScopeScheduleRunnable.invoke(scope, run);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void runAsFiberScope(Callable... callables) {
        if (mmhFiberScopeScheduleCallable == null)
            throw new UnsupportedOperationException("No fibers available!");

        try (AutoCloseable scope = openFiberScope()) {
            for (Callable callable : callables)
                mhFiberScopeScheduleRunnable.invoke(scope, callable);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void runAsFiber(Runnable... runnables) {
        if (mhFiberScopeScheduleRunnable == null)
            throw new UnsupportedOperationException("No fibers available!");

        try {
            for (Runnable run : runnables)
                mhFiberScopeScheduleRunnable.invoke(globalFiberScope, run);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void runAsFiber(Callable... callables) {
        if (mmhFiberScopeScheduleCallable == null)
            throw new UnsupportedOperationException("No fibers available!");

        try {
            for (Callable callable : callables)
                mhFiberScopeScheduleRunnable.invoke(globalFiberScope, callable);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
