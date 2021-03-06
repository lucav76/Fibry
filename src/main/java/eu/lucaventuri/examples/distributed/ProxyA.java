package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpChannel;
import eu.lucaventuri.fibry.distributed.TcpReceiver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public class ProxyA {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        int port = 9801;
        int portProxyB = 9802;

        System.out.println("*** Proxy A UP");

        //var ser = new JacksonSerDeser<String, String>(String.class);
        var ser = StringSerDeser.INSTANCE;

        ActorSystem.setAliasResolver(actor -> {
            System.out.println("Actor not found by Proxy A: " + actor);
            var actorProxy = "proxyB->proxyA";

            System.out.println("Proxy " + actorProxy + " known by Proxy A: " + ActorSystem.isActorAvailable(actorProxy));

            return actorProxy;
        });
        TcpReceiver.startTcpReceiverProxy(port, "secret", ser, ser, false);
        System.out.println("TCP receiver started in ProxyA");

        var ch = new TcpChannel<String, String>(new InetSocketAddress(portProxyB), "secret", ser, ser, true, "proxyA->proxyB");
        var proxyB = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("proxyB", ch, ser);

        ActorSystem.named("proxyAEcho").newActorWithReturn(str -> "ProxyAEcho-PONG to " + str);
        ActorSystem.named("proxyA").newActorWithReturn(str -> "ProxyA-PONG to " + str);
        ActorSystem.addProxy("proxyB->proxyA", ch);

        System.out.println("*** Proxy A listening on port " + port);
        SystemUtils.sleep(5000);
        System.out.println("Answer from proxyB: " + proxyB.sendMessageReturn("Ping Proxy B from ProxyA").get());
    }
}
