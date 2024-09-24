package eu.lucaventuri.common;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

//@Slf4j
final public class Exceptions {

    private static final Logger logger = Logger.getLogger(Exceptions.class.getName());
    private Exceptions() { /* Static only*/ }

    public static void silence(RunnableEx run) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.FINEST, t.getMessage(), t);
        }
    }

    public static void silence(RunnableEx run, RunnableEx finalizer) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.FINEST, t.getMessage(), t);
        } finally {
            if (finalizer != null)
                Exceptions.silence(finalizer::run);
        }
    }

    public static Runnable silentRunnable(RunnableEx run) {
        return () -> {
            try {
                run.run();
            } catch (Throwable t) {
                logger.log(Level.FINEST, t.getMessage(), t);
            }
        };
    }

    public static <T> T silence(CallableEx<T, ? extends Throwable> call, T valueOnException) {
        try {
            return call.call();
        } catch (Throwable t) {
            return valueOnException;
        }
    }

    public static <T> T silence(CallableEx<T, ? extends Throwable> call, T valueOnException, RunnableEx finalizer) {
        try {
            return call.call();
        } catch (Throwable t) {
            logger.log(Level.FINEST, t.getMessage(), t);
            return valueOnException;
        } finally {
            if (finalizer != null)
                Exceptions.silence(finalizer);
        }
    }

    public static <T> Callable<T> silentCallable(CallableEx<T, ? extends Throwable> call, T valueOnException) {
        return () -> {
            try {
                return call.call();
            } catch (Throwable t) {
                logger.log(Level.FINEST, t.getMessage(), t);
                return valueOnException;
            }
        };
    }

    public static <T> Consumer<T> silentConsumer(ConsumerEx<T, ? extends Throwable> consumer) {
        return input -> {
            try {
                consumer.accept(input);
            } catch (Throwable t) {
                logger.log(Level.FINEST, t.getMessage(), t);
            }
        };
    }

    public static void rethrowRuntime(RunnableEx run) {
        try {
            run.run();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static <T> T rethrowRuntime(CallableEx<T, ? extends Throwable> call) {
        try {
            return call.call();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void log(RunnableEx run) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage(), t);
        }
    }

    public static void log(RunnableEx run, RunnableEx finalRun) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage(), t);
        }
        finally {
            Exceptions.log(finalRun);
        }
    }

    public static <T> T log(CallableEx<T, ? extends Throwable> call, T valueOnException) {
        try {
            return call.call();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
            return valueOnException;
        }
    }

    public static <T> T log(CallableEx<T, ? extends Throwable> call, T valueOnException, RunnableEx finalRun) {
        try {
            return call.call();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
            return valueOnException;
        }
        finally {
            Exceptions.log(finalRun);
        }
    }

    public static void logShort(RunnableEx run) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
        }
    }

    public static void logShort(RunnableEx run, RunnableEx finalRun) {
        try {
            run.run();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
        }
        finally {
            Exceptions.log(finalRun);
        }
    }

    public static <T> T logShort(CallableEx<T, ? extends Throwable> call, T valueOnException) {
        try {
            return call.call();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
            return valueOnException;
        }
    }

    public static <T> T logShort(CallableEx<T, ? extends Throwable> call, T valueOnException, RunnableEx finalRun) {
        try {
            return call.call();
        } catch (Throwable t) {
            logger.log(Level.INFO, t.getMessage());
            return valueOnException;
        }
        finally {
            Exceptions.log(finalRun);
        }
    }

    /**
     * Executes a callable, and in case of exception it returns a result provided by a supplier
     *
     * @param callable Callable to execute
     * @param defaultSupplier Supplier a to provide a default value, in case of exception
     * @param <T> Type to return
     * @param <E> Exception that can be thrown
     * @return the result of the callable in case of success, and of the supplier if an exception is thrown
     */
    public static <T, E extends Throwable> T orElse(CallableEx<T, E> callable, Supplier<T> defaultSupplier) {
        try {
            return callable.call();
        } catch (Throwable e) {
            logger.log(Level.INFO, e.getMessage(), e);
            return defaultSupplier.get();
        }
    }

    /**
     * Executes a callable, and in case of exception it returns a default result
     *
     * @param callable Callable to execute
     * @param defaultValue default value, in case of exception
     * @param <T> Type to return
     * @param <E> Exception that can be thrown
     * @return the result of the callable in case of success, or the default value if an exception is thrown
     */
    public static <T, E extends Throwable> T orElseValue(CallableEx<T, E> callable, T defaultValue) {
        try {
            return callable.call();
        } catch (Throwable e) {
            logger.log(Level.INFO, e.getMessage(), e);
            return defaultValue;
        }
    }

    /**
     * Assert that a certain condition is met, or throw an exception
     */
    public static void assertAndThrow(boolean check, String error) {
        assert check : error;

        if (!check)
            throw new IllegalArgumentException(error);
    }

    public static Stream<StackTraceElement> getCompactStackTrace(Throwable ex, boolean skippAllJava) {
        StackTraceElement[] stack = ex.getStackTrace();
        int len = stack.length;

        while (len > 0 && isGenericClass(stack[len - 1]))
            len--;

        Stream<StackTraceElement> stream = Arrays.stream(stack).limit(len);

        if (skippAllJava)
            stream = stream.filter(s -> !isJavaClass(s));

        return stream;
    }

    private static boolean isJavaClass(StackTraceElement elem) {
        String cls = elem.getClassName();

        return (cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("sun."));
    }

    private static boolean isGenericClass(StackTraceElement elem) {
        String cls = elem.getClassName();

        return (isJavaClass(elem) || cls.startsWith("org."));
    }
}
