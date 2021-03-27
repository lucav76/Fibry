package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Bob {
    public static void main(String[] args) throws IOException {
        int proxy2Port = 9802;
        //var ser = new JacksonSerDeser<String, String>(String.class);
        var ser = StringSerDeser.INSTANCE;

        // We could call this a server actor, as it does not need to reach any other actors;
        // It only creates a channel to register itself
        var ch = new TcpChannel<String, String>(new InetSocketAddress(proxy2Port), "secret", ser, ser, true, "bob");
        ActorSystem.named("bob").newActorWithReturn((String message) -> {
            System.out.println("Bob received message " + message);
            return "Bob received: " + message;
        });

        ch.ensureConnection();
        System.out.println("***** Bob UP");
    }
}
