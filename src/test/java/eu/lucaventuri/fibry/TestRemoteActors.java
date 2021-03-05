package eu.lucaventuri.fibry;

import com.sun.net.httpserver.HttpExchange;
import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.fibry.distributed.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.*;

class User implements Serializable {
    public String name;

    User() {
    }

    User(String name) {
        this.name = name;
    }
}

class PhoneNumber implements Serializable {
    public String number;

    PhoneNumber() {
    }

    PhoneNumber(String number) {
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneNumber that = (PhoneNumber) o;
        return Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return "PhoneNumber{" +
                "number='" + number + '\'' +
                '}';
    }
}

@Test
public class TestRemoteActors {
    public void testHttpNoReturn() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19001;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            return "OK";
        });

        var actor = ActorSystem.anonymous().<String>newRemoteActor("actor1", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, false), StringSerDeser.INSTANCE);
        actor.sendMessage("Test");

        latch.await();
    }

    public void testHttpWithReturnGET() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19002;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            Assert.assertEquals("GET", ex.getRequestMethod());

            return ex.getRequestURI().getQuery();
        });

        var actor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("actor2", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, false), StringSerDeser.INSTANCE);
        var result = actor.sendMessageReturn("Test2");

        latch.await();
        System.out.println(result.get());
        Assert.assertEquals("actorName=actor2&type=java.lang.String&waitResult=true&message=Test2", result.get());
    }

    public void testHttpWithReturnPOST() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19003;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            Assert.assertEquals("POST", ex.getRequestMethod());

            return Exceptions.silence(() -> new String(ex.getRequestBody().readAllBytes()), "Error!");
        });

        var actor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("actor3", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.POST, null, null, false), StringSerDeser.INSTANCE);
        var result = actor.sendMessageReturn("Test3");

        latch.await();
        System.out.println(result.get());
        Assert.assertEquals(result.get(), "Test3");
    }

    public void testHttpWithReturnPUT() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19004;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            Assert.assertEquals("PUT", ex.getRequestMethod());

            return Exceptions.silence(() -> new String(ex.getRequestBody().readAllBytes()), "Error!");
        });

        var actor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("actor4", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.PUT, null, null, false), StringSerDeser.INSTANCE);
        var result = actor.sendMessageReturn("Test4");

        latch.await();
        System.out.println(result.get());
        Assert.assertEquals(result.get(), "Test4");
    }

    public void testHttpWithReturnJackson() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19005;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            System.out.println(ex.getRequestURI().getQuery());

            return "{\"number\":\"+47012345678\"}";
        });

        var user = new User("TestUser");
        var phone = new PhoneNumber("+47012345678");
        var actor = ActorSystem.anonymous().<User, PhoneNumber>newRemoteActorWithReturn("actor2", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, false), new JacksonSerDeser<>(PhoneNumber.class));
        var result = actor.sendMessageReturn(user);

        latch.await(1, TimeUnit.SECONDS);
        System.out.println(result.get());
        Assert.assertEquals(phone, result.get());
    }

    public void testHttpWithReturnJavaSerialization() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 19006;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            System.out.println(ex.getRequestURI().getQuery());

            var buf = new ByteArrayOutputStream();

            Exceptions.rethrowRuntime(() -> {
                var os = new ObjectOutputStream(buf);

                os.writeObject(new PhoneNumber("+4787654321"));
            });
            return Base64.getEncoder().encodeToString(buf.toByteArray());
        });

        var user = new User("TestUser2");
        var phone = new PhoneNumber("+4787654321");
        var actor = ActorSystem.anonymous().<User, PhoneNumber>newRemoteActorWithReturn("actor3", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, true), new JavaSerializationSerDeser<>());
        var result = actor.sendMessageReturn(user);

        latch.await(1, TimeUnit.SECONDS);
        System.out.println(result.get());
        Assert.assertEquals(phone, result.get());
    }


    public void testSerializer() {
        var ser = new JacksonSerDeser<String, String>(String.class);
        var serialized = ser.serializeToString("test1");

        System.out.println(serialized);
        System.out.println(ser.deserializeString(serialized));

        var serUser = new JacksonSerDeser<User, User>(User.class);
        var userSerialized = serUser.serializeToString(new User("User1"));
        System.out.println(userSerialized);
        System.out.println(serUser.deserializeString(userSerialized));
    }

    public void testTcpNoReturn() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 20001;
        var ser = new JacksonSerDeser<String, String>(String.class);

        TcpReceiver.startTcpReceiverProxy(port, "abc", ser, ser, false);

        ActorSystem.named("tcpActor1").newActor(str -> {
            Assert.assertEquals(str, "test1");

            latch.countDown();
        });

        var ch = new TcpChannel<String, Void>(new InetSocketAddress(port), "abc", null, null, true, "chNoReturn");
        var actor = ActorSystem.anonymous().<String>newRemoteActor("tcpActor1", ch, ser);

        actor.sendMessage("test1");

        latch.await();
    }

    public void testTcpAnswer() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 20002;
        var ser = new JacksonSerDeser<String, String>(String.class);

        TcpReceiver.startTcpReceiverProxy(port, "abc", ser, ser, false);

        ActorSystem.named("tcpActor2").newActorWithReturn(str -> {
            Assert.assertEquals(str, "test2");

            latch.countDown();

            return str.toString().toUpperCase();
        });

        var ch = new TcpChannel<String, String>(new InetSocketAddress(port), "abc", ser, ser, true, "chAnswer");
        var actor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("tcpActor2", ch, ser);

        var ret = actor.sendMessageReturn("test2");

        latch.await();

        Assert.assertEquals("TEST2", ret.get());
    }

    public void testTcpException() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 20003;
        var ser = new JacksonSerDeser<Integer, Integer>(Integer.class);

        TcpReceiver.startTcpReceiverProxy(port, "abc", ser, ser, false);

        ActorSystem.named("tcpActor3").newActorWithReturn((Integer n) -> {
            Assert.assertEquals(n, Integer.valueOf(11));

            latch.countDown();

            return n/0;
        });

        var ch = new TcpChannel<Integer, Integer>(new InetSocketAddress(port), "abc", ser, ser, true, "chException");
        var actor = ActorSystem.anonymous().<Integer, Integer>newRemoteActorWithReturn("tcpActor3", ch, ser);

        var ret = actor.sendMessageReturn(11);

        latch.await();

        try {
            ret.get();
            Assert.fail();
        } catch(Throwable t) {
            System.err.println("Expected exception: " + t);
            System.err.println("Cause exception: " + t.getCause());
        }
    }

    public void testTcpAnswerMix() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 20004;
        var serMix = new JacksonSerDeser<String, Integer>(Integer.class);
        var serMix2 = new JacksonSerDeser<Integer, String>(String.class);

        TcpReceiver.startTcpReceiverProxy(port, "abc", serMix2, serMix2, false);

        ActorSystem.named("tcpActor4").newActorWithReturn(str -> {
            Assert.assertEquals(str, "test2");

            latch.countDown();

            return str.toString().length();
        });

        var ch = new TcpChannel<String, Integer>(new InetSocketAddress(port), "abc", serMix, serMix, true, "chAnswerMix");
        var actor = ActorSystem.anonymous().<String, Integer>newRemoteActorWithReturn("tcpActor4", ch, serMix);

        var ret = actor.sendMessageReturn("test2");

        latch.await();

        Assert.assertEquals(Integer.valueOf(5), ret.get());
    }

    public void testTcpConnectionDrop() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latchReceived = new CountDownLatch(1);
        CountDownLatch latchConnectionDropped = new CountDownLatch(1);
        int port = 20005;
        var serMix = new JacksonSerDeser<String, Integer>(Integer.class);
        var serMix2 = new JacksonSerDeser<Integer, String>(String.class);
        var actorName = "tcpActor5";

        // Server
        TcpReceiver.startTcpReceiverProxy(port, "abc", serMix2, serMix2, false);

        ActorSystem.named(actorName).newActorWithReturn(str -> {
            Assert.assertEquals(str, "test2");

            latchReceived.countDown();
            try {
                latchConnectionDropped.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return str.toString().length();
        });

        // client
        var ch = new TcpChannel<String, Integer>(new InetSocketAddress(port), "abc", serMix, serMix, true, "chDrop");
        var actor = ActorSystem.anonymous().<String, Integer>newRemoteActorWithReturn(actorName, ch, serMix);

        var ret = actor.sendMessageReturn("test2");

        latchReceived.await();
        ch.drop();
        ch.ensureConnection();
        latchConnectionDropped.countDown();

        var value = ret.get();

        System.out.println("Received value: " + value);
        Assert.assertEquals(Integer.valueOf(5), value);
    }

    public void testTcpAliases() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 20006;
        var serMix = new JacksonSerDeser<String, Integer>(Integer.class);
        var serMix2 = new JacksonSerDeser<Integer, String>(String.class);
        var actorName = "tcpActor6";

        TcpReceiver.startTcpReceiverProxy(port, "abc", serMix2, serMix2, false);

        ActorSystem.named(actorName).newActorWithReturn(str -> {
            Assert.assertEquals(str, "test2");

            latch.countDown();

            return str.toString().length();
        });

        ActorSystem.setAliasResolver(name -> name.replace("alias-", ""));

        var ch = new TcpChannel<String, Integer>(new InetSocketAddress(port), "abc", serMix, serMix, true, "chAnswerMix");
        var actor = ActorSystem.anonymous().<String, Integer>newRemoteActorWithReturn("alias-" + actorName, ch, serMix);

        var ret = actor.sendMessageReturn("test2");

        latch.await();

        Assert.assertEquals(Integer.valueOf(5), ret.get());
    }
}
