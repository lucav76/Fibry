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

/* Please run TcpExample, which will run this class as well */
public class Bob {
    public static void main(String[] args) throws IOException {
        int proxy1Port = 9801;
        //var ser = new JacksonSerDeser<String, String>(String.class);
        var ser = StringSerDeser.INSTANCE;

        System.out.println("***** Bob UP");

        var ch = new TcpChannel<String, String>(new InetSocketAddress(proxy1Port), "secret", ser, ser, true, "bob");
        ActorSystem.named("bob").newActorWithReturn((String message) -> {
            System.out.println("Bob received message " + message);
            return "Bob received: " + message;
        });

        ch.ensureConnection();

        System.out.println("***** Bob Connected");
    }
}
