package eu.lucaventuri.collections;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.jmacs.Actor;
import eu.lucaventuri.jmacs.Stereotypes;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class TestStereotypes {
    @Test
    public void testWorkers() throws ExecutionException, InterruptedException {
        final AtomicInteger val = new AtomicInteger();
           Supplier<Actor<Integer, Void, Void>> master = Stereotypes.auto().workersCreator(val::addAndGet);

        master.get().sendMessageReturn(1).get();
        master.get().sendMessageReturn(2).get();
        master.get().sendMessageReturn(3).get();

        assertEquals(6, val.get());
    }

    @Test
    public void testWorkersConsumer() throws ExecutionException, InterruptedException {
        final AtomicInteger val = new AtomicInteger();
        Consumer<Integer> master = Stereotypes.auto().workersConsumer(n -> {
            System.out.println(n);

            val.addAndGet(n);
        });

        master.accept(1);
        master.accept(2);
        master.accept(3);

        while (val.get() < 6)
            SystemUtils.sleep(100);

        assertEquals(6, val.get());
    }

    @Test
    public void testWorkerWithReturn() throws ExecutionException, InterruptedException {
        final AtomicInteger val = new AtomicInteger();
        Supplier<Actor<Integer, Integer, Void>> master = Stereotypes.auto().workersWithReturnCreator(val::addAndGet);

        assertEquals(1, master.get().sendMessageReturn(1).get().intValue());
        assertEquals(3, master.get().sendMessageReturn(2).get().intValue());
        assertEquals(6, master.get().sendMessageReturn(3).get().intValue());

        assertEquals(6, val.get());
    }

    @Test
    public void testWorkerWithReturnFunction() throws ExecutionException, InterruptedException {
        final AtomicInteger val = new AtomicInteger();
        Function<Integer, CompletableFuture<Integer>> master = Stereotypes.auto().workersFunction(val::addAndGet);

        assertEquals(1, master.apply(1).get().intValue());
        assertEquals(3, master.apply(2).get().intValue());
        assertEquals(6, master.apply(3).get().intValue());

        assertEquals(6, val.get());
    }
}
