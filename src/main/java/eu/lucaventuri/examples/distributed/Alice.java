package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* Please run TcpExample, which will run this class as well */
public class Alice {
    public static void main(String[] args) {
        int proxy1Port = 9801;
        //var ser = new JacksonSerDeser<String, String>(String.class);
        var ser = StringSerDeser.INSTANCE;

        System.out.println("***** Alice UP");

        var ch = new TcpChannel<String, String>(new InetSocketAddress(proxy1Port), "secret", ser, ser, true, "Alice->proxyA");
        var bob = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("bob", ch, ser);

        for (int i = 1; i <= 5; i++) {
            try {
                var answer = bob.sendMessageReturn("Call from Alice").get(1, TimeUnit.SECONDS);

                System.out.println("Bob answer: " + answer);
                System.exit(0);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println(e);
                System.out.println("Bob did not pick up call " + i);
                SystemUtils.sleep(1000);
            }
        }

        System.exit(1);
    }
}
