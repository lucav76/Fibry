package eu.lucaventuri.common;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

/**
 * Some utilities
 *
 * @author Luca Venturi
 */
public final class SystemUtils {
    static private final boolean assertsEnabled;

    static {
        // Check if the asserts are enabled
        boolean tempAssertsEnabled = false;
        assert tempAssertsEnabled = true;

        assertsEnabled = tempAssertsEnabled;
    }

    private SystemUtils() {
    }

    /**
     * Sleeps some ms
     *
     * @param ms ms to sleep
     */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms < 0 ? 0 : ms);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Sleeps some ms
     *
     * @param ms ms to sleep
     */
    public static void sleepEnsure(long ms) {
        long target = System.currentTimeMillis() + ms;

        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            sleepEnsure(target - System.currentTimeMillis());
        }
    }

    /**
     * Sleeps a random time
     *
     * @param ms maximum ms to sleep
     */
    public static void randomSleep(int ms) {
        sleep(new Random().nextInt(ms) + 1);
    }

    /**
     * Sleeps a random time
     *
     * @param minMs minimum ms to sleep
     * @param maxMs maximum ms to sleep
     */
    public static void randomSleep(int minMs, int maxMs) {
        assert maxMs > minMs;

        sleep(new Random().nextInt(maxMs - minMs) + minMs);
    }

    /**
     * @param parent starting directory
     * @return a list with all the files, recursively
     */
    public static List<File> getAllFiles(File parent) {
        List<File> files = new ArrayList<>();

        for (File f : parent.listFiles()) {
            if (f.isDirectory())
                files.addAll(getAllFiles(f));
            else
                files.add(f);
        }

        return files;
    }

    /**
     * @param parent starting directory
     * @return a list with all the files
     */
    public static Stream<File> getAllFilesStream(File parent) {
        List<File> files = getAllFiles(parent);

        Builder<File> builder = Stream.<File>builder();

        for (File file : files)
            builder.accept(file);

        return builder.build();
    }

    /**
     * Close without exceptions
     *
     * @param clo closeable to close
     */
    public static void close(Closeable clo) {
        if (clo == null)
            return;

        try {
            clo.close();
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /**
     * Close an Optional without exceptions
     *
     * @param clo closeable to close
     */
    public static void close(Optional<? extends Closeable> clo) {
        clo.ifPresent(SystemUtils::close);
    }


    /**
     * @return true if the asserts are enabled, and therefore the program is running in debug mode
     */
    public static boolean getAssertsEnabled() {
        return assertsEnabled;
    }

    /**
     * @param className name of the class to find
     * @return the class, or null if it is not available
     */
    @SuppressWarnings("rawtypes")
    public static Class findClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static long time(Runnable run) {
        long start = System.currentTimeMillis();

        run.run();

        return System.currentTimeMillis() - start;
    }

    public static <E extends Exception> long timeEx(RunnableEx<E> run) throws E {
        long start = System.currentTimeMillis();

        run.run();

        return System.currentTimeMillis() - start;
    }

    public static long printTime(Runnable run, String description) {
        long start = System.currentTimeMillis();

        run.run();

        long time = System.currentTimeMillis() - start;

        System.out.println(description + " : " + time + " ms");

        return time;
    }

    public static <E extends Exception> long printTimeEx(RunnableEx<E> run, String description) throws E {
        long start = System.currentTimeMillis();

        run.run();

        long time = System.currentTimeMillis() - start;

        System.out.println(description + " : " + time + " ms");

        return time;
    }
}