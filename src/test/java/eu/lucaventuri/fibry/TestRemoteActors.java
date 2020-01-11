package eu.lucaventuri.fibry;

import com.sun.net.httpserver.HttpExchange;
import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.fibry.distributed.HttpChannel;
import eu.lucaventuri.fibry.distributed.JacksonSerDeser;
import eu.lucaventuri.fibry.distributed.JavaSerializationSerDeser;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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
    public void testNoReturn() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9001;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            return "OK";
        });

        var actor = ActorSystem.anonymous().<String>newRemoteActor("actor1", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, false), StringSerDeser.INSTANCE);
        actor.sendMessage("Test");

        latch.await();
    }

    public void testWithReturnGET() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9002;

        Stereotypes.def().embeddedHttpServer(port, (HttpExchange ex) -> {
            latch.countDown();
            Assert.assertEquals("GET", ex.getRequestMethod());

            return ex.getRequestURI().getQuery();
        });

        var actor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("actor2", new HttpChannel("http://localhost:" + port + "/", HttpChannel.HttpMethod.GET, null, null, false), StringSerDeser.INSTANCE);
        var result = actor.sendMessageReturn("Test2");

        latch.await();
        System.out.println(result.get());
        Assert.assertEquals("actorName=actor2&type=java.lang.String&message=Test2", result.get());
    }

    public void testWithReturnPOST() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9003;

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

    public void testWithReturnPUT() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9004;

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

    public void testWithReturnJackson() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9005;

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

    public void testWithReturnJavaSerialization() throws IOException, InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);
        int port = 9006;

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
}
