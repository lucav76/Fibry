package eu.lucaventuri.fibry.pubsub;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubscribableCompositeValueTest extends TestCase {
    @Test
    public void testBasics() {
        var v = new SubscribableCompositeValue<Integer>(Integer.class, 3).put(0, 100);
        AtomicInteger times = new AtomicInteger();

        Assert.assertArrayEquals(v.get(), new Integer[]{100, null, null});
        var subscription = v.subscribe(s -> times.incrementAndGet());
        Assert.assertArrayEquals(v.get(), new Integer[]{100, null, null});
        assertEquals(times.get(), 0);
        v.put(1, 200);
        assertEquals(times.get(), 0);
        Assert.assertArrayEquals(v.get(), new Integer[]{100, 200, null});
        v.put(2, 300);
        assertEquals(times.get(), 1);
        Assert.assertArrayEquals(v.get(), new Integer[]{100, 200, 300});
        v.subscribe(s -> {
                    times.incrementAndGet();
                    Assert.assertArrayEquals(v.get(), new Integer[]{100, 200, 301});
                }
        );
        v.put(2, 301);
        assertEquals(times.get(), 3);
        Assert.assertArrayEquals(v.get(), new Integer[]{100, 200, 301});
    }
}