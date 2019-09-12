package eu.lucaventuri.fibry;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.Stereotypes;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        Consumer<Integer> master = Stereotypes.auto().workersAsConsumerCreator(n -> {
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
        Function<Integer, CompletableFuture<Integer>> master = Stereotypes.auto().workersAsFunctionCreator(val::addAndGet);

        assertEquals(1, master.apply(1).get().intValue());
        assertEquals(3, master.apply(2).get().intValue());
        assertEquals(6, master.apply(3).get().intValue());

        assertEquals(6, val.get());
    }

    @Test
    public void testMapReducePool() {
        MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce(PoolParameters.fixedSize(4), (Integer n) -> n * n, Integer::sum, 0);

        mr.map(1, 2, 3, 4, 5);
        // 1+4+9+16+25 == 55
        assertEquals(Integer.valueOf(55), mr.get(true));
    }

    @Test
    public void testMapReducePool2() {
        MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce(PoolParameters.fixedSize(4), (Integer n) -> n * n, Integer::sum, 0);
        int num = 100_000;

        for (int i = 0; i < num; i++) {
            mr.map(i % 2);
        }

        System.out.println("Messages sent");

        System.out.println(mr.get(true));
        assertEquals(Integer.valueOf(num / 2), mr.get(false));
    }

    @Test
    public void testMapReduceSpawner() {
        int res = Stereotypes.def().mapReduce((Integer n) -> n * n, Integer::sum, 0).map(1, 2, 3, 4, 5).get(true);

        // 1+4+9+16+25 == 55
        assertEquals(55, res);
    }

    @Test
    public void testMapReduceSpawner2() {
        MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce((Integer n) -> n * n, Integer::sum, 0);
        int num = 1000; // Too slow without fibers

        for (int i = 0; i < num; i++) {
            mr.map(i % 2);
        }

        System.out.println(mr.get(true));
        assertEquals(Integer.valueOf(num / 2), mr.get(false));
    }

    @Test
    public void testMapWithExceptions() {
        MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce((Integer n) -> {
            throw new IllegalArgumentException();
        }, Integer::sum, 0);
        int num = 2; // Too slow without fibers

        for (int i = 0; i < num; i++) {
            mr.map(i % 2);
        }

        System.out.println(mr.get(true));
        assertEquals(Integer.valueOf(0), mr.get(false));
    }

    @Test
    public void testMapWithExceptions2() {
        MapReducer<Integer, Integer> mr = Stereotypes.def().mapReduce((Integer n) -> 100 / n, Integer::sum, 0);

        mr.map(100);
        mr.map(50);
        mr.map(0);
        mr.map(20);
        mr.map(25);

        System.out.println(mr.get(true));
        assertEquals(Integer.valueOf(12), mr.get(false));
    }

    @Test
    public void testChat() throws InterruptedException {
        String prefix = "CHAT|";
        String userName = "tom";
        String userName2 = "max";
        CountDownLatch persistenceLatch = new CountDownLatch(2);
        CountDownLatch chatLatch = new CountDownLatch(1);
        CountDownLatch chatLatch2 = new CountDownLatch(1);

        BiConsumer<String, String> chatSystem = Stereotypes.def().chatSystem(prefix, message -> {
            persistenceLatch.countDown();
            System.out.println("Persisted message: " + message);
        });


        Actor<Object, Void, Void> actor1 = ActorSystem.named(prefix + userName).newActor(message -> {
            chatLatch.countDown();
            System.out.println("Online message for tom: " + message);
            chatSystem.accept(userName2, "Hi " + userName2.toUpperCase() + "!");
        });

        Actor<Object, Void, Void> actor2 = ActorSystem.named(prefix + userName2).newActor(message -> {
            chatLatch2.countDown();
            System.out.println("Online message for max: " + message);
        });

        chatSystem.accept(userName, "Test!");

        chatLatch.await();
        chatLatch2.await();
        persistenceLatch.await();

        actor1.sendPoisonPill();
        actor2.sendPoisonPill();
    }

    @Test
    public void testChatNoPersistence() throws InterruptedException {
        String prefix = "CHAT2|";
        String userName = "tom";
        String userName2 = "max";
        CountDownLatch chatLatch = new CountDownLatch(1);
        CountDownLatch chatLatch2 = new CountDownLatch(1);

        BiConsumer<String, String> chatSystem = Stereotypes.def().chatSystem(prefix, null);

        Actor<Object, Void, Void> actor1 = ActorSystem.named(prefix + userName).newActor(message -> {
            chatLatch.countDown();
            System.out.println("Online message for tom: " + message);
            chatSystem.accept(userName2, "Hi " + userName2.toUpperCase() + "!");
        });

        Actor<Object, Void, Void> actor2 = ActorSystem.named(prefix + userName2).newActor(message -> {
            chatLatch2.countDown();
            System.out.println("Online message for max: " + message);
        });

        chatSystem.accept(userName, "Test!");

        chatLatch.await();
        chatLatch2.await();

        actor1.sendPoisonPill();
        actor2.sendPoisonPill();
    }

    @Test
    public void testLocalForward() throws IOException {
        CountDownLatch latch = new CountDownLatch(1);

        SinkActorSingleTask<Void> actorAcceptor = Stereotypes.def().tcpAcceptor(1000, socket1000 -> {
            try {
                byte ar[] = new byte[4];
                SystemUtils.keepReadingStream(socket1000.getInputStream(), ar);
                System.out.println("Received on port 1000: " + new String(ar));
                assertEquals("test", new String(ar));
                socket1000.getOutputStream().write("TEST".getBytes());
                System.out.println("Sent TEST through port 1000");
                latch.await();
                System.out.println("Server shutting down");
            } catch (IOException | InterruptedException e) {
                fail(e.toString());
            } finally {
                SystemUtils.close(socket1000);
            }
        }, false);

        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1001, 1000, true, true);
        Socket socket1001 = new Socket(InetAddress.getLocalHost(), 1001);

        //SystemUtils.sleep(1000);
        try {
            socket1001.getOutputStream().write("test".getBytes());
            byte ar[] = new byte[4];
            SystemUtils.keepReadingStream(socket1001.getInputStream(), ar);
            System.out.println("Received on port 1001: " + new String(ar));
            assertEquals("TEST", new String(ar));

            latch.countDown();
        } finally {
            SystemUtils.close(socket1001);
            actorForwarding.askExitAndWait();
            actorAcceptor.askExitAndWait();
        }
    }

    @Test
    public void testBinaryLocalForward() throws IOException {
        byte[] testArrary = prepareBinaryArray();

        CountDownLatch latch = new CountDownLatch(1);
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1001, 1000, false, false);
        Socket socket1001 = new Socket(InetAddress.getLocalHost(), 1001);

        //SystemUtils.sleep(1000);
        socket1001.getOutputStream().write(testArrary);
        byte ar[] = new byte[testArrary.length];
        SystemUtils.keepReadingStream(socket1001.getInputStream(), ar);
        System.out.println("Received on port 1001 " + ar.length + " bytes");
        for (int i = 0; i < ar.length; i++)
            assertEquals((byte) i, ar[i]);

        latch.countDown();
        SystemUtils.close(socket1001);
        actorForwarding.askExitAndWait();
        actorAcceptor.askExitAndWait();
    }

    @Test
    public void testDoubleBinaryLocalForward() throws IOException {
        byte[] testArrary = prepareBinaryArray();

        CountDownLatch latch = new CountDownLatch(1);
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1001, 1000, true, true);
        SinkActorSingleTask<Void> actorForwarding2 = Stereotypes.def().forwardLocal(1002, 1001, true, true);
        Socket socket1002 = new Socket(InetAddress.getLocalHost(), 1002);

        //SystemUtils.sleep(1000);
        socket1002.getOutputStream().write(testArrary);
        byte ar[] = new byte[testArrary.length];
        SystemUtils.keepReadingStream(socket1002.getInputStream(), ar);
        System.out.println("Received on port 1002 " + ar.length + " bytes");
        for (int i = 0; i < ar.length; i++)
            assertEquals((byte) i, ar[i]);

        latch.countDown();
        SystemUtils.close(socket1002);
        actorForwarding.askExitAndWait();
        actorForwarding2.askExitAndWait();
        actorAcceptor.askExitAndWait();
    }

    @Test
    public void testDoubleBinaryLocalForwardSplit() throws IOException {
        byte[] testArrary = prepareBinaryArray();

        CountDownLatch latch = new CountDownLatch(1);
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1001, 1000, true, true);
        SinkActorSingleTask<Void> actorForwarding2 = Stereotypes.def().forwardLocal(1002, 1001, true, true);
        Socket socket1002 = new Socket(InetAddress.getLocalHost(), 1002);

        //SystemUtils.sleep(1000);
        socket1002.getOutputStream().write(testArrary, 0, 1);
        socket1002.getOutputStream().write(testArrary, 1, 1023);
        socket1002.getOutputStream().write(testArrary, 1024, 511 * 1024);
        socket1002.getOutputStream().write(testArrary, 512 * 1024, 512 * 1024);

        byte ar[] = new byte[testArrary.length];
        SystemUtils.keepReadingStream(socket1002.getInputStream(), ar, 0, 255 * 1024);
        SystemUtils.keepReadingStream(socket1002.getInputStream(), ar, 255 * 1024, 1024);
        SystemUtils.keepReadingStream(socket1002.getInputStream(), ar, 256 * 1024, 512 * 1024);
        SystemUtils.keepReadingStream(socket1002.getInputStream(), ar, 768 * 1024, 256 * 1024);
        System.out.println("Received on port 1002 " + ar.length + " bytes");
        for (int i = 0; i < ar.length; i++)
            assertEquals((byte) i, ar[i]);

        latch.countDown();
        SystemUtils.close(socket1002);
        actorForwarding.askExitAndWait();
        actorForwarding2.askExitAndWait();
        actorAcceptor.askExitAndWait();
    }

    private byte[] prepareBinaryArray() {
        byte testArrary[] = new byte[1024 * 1024];

        for (int i = 0; i < testArrary.length; i++)
            testArrary[i] = (byte) i;
        return testArrary;
    }

    private SinkActorSingleTask<Void> createBinaryAcceptor(byte[] testArrary, CountDownLatch latch) throws IOException {
        return Stereotypes.def().tcpAcceptor(1000, socket1000 -> {
            try {
                byte ar[] = new byte[testArrary.length];
                SystemUtils.keepReadingStream(socket1000.getInputStream(), ar);
                System.out.println("Received on port 1000 " + ar.length + " bytes");
                for (int i = 0; i < ar.length; i++)
                    assertEquals((byte) i, ar[i]);
                socket1000.getOutputStream().write(ar);
                System.out.println("Sent TEST through port 1000");
                latch.await();
                System.out.println("Server shutting down");
                SystemUtils.close(socket1000);
            } catch (IOException | InterruptedException e) {
                fail(e.toString());
            }
        }, false);
    }

    @Test
    public void testTransferStreams() throws IOException {
        byte ar[] = {(byte) 127, (byte) 128, (byte) 250, (byte) 255, (byte) 256, (byte) 510, (byte) 511, (byte) 512, (byte) -1};
        ByteArrayInputStream bais = new ByteArrayInputStream(ar);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        SystemUtils.transferStream(bais, baos, null);

        byte ar2[] = baos.toByteArray();

        assertEquals(ar.length, ar2.length);

        for (int i = 0; i < ar.length; i++)
            assertEquals(ar[i], ar2[i]);
    }
}
