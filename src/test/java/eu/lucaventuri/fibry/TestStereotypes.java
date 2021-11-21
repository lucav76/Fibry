package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Mergeable;
import eu.lucaventuri.common.MergeableParallelBatches;
import eu.lucaventuri.common.SystemUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

class M extends BatchMergeable implements MergeableParallelBatches {
    private final String key;
    private final String value;
    private final int numMerged;

    M(String key, String value) {
        this(key, value, 1);
    }

    M(String key, String value, int numMerged) {
        this.key = key;
        this.value = value;
        this.numMerged = numMerged;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Mergeable mergeWith(Mergeable m) {
        if (!key.equals(m.getKey()))
            throw new IllegalArgumentException("Cannot merge two different keys: " + key + " vs " + m.getKey());

        Mergeable newM = new M(key, value + ((M) m).value, numMerged + 1);

        newM.mergeSignalWith(this);
        newM.mergeSignalWith(m);

        return newM;
    }

    @Override
    public String toString() {
        return key + ": " + value;
    }

    @Override
    public int elementsMerged() {
        return numMerged;
    }
}

public class TestStereotypes {

    @BeforeClass
    public static void setupClass() {
        ActorSystem.setDefaultPollTimeoutMs(10);
        Stereotypes.setDebug(true);
    }

    @AfterClass
    public static void teardownClass() {
        ActorSystem.setDefaultPollTimeoutMs(Integer.MAX_VALUE);
    }

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
        int refPort = 10010;

        SinkActorSingleTask<Void> actorAcceptor = Stereotypes.def().tcpAcceptor(refPort, socket -> {
            try {
                byte ar[] = new byte[4];
                SystemUtils.keepReadingStream(socket.getInputStream(), ar);
                System.out.println("Received on port 1010: " + new String(ar));
                assertEquals("test", new String(ar));
                socket.getOutputStream().write("TEST".getBytes());
                System.out.println("Sent TEST through port 1010");
                latch.await();
                System.out.println("Server shutting down");
            } catch (IOException | InterruptedException e) {
                fail(e.toString());
            } finally {
                SystemUtils.close(socket);
            }
        }, false, 50);

        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(refPort + 1, refPort, true, true, 50);
        Socket socket1011 = new Socket(InetAddress.getLocalHost(), refPort + 1);

        //SystemUtils.sleep(1000);
        try {
            socket1011.getOutputStream().write("test".getBytes());
            byte ar[] = new byte[4];
            SystemUtils.keepReadingStream(socket1011.getInputStream(), ar);
            System.out.println("Received on port 1011: " + new String(ar));
            assertEquals("TEST", new String(ar));

            latch.countDown();
        } finally {
            long start = System.currentTimeMillis();
            System.out.println("Finally 1");
            SystemUtils.close(socket1011);
            System.out.println("Finally 2 " + (System.currentTimeMillis() - start));
            actorForwarding.askExit();
            System.out.println("Finally 2b " + (System.currentTimeMillis() - start));
            actorForwarding.waitForExit();
            System.out.println("Finally 3 " + (System.currentTimeMillis() - start));
            //actorAcceptor.askExitAndWait();
            actorAcceptor.askExit();
            System.out.println("Finally 4 " + (System.currentTimeMillis() - start));
            actorAcceptor.waitForExit();
            System.out.println("Finally 5 " + (System.currentTimeMillis() - start));
        }

        actorAcceptor.askExitAndWait();
        actorForwarding.askExitAndWait();
        SystemUtils.close(socket1011);
    }

    @Test
    public void testBinaryLocalForward() throws IOException {
        byte[] testArrary = prepareBinaryArray();
        int refPort = 10020;

        CountDownLatch latch = new CountDownLatch(1);
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch, refPort);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(refPort + 1, refPort, false, false, 50);
        Socket socket1001 = new Socket(InetAddress.getLocalHost(), refPort + 1);

