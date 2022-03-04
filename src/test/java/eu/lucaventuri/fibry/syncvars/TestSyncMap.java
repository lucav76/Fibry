package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.TcpChannel;
import eu.lucaventuri.fibry.distributed.TcpReceiver;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestSyncMap extends TestCase {
    public void testSetValue() {
        SyncMap<String> sv = new SyncMap<>();

        assertNull(sv.getValue("1"));

        sv.setValue("1", "abc");
        assertEquals("abc", sv.getValue("1"));

        sv.setValue("2", "def");
        assertEquals("def", sv.getValue("2"));
        assertEquals("abc", sv.getValue("1"));
    }

    public void testSubscribe() {
        SyncMap<String> sv = new SyncMap<>();
        AtomicBoolean ok = new AtomicBoolean(false);

        sv.subscribe(v -> {
            if (v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                ok.set(true);
            }
        });

        sv.subscribe((name, oldValue, newValue) -> {
            if (newValue.equals("def")) {
                assertEquals("abc", oldValue);
            }
        });

        sv.setValue("1", "abc");
        assertFalse(ok.get());

        sv.setValue("1", "def");
        assertTrue(ok.get());
    }

    public void testValueHolderActor() {
        SyncMap<String> sv = new SyncMap<>();
        SyncMapConsumer<String> vh = new SyncMapConsumer<>();

        sv.subscribe(vh);

        sv.setValue("0", "abc");
        assertEquals("abc", vh.getValue("0"));

        sv.setValue("0", "def");
        assertEquals("def", vh.getValue("0"));
    }

    public void testSubscribeValueHolder() {
        SyncMap<String> sv = new SyncMap<>();
        AtomicBoolean ok = new AtomicBoolean(false);

        SyncMapConsumer<String> vh = new SyncMapConsumer<>();

        sv.subscribe(vh);
        vh.subscribe(v -> {
            if (v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                ok.set(true);
            }
        });

        sv.setValue("1", "abc");
        assertFalse(ok.get());

        sv.setValue("1", "def");
        assertTrue(ok.get());
    }

    public void testRemoteActors() throws IOException, InterruptedException {
        int port = 20021;
        var ser = OldAndNewValueWithName.<String>getJacksonSerDeser();
        SyncMapConsumer<String> vh = new SyncMapConsumer<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Will receive value notifications
        TcpReceiver.startTcpReceiverProxy(port, "abc", ser, ser, false);
        ActorSystem.named("SyncMapActor1").newActor(vh);

        vh.subscribe(v -> {
            if (v.getName().equals("1") && v.getNewValue().equals("xyz"))
                latch.countDown();
        });

        // Will change the value and notify the consumer
        var ch = new TcpChannel<OldAndNewValueWithName<String>, Void>(new InetSocketAddress(port), "abc", null, null, true, "chNoReturn");
        var remoteConsumer = ActorSystem.anonymous().<OldAndNewValueWithName<String>>newRemoteActor("SyncMapActor1", ch, ser);
        SyncMap<String> sv = new SyncMap<>();

        sv.subscribe(remoteConsumer);
        sv.setValue("0", "xyz");
        sv.setValue("1", "xyz");

        latch.await(2, TimeUnit.SECONDS);
    }

    public void testWriteFromThreads() {
        SyncMap<String> sv = new SyncMap<>(); // Messages sent on the same thread
        AtomicBoolean ok = new AtomicBoolean(false);
        var actor = sv.newCompareAndSetActor();
        SyncMapConsumer<String> vh = new SyncMapConsumer<>();

        sv.subscribe(vh);
        vh.subscribe(v -> {
            if (v.getName().equals("1") && v.getNewValue().equals("def")) {
                assertEquals("abc", v.getOldValue());
                assertFalse(ok.get());
                try {
                    // After the message ends, "xyz" will be processed. This is discouraged, as it could create race conditions
                    actor.sendMessageReturn(new OldAndNewValueWithName<>(v.getName(), "def", "xyz")).get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                assertTrue(ok.get());
            }

            if (v.getName().equals("1") && v.getNewValue().equals("xyz")) {
                assertEquals("def", v.getOldValue());
                ok.set(true);
            }
        });

        sv.setValue("1", "abc");
        assertFalse(ok.get());

        sv.setValue("0", "def");
        assertFalse(ok.get());

        sv.setValue("1", "def");
        assertTrue(ok.get());
    }
}