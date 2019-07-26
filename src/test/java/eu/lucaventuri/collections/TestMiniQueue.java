package eu.lucaventuri.collections;

import eu.lucaventuri.fibry.MiniQueue;
import eu.lucaventuri.fibry.Stereotypes;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingDeque;
import static org.junit.Assert.*;

public class TestMiniQueue {
    @Test
    public void testBlocking() throws InterruptedException {
        MiniQueue<String> queue = MiniQueue.blocking();

        queue.add("1");
        assertEquals(1, queue.size());

        Stereotypes.auto().runOnce( () -> queue.add("2")).waitForExit();

        assertEquals(2, queue.size());
        assertEquals("1", queue.take());
        assertEquals("2", queue.take());
        assertEquals(0, queue.size());
    }

    @Test
    public void testDropping() throws InterruptedException {
        MiniQueue<String> queue = MiniQueue.dropping();

        queue.add("1");
        assertEquals(0, queue.size());

        Stereotypes.auto().runOnce( () -> queue.add("2")).waitForExit();

        assertEquals(0, queue.size());

        try {
            queue.take();
            fail();
        } catch(Exception e) {

        }
    }
}
