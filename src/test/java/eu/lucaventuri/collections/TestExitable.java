package eu.lucaventuri.collections;

import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.PartialActor;
import eu.lucaventuri.fibry.Stereotypes;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

class Exit extends Exitable {
    Exit() {
        Stereotypes.auto().runOnce( () -> {
            while(!isExiting()) {
                SystemUtils.sleep(1);;
            }

            notifyFinished();;
        });
    }
}
public class TestExitable {
    @Test
    public void testExitable() {
        Exit e = new Exit();

        Stereotypes.auto().runOnce(e::askExit);

        e.waitForExit();
    }

    @Test
    public void testMultiExitable() {
        MultiExitable me=new MultiExitable();
        Exit e0 = new Exit();
        Exit e1 = new Exit();
        Exit e2 = new Exit();
        Exit e3 = new Exit();

        assertFalse(e0.isExiting());
        assertFalse(e0.isFinished());

        me.add(e0);;
        me.add(e1);;
        me.add(e2);;
        me.add(e3);;

        assertFalse(me.isFinished());
        assertFalse(me.isExiting());

        me.remove(e0);
        Stereotypes.auto().runOnce(me::askExit);

        me.waitForExit();

        assertFalse(e0.isExiting());
        assertFalse(e0.isFinished());

        assertTrue(me.isFinished());
        assertTrue(me.isExiting());

        Exit e4 = new Exit();

        assertFalse(e4.isExiting());
        assertFalse(e4.isFinished());

        me.add(e4);

        assertTrue(e4.isExiting());
        assertFalse(e4.isFinished());

        assertTrue(me.isExiting());

        me.waitForExit();

        assertTrue(me.isFinished());
        assertTrue(me.isExiting());
        assertTrue(e4.isExiting());
        assertTrue(e4.isFinished());
        assertFalse(e0.isExiting());
        assertFalse(e0.isFinished());
    }

    @Test
    public void testAutoCloseNonBlocking() throws InterruptedException {
        AtomicInteger num = new AtomicInteger();
        CountDownLatch latchJob = new CountDownLatch(1);
        CountDownLatch latchStart = new CountDownLatch(1);
        AtomicReference<PartialActor<String, Void>> ref = new AtomicReference<>();

        try (Actor<String, Void, Void> actor = ActorSystem.anonymous().newActor((message, thisActor) -> {
            ref.set(thisActor);
            latchJob.countDown();
            SystemUtils.sleep(100);
            num.incrementAndGet();
        }))
        {
            actor.sendMessage("Go! - Exiting: ");
            latchJob.await();
        }

        // Normally at this point the actor has been asked to exit but the message is still under process
        assertTrue(ref.get().isExiting());
        assertEquals(num.get(), 0);
    }

    @Test
    public void testAutoCloseBlocking() throws InterruptedException {
        AtomicInteger num = new AtomicInteger();
        try (Actor<String, Void, Void> actor = ActorSystem.anonymous().strategy(Exitable.CloseStrategy.SEND_POISON_PILL_AND_WAIT).newActor((message, thisActor) -> {
            SystemUtils.sleep(100);
            num.incrementAndGet();
        }))
        {
            actor.sendMessage("Go!");
            actor.sendMessage("Go2!");
        }

        // The try catch will block until the actor is actually dead, because of the CloseStrategy selected
        assertEquals(num.get(), 2);
    }
}
