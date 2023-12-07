package eu.lucaventuri.fibry;

import junit.framework.TestCase;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class TestGenerators extends TestCase {

    public void testGeneratorEmpty() {
        Iterable<Integer> gen = Generator.fromProducer(yielder -> {
        }, 5, true);

        for(int n: gen)
            System.out.println(n);

        System.out.println("Empty done");
    }

    public void testGeneratorRndom() {
        Iterable<Integer> gen = Generator.fromProducer(yielder -> {
            for (int i = 0; i <= 10; i++) {
                if (Math.random() < 0.5)
                    yielder.yield(i);
            }
        }, 5, true);

        for(int n: gen)
            System.out.println(n);
    }

    public void testGeneratorSmall() {
        testGenerator(1, 10, false);
        testGenerator(5, 10, false);
        testGenerator(20, 10, false);

        testGenerator(1, 100_000, false);
        testGenerator(100, 100_000, false);
    }

    public void testGeneratorMultipleTimes() {
        int num = 50;

        Generator<Integer> gen = Generator.fromProducer(yielder -> {
            for (int i = 0; i <= num; i++)
                yielder.yield(i);
        }, 20);

        testResult(gen);
        testResult(gen);
        testResult(gen);
    }

    public void testGenerator100K_1() {
        testGenerator(1, 100_000, false);
    }

    public void testGenerator100K_10() {
        testGenerator(1, 100_000, false);
    }

    public void testGenerator100K_100() {
        testGenerator(100, 100_000, false);
    }

    public void testGenerator1M_100() {
        testGenerator(100, 1_000_000, false);
    }

    public void testGenerator1M_100_max() {
        testGenerator(100, 1_000_000, true);
    }

    public void testStream() {
        AtomicLong l = new AtomicLong();
        AtomicInteger pos = new AtomicInteger();

        Stream.generate(pos::incrementAndGet).limit(1000_000).forEach(l::addAndGet);

        System.out.println(l.get());
    }

    public void testToStream() {
        int num = 100_000;
        Generator<Integer> gen = Generator.fromProducer(yielder -> {
            Random rnd = new Random(100);

            int n = 0;
            for (int i = 0; i <= num; i++) {
                if (rnd.nextDouble() >= 0.5) {
                    yielder.yield(n++);
                }
            }
        }, 100);

        long sum = testResult(gen);

        AtomicLong l1 = new AtomicLong();
        AtomicLong l2 = new AtomicLong();
        AtomicLong n1 = new AtomicLong();
        AtomicLong n2 = new AtomicLong();

        gen.toStream().forEach(number -> {
            n1.incrementAndGet();
            l1.addAndGet(number);
        });
        gen.toStream().forEach(number -> {
            n2.incrementAndGet();
            l2.addAndGet(number);
        });

        assertEquals(sum, l1.get());
        assertEquals(sum, l2.get());
        assertEquals(n1.get(), n2.get());
    }

    public void testParallelGenerator1M_100_8() {
        testParallelGenerators(1000, 125_000, 8, false);
    }

    public void testParallelGenerator1M_100_8_MAX() {
        testParallelGenerators(1000, 125_000, 8, true);
    }

    public void testAdvancedGeneratorSmall() {
        testAdvancedGenerator(1, 10, false);
        testAdvancedGenerator(5, 10, false);
        testAdvancedGenerator(20, 10, false);
    }

    public void testAdvancedGeneratorNonEmptyError() throws InterruptedException {
        testForErrors(() -> testAdvancedGeneratorNonEmpty(1, 5));
    }

    public void testAdvancedGeneratorError() throws InterruptedException {
        testForErrors(() -> testAdvancedGenerator(1, 5, false));
    }

    public void testGeneratorError() throws InterruptedException {
        testForErrors(() -> testGenerator(1, 5, false));
    }

    public void testForErrors(Runnable run) throws InterruptedException {
        int n = 50;
        CountDownLatch latch = new CountDownLatch(n);

        for (int i = 0; i < n; i++) {
            new Thread(() -> {
                run.run();
                latch.countDown();
                //System.out.println("Counting down...");
            }).start();
        }

        latch.await();
    }

    public void testAdvancedGenerator1M_1() {
        testAdvancedGenerator(1, 1_000_000, false);
    }

    public void testAdvancedGenerator1M_1_MAX() {
        testAdvancedGenerator(1, 1_000_000, true);
    }


    public void testAdvancedGenerator1M_100() {
        testAdvancedGenerator(100, 1_000_000, false);
    }

    public void testAdvancedGenerator1M_100_MAX() {
        testAdvancedGenerator(100, 1_000_000, true);
    }

    public void testAdvancedGeneratorNonEmpty1M_100() {
        testAdvancedGeneratorNonEmpty(100, 1_000_000);
    }

    private void testGenerator(int queueSize, int num, boolean maxThroughput) {
        Generator<Integer> gen = Generator.fromProducer(yielder -> {
            for (int i = 0; i <= num; i++)
                yielder.yield(i);
        }, queueSize, maxThroughput);

        testResult(gen);
    }

    private void testParallelGenerators(int queueSize, int numPerProducer, int numProducers, boolean maxThroughput) {
        AtomicInteger index = new AtomicInteger();
        Generator<Integer> gen = Generator.fromParallelProducers(() -> yielder -> {
            int ind = index.getAndIncrement();
            for (int i = 0; i <= numPerProducer; i++)
                yielder.yield((numPerProducer + 1) * ind + i);
        }, numProducers, queueSize, maxThroughput);

        testResult(gen);
    }

    private long testResult(Generator<Integer> gen) {
        long sum = 0;
        int num = 0;

        for (int n : gen) {
            sum += n;
            num++;
        }

        assertEquals(((long) num * (num - 1) / 2), sum);

        return sum;
    }

    private void testAdvancedGenerator(int queueSize, int num, boolean maxThroughput) {
        Generator<Integer> gen = Generator.fromAdvancedProducer(yielder -> {
            for (int i = 0; i <= num - 1; i++)
                yielder.yield(i);

            return num;
        }, num, maxThroughput);

        testResult(gen);
    }

    private void testAdvancedGeneratorError(int queueSize, int num) {
        Generator<Integer> gen = Generator.fromAdvancedProducer(yielder -> {
            for (int i = 0; i <= num; i++)
                yielder.yield(i);

            return null;
        }, num);

        testResult(gen);
    }

    private void testAdvancedGeneratorNonEmpty(int queueSize, int num) {
        Generator<Integer> gen = Generator.fromNonEmptyAdvancedProduce(yielder -> {
            for (int i = 0; i <= num - 1; i++)
                yielder.yield(i);

            return num;
        }, num);

        testResult(gen);
    }
}


