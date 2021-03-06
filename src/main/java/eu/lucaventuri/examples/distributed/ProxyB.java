package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpChannel;
import eu.lucaventuri.fibry.distributed.TcpReceiver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public class ProxyB {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        int port = 9802;
        int portProxyA = 9801;

        System.out.println("*** Proxy B UP");

        //var ser = new JacksonSerDeser<String, String>(String.class);
        var ser = StringSerDeser.INSTANCE;

        ActorSystem.setAliasResolver(actor -> {
            System.out.println("Actor not found by Proxy B: " + actor);
            var actorProxy = "proxyA->proxyB";

            System.out.println("Proxy " + actorProxy + " known by Proxy B: " + ActorSystem.isActorAvailable(actorProxy));

            return actorProxy;
        });
        TcpReceiver.startTcpReceiverProxy(port, "secret", ser, ser, false);
        System.out.println("TCP receiver started in ProxyB");

        var ch = new TcpChannel<String, String>(new InetSocketAddress(portProxyA), "secret", ser, ser, true, "proxyB->proxyA");
        System.out.println("Channel created in ProxyB");
        var proxyA = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn("proxyA", ch, ser);

        ch.ensureConnection();
        ActorSystem.named("proxyB").newActorWithReturn(str -> "ProxyB-PONG to " + str);

        System.out.println("Channel connected in ProxyB");
        ActorSystem.addProxy("proxyA->proxyB", ch);
        var answer = ch.sendMessageReturn("proxyAEcho", ser,ser, "Ping Proxy A Echo from ProxyB");
        System.out.println("Answer from proxyAEcho: " + answer.get());
        System.out.println("Answer from proxyA: " + proxyA.sendMessageReturn("Ping Proxy A from ProxyB").get());
        System.out.println("*** Proxy B listening on port " + port);
    }
}
