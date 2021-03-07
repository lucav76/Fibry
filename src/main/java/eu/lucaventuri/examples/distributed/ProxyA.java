package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpChannel;
import eu.lucaventuri.fibry.distributed.TcpReceiver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public class ProxyA {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        startProxy(9801, "proxyA", 9802, "proxyB", 9810, "!mapActor!");
    }

    static void startProxy(int thisProxyPort, String thisProxyName, int nextProxyPort, String nextProxyName, int portMapActor, String mapActorName) throws IOException {
        System.out.println("*** " + thisProxyName + " UP");
        var ser = StringSerDeser.INSTANCE;
        String sharedKey = "secret";

        TcpReceiver.startTcpReceiverProxy(thisProxyPort, sharedKey, ser, ser, false);

        var ch = new TcpChannel<String, String>(new InetSocketAddress(nextProxyPort), sharedKey, ser, ser, true, thisProxyName + "->" + nextProxyName);
        var nextProxy = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn(nextProxyName, ch, ser);

        ActorSystem.named(thisProxyName + "Echo").newActorWithReturn(str -> thisProxyName + "Echo" + "-PONG to " + str);
        ActorSystem.named(thisProxyName).newActorWithReturn(str -> thisProxyName + "-PONG to " + str);
        ActorSystem.addProxy(nextProxyName, ch);

        var chMap = new TcpChannel<String, String>(new InetSocketAddress(portMapActor), sharedKey, ser, ser, true, thisProxyName + "->" + mapActorName);
        var mapActor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn(mapActorName, chMap, ser);

        ActorSystem.setAliasResolver(actor -> {
            System.out.println("Actor not found by " + thisProxyName + ": " + actor);
            String proxyFromMap = null;
            try {
                proxyFromMap = mapActor.sendMessageReturn("GET|" + actor).get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println(e);
            }
            System.out.println("Proxy " + proxyFromMap + " known by " + thisProxyName + ": " + ActorSystem.isActorAvailable(proxyFromMap));

            return proxyFromMap;
        });

        TcpReceiver.setListener((actorName, operation) -> {
            if (operation == TcpReceiver.ActorOperation.LEFT)
                mapActor.sendMessage("DELETE|" + actorName);
            else if (operation == TcpReceiver.ActorOperation.JOIN)
                mapActor.sendMessage("PUT|" + actorName + "|" + thisProxyName);
        });

        System.out.println("*** " + thisProxyName + " listening on port " + thisProxyPort);
    }
}
