package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorUtils;
import eu.lucaventuri.fibry.MapReducer;
import eu.lucaventuri.fibry.PoolParameters;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class MapReduceExample {
    public static void main(String[] args) {
        int num = Integer.parseInt(args[0]);
        int numParallel = Integer.parseInt(args[1]);
        boolean threads = args.length == 3 && args[2].equals("threads");

        if (num % numParallel != 0)
            throw new IllegalArgumentException(num + " should be divisible by " + numParallel);

        Stereotypes.NamedStereotype st = threads ? Stereotypes.threads() : Stereotypes.def();
        System.out.println("Using " + (threads ? "threads" : "default strategy: " + st.getStrategy() + " - Fibers available: " + ActorUtils.areFibersAvailable()));

        AtomicReference<Double> piSingleThread = new AtomicReference<>();
        long msSingleThread = SystemUtils.time(() -> piSingleThread.set(4.0 * countInside(num) / num));

        System.out.println("PI estimated by current thread: " + piSingleThread.get() + " - " + msSingleThread + " ms");

        AtomicReference<Double> piMultiThread = new AtomicReference<>();

        long msMultiThread = SystemUtils.time(() -> {
            //st.mapReduce(PoolParameters.fixedSize(numParallel), (Integer n) -> countInside(n), );

            MapReducer<Integer, Integer> mrMulti = st.mapReduce(MapReduceExample::countInside, Integer::sum, 0);

            for (int i = 0; i < numParallel; i++)
                mrMulti.map(num / numParallel);
            piMultiThread.set(4.0 * mrMulti.get(true) / num);
        });

        System.out.println("PI estimated by " + numParallel + " threads: " + piMultiThread.get() + " - " + msMultiThread + " ms");

        // Slow, message based, version
        AtomicReference<Double> piMessageFlood = new AtomicReference<>();
        long msMessageFlood = SystemUtils.time(() -> {
            //st.mapReduce(PoolParameters.fixedSize(numParallel), (Integer n) -> countInside(n), );
            MapReducer<Integer, Integer> mr = st.mapReduce(PoolParameters.fixedSize(numParallel),MapReduceExample::countInside, Integer::sum, 0);
            for (int i = 0; i < num; i++)
                mr.map(1);
            piMessageFlood.set(4.0 * mr.get(true) / num);
        });

        System.out.println("PI estimated by " + numParallel + " threads and " + num + ": " + piMessageFlood.get() + " - " + msMessageFlood + " ms");
    }

    public static int countInside(int num) {
        Random rnd = ThreadLocalRandom.current();
        int inside = 0;

        for (int i = 0; i < num; i++) {
            double x = rnd.nextDouble();
            double y = rnd.nextDouble();

            if (x * x + y * y < 1)
                inside++;
        }

        return inside;
    }
}
