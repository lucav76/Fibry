package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.common.NameValuePair;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubscribableMapTest extends TestCase {
    @Test
    public void testBasics() {
        var v = new SubscribableMap<String, String>().put("a", "AAA");
        AtomicInteger times = new AtomicInteger();

        assertEquals(v.get("a"), "AAA");
        var subscription = v.subscribe(s -> times.incrementAndGet());
        assertEquals(v.get("a"), "AAA");
        assertEquals(times.get(), 0);
        assertNull(v.get("b"));
        v.put("b", "BBB");
        assertEquals(times.get(), 1);
        assertEquals(v.get("b"), "BBB");

        var subscription2 = v.subscribe(s -> times.incrementAndGet());
        v.put("b", "BBB");
        assertEquals(times.get(), 3);
        assertEquals(v.get("b"), "BBB");

        subscription2.cancel();
        v.put("c", "CCC");
        assertEquals(times.get(), 4);
        assertEquals(v.get("c"), "CCC");

        subscription.cancel();
        v.put("d", "DDD");
        assertEquals(times.get(), 4);
        assertEquals(v.get("d"), "DDD");

        var subscription3 = v.subscribe(s -> {
            times.incrementAndGet();
            assertEquals(s, new NameValuePair<>("e", "EEE"));
        });
        v.put("e", "EEE");
        assertEquals(times.get(), 5);
        assertEquals(v.get("e"), "EEE");
        subscription3.cancel();

        var subscription4 = v.subscribe(s -> {
            times.incrementAndGet();
            assertEquals(s.key, "e");
            assertNull(s.value);
        });
        v.remove("e");
        assertEquals(times.get(), 6);
        assertNull(v.get("e"));
    }
}