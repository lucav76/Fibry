package eu.lucaventuri.fibry;

import java.util.concurrent.atomic.AtomicInteger;

import eu.lucaventuri.common.SystemUtils;
import junit.framework.TestCase;

public class TestWeightedActor extends TestCase {

    public void testNormalPool() {
        AtomicInteger num = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        int numThreads = 10;

        var leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(numThreads), null).newPool(msg-> {
            int n = num.incrementAndGet();
            max.updateAndGet( m -> Math.max(m, n));
            SystemUtils.sleep(10);
            num.decrementAndGet();
        });

        for(int i=0; i<50; i++)
            leader.sendMessage(i);

        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(max.get(), 10);
    }

    public void testWeightedPoolWeight1() {
        AtomicInteger num = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        int numThreads = 10;

        var leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(numThreads), null).newWeightedPool(msg-> {
            int n = num.incrementAndGet();
            max.updateAndGet( m -> Math.max(m, n));
            SystemUtils.sleep(10);
            num.decrementAndGet();
        });

        for(int i=0; i<50; i++)
            leader.sendMessage(i, 1);

        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(max.get(), 10);
    }

    public void testWeightedPoolWeight2() {
        AtomicInteger num = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        int numThreads = 10;

        var leader = ActorSystem.anonymous().poolParams(PoolParameters.fixedSize(numThreads), null).newWeightedPool(msg-> {
            int n = num.incrementAndGet();
            max.updateAndGet( m -> Math.max(m, n));
            SystemUtils.sleep(10);
            num.decrementAndGet();
        });

        for(int i=0; i<50; i++)
            leader.sendMessage(i, 3);

        leader.sendPoisonPill();
        leader.waitForExit();

        assertEquals(max.get(), 3);
    }
}