        //SystemUtils.sleep(1000);
        socket1001.getOutputStream().write(testArrary);
        byte ar[] = new byte[testArrary.length];
        SystemUtils.keepReadingStream(socket1001.getInputStream(), ar);
        System.out.println("Received on port " + (refPort + 1) + " " + ar.length + " bytes");
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
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch, 1030);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1031, 1030, true, true, 10);
        SinkActorSingleTask<Void> actorForwarding2 = Stereotypes.def().forwardLocal(1032, 1031, true, true, 10);
        Socket socket1032 = new Socket(InetAddress.getLocalHost(), 1032);

        //SystemUtils.sleep(1000);
        socket1032.getOutputStream().write(testArrary);
        byte ar[] = new byte[testArrary.length];
        SystemUtils.keepReadingStream(socket1032.getInputStream(), ar);
        System.out.println("Received on port 1002 " + ar.length + " bytes");
        for (int i = 0; i < ar.length; i++)
            assertEquals((byte) i, ar[i]);

        latch.countDown();
        SystemUtils.close(socket1032);
        actorForwarding.askExitAndWait();
        actorForwarding2.askExitAndWait();
        actorAcceptor.askExitAndWait();
    }

    @Test
    public void testDoubleBinaryLocalForwardSplit() throws IOException {
        byte[] testArrary = prepareBinaryArray();

        CountDownLatch latch = new CountDownLatch(1);
        SinkActorSingleTask<Void> actorAcceptor = createBinaryAcceptor(testArrary, latch, 1040);
        SinkActorSingleTask<Void> actorForwarding = Stereotypes.def().forwardLocal(1041, 1040, true, true, 10);
        SinkActorSingleTask<Void> actorForwarding2 = Stereotypes.def().forwardLocal(1042, 1041, true, true, 10);
        Socket socket1002 = new Socket(InetAddress.getLocalHost(), 1042);

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

    private SinkActorSingleTask<Void> createBinaryAcceptor(byte[] testArrary, CountDownLatch latch, int port) throws IOException {
        return Stereotypes.def().tcpAcceptor(port, socket -> {
            try {
                byte ar[] = new byte[testArrary.length];
                SystemUtils.keepReadingStream(socket.getInputStream(), ar);
                System.out.println("Received on port " + port + " " + ar.length + " bytes");
                for (int i = 0; i < ar.length; i++)
                    assertEquals((byte) i, ar[i]);
                socket.getOutputStream().write(ar);
                System.out.println("Sent TEST through port 1000");
                latch.await();
                System.out.println("Server shutting down");
                SystemUtils.close(socket);
            } catch (IOException | InterruptedException e) {
                fail(e.toString());
            }
        }, false, 50);
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

    @Test
    public void testPipeline() throws ExecutionException, InterruptedException {
        Actor<String, String, Void> actUpper = ActorSystem.anonymous().newActorWithReturn((Function<String, String>) String::toUpperCase);
        MessageOnlyActor<Integer, String, Void> actPipeline = Stereotypes.def().pipelineTo((Integer n) -> "test" + n, actUpper);

        assertEquals(actPipeline.sendMessageReturn(3).get(), "TEST3");

        actPipeline.sendPoisonPill();
        actUpper.waitForExit();
    }

    @Test
    public void testPipeline2() throws ExecutionException, InterruptedException {
        Actor<String, String, Void> actUpper = ActorSystem.anonymous().newActorWithReturn((Function<String, String>) String::toUpperCase);
        MessageOnlyActor<String, String, Void> actPipeline2 = Stereotypes.def().pipelineTo((String text) -> text + text, actUpper);
        MessageOnlyActor<Integer, String, Void> actPipeline = Stereotypes.def().pipelineTo((Integer n) -> "test" + n, actPipeline2);

        assertEquals(actPipeline.sendMessageReturn(3).get(), "TEST3TEST3");

        actPipeline.sendPoisonPill();
        actUpper.waitForExit();
    }

    @Test
    public void testBatches() {
        final List<String> listJoined = new Vector<>();
        BaseActor<String, Void, Void> actor = Stereotypes.def().batchProcessList(list -> listJoined.add(String.join(",", list)), 3, 100, 1, true);

        createMessagesAndCheck(listJoined, actor);

        checkSize(listJoined);
    }

    @Test
    public void testMergeableBatches() {

        Map<String, Mergeable> mapJoined = new ConcurrentHashMap<>();
        BaseActor<Mergeable, Void, Void> actor = Stereotypes.def().batchProcessMerge(map -> map.forEach(mapJoined::put), 30, 100_000, 1, true);

        actor.sendMessage(new M("a", "1"));
        actor.sendMessage(new M("b", "2"));
        actor.sendMessage(new M("c", "3"));
        actor.sendMessage(new M("a", "4"));
        SystemUtils.sleep(200);
        actor.sendMessage(new M("d", "4.5"));
        actor.sendMessage(new M("a", "5"));
        actor.sendPoisonPill();
        actor.waitForExit();

        assertEquals(4, mapJoined.size());
        assertEquals("a: 145", mapJoined.get("a").toString());
        assertEquals("b: 2", mapJoined.get("b").toString());
        assertEquals("c: 3", mapJoined.get("c").toString());
        assertEquals("d: 4.5", mapJoined.get("d").toString());
    }

    @Test
    public void testMergeableBatchesDelivery() throws ExecutionException, InterruptedException {
        Map<String, Mergeable> mapJoined = new ConcurrentHashMap<>();
        BaseActor<Mergeable, Void, Void> actor = Stereotypes.def().batchProcessMerge(map -> map.forEach(mapJoined::put), 30, 50, 1, true);

        actor.sendMessage(new M("a", "1"));
        actor.sendMessage(new M("b", "2"));
        actor.sendMessage(new M("c", "3"));
        actor.sendMessage(new M("a", "4"));
        actor.sendMessage(new M("d", "4.5"));
        M m = new M("a", "5");
        actor.sendMessageReturn(m).get();

        assertEquals(0, mapJoined.size());

        m.awaitBatchProcessing();
        assertEquals(4, mapJoined.size());
        assertEquals("a: 145", mapJoined.get("a").toString());
        assertEquals("b: 2", mapJoined.get("b").toString());
        assertEquals("c: 3", mapJoined.get("c").toString());
        assertEquals("d: 4.5", mapJoined.get("d").toString());

        actor.sendPoisonPill();
        actor.waitForExit();
    }

    private void checkSize(List<String> listJoined) {
        if (listJoined.size() == 3)
            assertEquals("e,f,g", listJoined.get(2));
        else {
            assertEquals("e", listJoined.get(2));
            assertEquals("f,g", listJoined.get(3));
        }
    }

    private String extractList(List<String> listJoined) {
        StringBuffer sb = new StringBuffer();

        for (String el : listJoined)
            sb.append("'" + el + "'   ");

        return sb.toString();
    }

    @Test
    public void testBatches2() {
        final List<String> listJoined = new Vector<>();
        BaseActor<String, Void, Void> actor = Stereotypes.def().batchProcessList(list -> listJoined.add(String.join(",", list)), 3, 100, 1, false);

        createMessagesAndCheck(listJoined, actor);

        checkSize(listJoined);
    }

    private void createMessagesAndCheck(List<String> listJoined, BaseActor<String, Void, Void> actor) {
        actor.sendMessage("a");
        actor.sendMessage("b");
        actor.sendMessage("c");
        actor.sendMessage("d");
        SystemUtils.sleep(200);
        actor.sendMessage("e");
        actor.sendMessage("f");
        actor.sendMessage("g");
        actor.sendPoisonPill();
        actor.waitForExit();

        assertEquals("a,b,c", listJoined.get(0));
        assertEquals("d", listJoined.get(1));

        System.out.println(extractList(listJoined));
    }

    @Test
    public void testBatchesGroupBy() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        BaseActor<String, Void, Void> actor = Stereotypes.def().batchProcessGroupBy(mapGroupBy -> {
            if (mapGroupBy.get("A") == 3)
                latch.countDown();
        }, 5, 1_000_000, 1, true);


        actor.sendMessage("A");
        actor.sendMessage("A");
        actor.sendMessage("B");
        actor.sendMessage("C");
        actor.sendMessage("D");
        actor.sendMessage("D");
        actor.sendMessage("B");
        actor.sendMessage("A");
        actor.sendMessage("E");

        latch.await();
        actor.sendPoisonPill();
        actor.waitForExit();  // Added to the batch when the actor exits
    }

    @Test
    public void testBatchesGroupByWait() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        BaseActor<String, Void, Void> actor = Stereotypes.def().batchProcessGroupBy(mapGroupBy -> {
            if (mapGroupBy.get("A") == 3)
                latch.countDown();
        }, 10, 10, 1, true);


        actor.sendMessage("A");
        actor.sendMessage("A");
        actor.sendMessage("B");
        actor.sendMessage("C");
        actor.sendMessage("D");
        actor.sendMessage("D");
        actor.sendMessage("B");
        actor.sendMessage("A");

        latch.await();
        actor.sendPoisonPill();
        actor.waitForExit();  // Added to the batch when the actor exits
    }

    @Test
    public void testBatchesGroupByCleaning() {
        final AtomicReference<Map<String, Long>> map = new AtomicReference<>();
        BaseActor<String, Void, Void> actor = Stereotypes.def().batchProcessGroupBy(res -> map.set(new HashMap<>(res)), 10, 1_000_000, 1, true);

        actor.sendMessage("A");
        actor.sendMessage("A");
        actor.sendMessage("B");
        actor.sendMessage("C");
        actor.sendMessage("D");
        actor.sendMessage("D");
        actor.sendMessage("B");
        actor.sendMessage("A");

        actor.sendPoisonPill();
        actor.waitForExit();  // Added to the batch when the actor exits

        assertEquals(4, map.get().size());
        assertEquals(Long.valueOf(3), map.get().get("A"));
        assertEquals(Long.valueOf(2), map.get().get("B"));
        assertEquals(Long.valueOf(1), map.get().get("C"));
        assertEquals(Long.valueOf(2), map.get().get("D"));
    }

    @Test
    public void testParallelBatchesBySize() throws InterruptedException {
        testParallelBatches(3, 10_000_000);
    }

    @Test
    public void testParallelBatchesByTimeout() throws InterruptedException {
        testParallelBatches(5, 50);
        return;
    }

    private void testParallelBatches(int parallelBatchSize, int timeout) throws InterruptedException {
        CountDownLatch countA = new CountDownLatch(1);
        CountDownLatch countB = new CountDownLatch(1);
        CountDownLatch countC = new CountDownLatch(1);
        BaseActor<M, Void, Void> actor = Stereotypes.def().batchProcessMergeParallelBatches(
                m -> {
                    System.out.println(m);
                    if (m.getKey().equals("A")) {
                        assertEquals(countA.getCount(), 1);
                        countA.countDown();
                        assertEquals(m.toString(), "A: 123");
                    }
                    if (m.getKey().equals("B")) {
                        assertEquals(countB.getCount(), 1);
                        countB.countDown();
                        assertEquals(m.toString(), "B: 456");
                    }
                    if (m.getKey().equals("C")) {
                        assertEquals(countA.getCount(), 0);
                        assertEquals(countB.getCount(), 0);
                        assertEquals(countC.getCount(), 1);
                        countC.countDown();
                        assertEquals(m.toString(), "C: 0"); // Sent at cleanup if not by timeout
                    }
                }, parallelBatchSize, timeout, 1, false);

        actor.sendMessage(new M("A", "1"));
        actor.sendMessage(new M("B", "4"));
        actor.sendMessage(new M("A", "2"));
        actor.sendMessage(new M("B", "5"));
        actor.sendMessage(new M("B", "6"));
        actor.sendMessage(new M("C", "0"));
        actor.sendMessage(new M("A", "3"));


        countA.await();
        countB.await();
        actor.sendPoisonPill();
        actor.waitForExit();  // Added to the batch when the actor exits

        countC.await();
    }

    @Test
    public void testDownloader() throws IOException, InterruptedException {
        AtomicInteger num = new AtomicInteger();
        var server = Stereotypes.auto().embeddedHttpServer(1001, new Stereotypes.HttpStringWorker("/test", ex -> "100"),
                new Stereotypes.HttpStringWorker("/test2", ex -> {
                    throw new RuntimeException("Expected");
                }), new Stereotypes.HttpStringWorker("/test3", ex -> {
                    if (num.incrementAndGet() == 1)
                        throw new RuntimeException("Expected");

                    return "200";
                }));
        CountDownLatch latch = new CountDownLatch(4);

        try {
            Stereotypes.auto().downloader().sendMessage(new Stereotypes.HttpUrlDownload<String>("http://localhost:1001/test", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.OK);
                assertEquals(r.response.statusCode(), 200);
                assertEquals(r.response.body(), "100");
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().downloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<String>("http://localhost:1001/dummy", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.FAILED);
                assertEquals(r.response.statusCode(), 404);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().downloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<String>("http://localhost:10011/dummy", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.EXCEPTION);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().downloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<String>("http://localhost:1001/test2", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.FAILED);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().downloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<String>("http://localhost:1001/test3", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.OK);
                assertEquals(r.response.statusCode(), 200);
                assertEquals(r.response.body(), "200");
                latch.countDown();
            }, 0, 0));

            latch.await();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testBytesDownloader() throws IOException, InterruptedException {
        AtomicInteger num = new AtomicInteger();
        var server = Stereotypes.auto().embeddedHttpServer(1001, new Stereotypes.HttpStringWorker("/test", ex -> "100"),
                new Stereotypes.HttpStringWorker("/test2", ex -> {
                    throw new RuntimeException("Expected");
                }), new Stereotypes.HttpStringWorker("/test3", ex -> {
                    if (num.incrementAndGet() == 1)
                        throw new RuntimeException("Expected");

                    return "200";
                }));
        CountDownLatch latch = new CountDownLatch(4);

        try {
            Stereotypes.auto().binaryDownloader().sendMessage(new Stereotypes.HttpUrlDownload<byte[]>("http://localhost:1001/test", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.OK);
                assertEquals(r.response.statusCode(), 200);
                assertArrayEquals(r.response.body(), new byte[]{'1', '0', '0'});
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().binaryDownloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<byte[]>("http://localhost:1001/dummy", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.FAILED);
                assertEquals(r.response.statusCode(), 404);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().binaryDownloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<byte[]>("http://localhost:10011/dummy", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.EXCEPTION);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().binaryDownloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<byte[]>("http://localhost:1001/test2", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.FAILED);
                latch.countDown();
            }, 0, 0));

            Stereotypes.auto().binaryDownloader(new Scheduler(), 1, true).sendMessage(new Stereotypes.HttpUrlDownload<byte[]>("http://localhost:1001/test3", r -> {
                assertEquals(r.reason, Stereotypes.HttpResult.Reason.OK);
                assertEquals(r.response.statusCode(), 200);
                assertArrayEquals(r.response.body(), new byte[]{'2', '0', '0'});
                latch.countDown();
            }, 0, 0));

            latch.await();
        } finally {
            server.stop(0);
        }
    }
}
