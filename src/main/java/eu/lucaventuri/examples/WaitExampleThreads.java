package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.jmacs.Stereotypes;

import java.util.concurrent.atomic.AtomicInteger;

public class WaitExampleThreads {
    public static void main(String[] args) {
        final AtomicInteger waiting = new AtomicInteger();

        Stereotypes.threads().runOnce(() -> {
            int last = -1;
            long start = System.currentTimeMillis();

            while (true) {
                SystemUtils.sleep(250);

                System.out.println("Waiting threads: " + waiting.get() + " - time: " + (System.currentTimeMillis() - start));

                if (last == waiting.get())
                    System.exit(0);


                last = waiting.get();
            }
        });

        for (int i = 0; i < 100_000; i++) {
            Stereotypes.threads().runOnce(() -> {
                waiting.incrementAndGet();
                SystemUtils.sleep(30_000);
                waiting.decrementAndGet();
            });
        }
    }
}
