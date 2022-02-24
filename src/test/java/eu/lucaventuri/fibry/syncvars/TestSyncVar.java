package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.TcpChannel;
import eu.lucaventuri.fibry.distributed.TcpReceiver;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestSyncVar extends TestCase {
    public void testSetValue() {
        SyncVar<String> sv = new SyncVar<>();

        assertNull(sv.getValue());

        sv.setValue("abc");
        assertEquals("abc", sv.getValue());

        sv.setValue("def");
        assertEquals("def", sv.getValue());
    }

    public void testSubscribe() {
        SyncVar<String> sv = new SyncVar<>();
        AtomicBoolean ok = new AtomicBoolean(false);

        sv.subscribe(v -> {
            if (v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                ok.set(true);
            }
        });

        sv.subscribe((oldValue, newValue) -> {
            if (newValue.equals("def")) {
                assertEquals("abc", oldValue);
            }
        });

        sv.setValue("abc");
        assertFalse(ok.get());

        sv.setValue("def");
        assertTrue(ok.get());
    }

    public void testValueHolderActor() {
        SyncVar<String> sv = new SyncVar<>();
        SyncVarConsumer<String> vh = new SyncVarConsumer<>();

        sv.subscribe(vh);

        sv.setValue("abc");
        assertEquals("abc", vh.getValue());

        sv.setValue("def");
        assertEquals("def", vh.getValue());
    }

    public void testSubscribeValueHolder() {
        SyncVar<String> sv = new SyncVar<>();
        AtomicBoolean ok = new AtomicBoolean(false);

        SyncVarConsumer<String> vh = new SyncVarConsumer<>();

        sv.subscribe(vh);
        vh.subscribe(v -> {
            if (v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                ok.set(true);
            }
        });

        sv.setValue("abc");
        assertFalse(ok.get());

        sv.setValue("def");
        assertTrue(ok.get());
    }

    public void testRemoteActors() throws IOException, InterruptedException {
        int port = 20001;
        var ser = OldAndNewValue.<String>getJacksonSerDeser();
        SyncVarConsumer<String> vh = new SyncVarConsumer<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Will receive value notifications
        TcpReceiver.startTcpReceiverProxy(port, "abc", ser, ser, false);
        ActorSystem.named("tcpActor1").newActor(vh);

        vh.subscribe(v -> {
            if (v.getNewValue().equals("xyz"))
                latch.countDown();
        });

        // Will change the value and notify the consumer
        var ch = new TcpChannel<OldAndNewValue<String>, Void>(new InetSocketAddress(port), "abc", null, null, true, "chNoReturn");
        var remoteConsumer = ActorSystem.anonymous().<OldAndNewValue<String>>newRemoteActor("tcpActor1", ch, ser);
        SyncVar<String> sv = new SyncVar<>();

        sv.subscribe(remoteConsumer);
        sv.setValue("xyz");

        latch.await();
    }

    public void testWriteFromThreads() {
        SyncVar<String> sv = new SyncVar<>(); // Messages sent on the same thread
        AtomicBoolean ok = new AtomicBoolean(false);
        var actor = sv.newCompareAndSetActor();
        SyncVarConsumer<String> vh = new SyncVarConsumer<>();

        sv.subscribe(vh);
        vh.subscribe(v -> {
            if (v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                assertFalse(ok.get());
                try {
                    // After the message ends, "xyz" will be processed. This is discouraged, as it could create race conditions
                    actor.sendMessageReturn(new OldAndNewValue<>("def", "xyz")).get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                assertTrue(ok.get());
            }

            if (v.getNewValue().equals("xyz")) {
                assertEquals("def", v.getOldValue());
                ok.set(true);
            }
        });

        sv.setValue("abc");
        assertFalse(ok.get());

        sv.setValue("def");
        assertTrue(ok.get());
    }
}