package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMassive {
    public static void main(String[] args) throws InterruptedException {
        int num = 500_000;
        CountDownLatch latch = new CountDownLatch(num);
        long start = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger max = new AtomicInteger(0);

        System.out.println("Creating " + num + " virtual threads");

        for (int i=0; i<num; i++) {
            Stereotypes.fibers().runOnce(() -> {
                final int curCount = count.incrementAndGet();
                int curMax = max.get();

                while (curCount > curMax) {
                    curMax = max.compareAndExchange(curMax, curCount);
                }
                SystemUtils.sleep(100);
                latch.countDown();
                count.decrementAndGet();
            });
        }

        latch.await();
        System.out.println("Done in " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("Max parallelism found: " + max.get());
    }

}
