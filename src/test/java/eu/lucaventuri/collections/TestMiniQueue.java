package eu.lucaventuri.collections;

import eu.lucaventuri.fibry.MiniQueue;
import eu.lucaventuri.fibry.Stereotypes;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void random() throws InterruptedException {
        var list = List.of(1,2,3,4,5,6,7,8,9,10);

        PriorityMiniQueue<Integer> queue = MiniQueue.priority();

        for(var n: list)
            queue.add(n);

        var list2 = new ArrayList<Integer>();
        Integer num;

        while((num=queue.poll(1, TimeUnit.MILLISECONDS))!=null)
            list2.add(num);


        System.out.println(list);
        System.out.println(list2);

        assertNotEquals(list, list2);
    }

    @Test
    public void randomPoison() throws InterruptedException {
        final int POISON_PILL = -1;
        PriorityMiniQueue<Integer> queue = MiniQueue.priority();
        queue.add(1);
        queue.add(2);
        queue.add(POISON_PILL, 1); // "Poison pill" at lower priority
        queue.add(3);

        int n1 = queue.poll(1, TimeUnit.MILLISECONDS);
        int n2 = queue.poll(1, TimeUnit.MILLISECONDS);

        assertNotEquals(n1, POISON_PILL);
        assertNotEquals(n2, POISON_PILL);
        queue.add(n1);

        int n3 = queue.poll(1, TimeUnit.MILLISECONDS);
        int n4 = queue.poll(1, TimeUnit.MILLISECONDS);

        assertNotEquals(n3, POISON_PILL);
        assertNotEquals(n4, POISON_PILL);

        assertTrue(n1==n3 || n1==n4);

        int n5 = queue.poll(1, TimeUnit.MILLISECONDS);
        assertEquals(n5, POISON_PILL);

        System.out.println(n1);
        System.out.println(n2);
        System.out.println(n3);
        System.out.println(n4);
        System.out.println(n5);
    }
}
