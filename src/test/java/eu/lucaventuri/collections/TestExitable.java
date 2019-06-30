package eu.lucaventuri.collections;

import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;
import org.junit.Test;
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
}
