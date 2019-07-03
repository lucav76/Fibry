package eu.lucaventuri.common;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
    public static void sleep(int ms) {
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
     * @return true if the asserts are enabled, and therefore the program is running in debug mode
     */
    public static boolean getAssertsEnabled() {
        return assertsEnabled;
    }
}