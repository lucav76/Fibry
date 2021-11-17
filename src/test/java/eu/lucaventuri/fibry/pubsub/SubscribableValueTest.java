package eu.lucaventuri.fibry.pubsub;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubscribableValueTest extends TestCase {
    @Test
    public void testBasics() {
        var v = new SubscribableValue<String>().set("a");
        AtomicInteger times = new AtomicInteger();

        assertEquals(v.get(), "a");
        var subscription = v.subscribe(s -> times.incrementAndGet());
        assertEquals(v.get(), "a");
        assertEquals(times.get(), 0);
        v.set("AAA");
        assertEquals(times.get(), 1);
        assertEquals(v.get(), "AAA");

        var subscription2 = v.subscribe(s -> times.incrementAndGet());
        v.set("BBB");
        assertEquals(times.get(), 3);
        assertEquals(v.get(), "BBB");

        subscription2.cancel();
        v.set("CCC");
        assertEquals(times.get(), 4);
        assertEquals(v.get(), "CCC");

        subscription.cancel();
        v.set("DDD");
        assertEquals(times.get(), 4);
        assertEquals(v.get(), "DDD");

        v.subscribe(s -> times.incrementAndGet());
        v.set("EEE");
        assertEquals(times.get(), 5);
        assertEquals(v.get(), "EEE");
    }
}