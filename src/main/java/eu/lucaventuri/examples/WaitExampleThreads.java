package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WaitExampleThreads {
    public static void main(String[] args) {
        final AtomicInteger waiting = new AtomicInteger();
        long start = System.currentTimeMillis();
        final AtomicLong last = new AtomicLong(-1);

        Stereotypes.threads().schedule(() -> {
            System.out.println("Waiting threads: " + waiting.get() + " - time: " + (System.currentTimeMillis() - start));

            if (last.get() == waiting.get())
                System.exit(0);

            last.set(waiting.get());
        }, 250);

        for (int i = 0; i < 10_000; i++) {
            Stereotypes.threads().runOnce(() -> {
                waiting.incrementAndGet();
                SystemUtils.sleep(30_000);
                waiting.decrementAndGet();
            });
        }
    }
}
