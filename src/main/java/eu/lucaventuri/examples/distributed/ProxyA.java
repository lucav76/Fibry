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
        startProxy(9801, "proxyA", 9802, "proxyB", 9810, "!mapActor!", "secret");
    }

    static void startProxy(int thisProxyPort, String thisProxyName, int nextProxyPort, String nextProxyName, int portMapActor, String mapActorName, String sharedKey) throws IOException {
        var ser = StringSerDeser.INSTANCE;

        // Starts a receiver usable for proxies
        TcpReceiver.startTcpReceiverProxy(thisProxyPort, sharedKey, ser, ser, false);

        // Creates a channel toward another proxy
        var ch = new TcpChannel<String, String>(new InetSocketAddress(nextProxyPort), sharedKey, ser, ser, true, thisProxyName + "->" + nextProxyName);
        ActorSystem.addProxy(nextProxyName, ch);

        // Registers two internal actors, for testing
        ActorSystem.named(thisProxyName + "Echo").newActorWithReturn(str -> thisProxyName + "Echo" + "-PONG to " + str);
        ActorSystem.named(thisProxyName).newActorWithReturn(str -> thisProxyName + "-PONG to " + str);

        // Creates a mapping actor, used to retrieve the routes to find the actors
        createMapActor(thisProxyName, portMapActor, mapActorName, ser, sharedKey);

        System.out.println("*** " + thisProxyName + " listening on port " + thisProxyPort);
    }

    private static void createMapActor(String thisProxyName, int portMapActor, String mapActorName, StringSerDeser ser, String sharedKey) {
        // Creates a channel and an actor toward the mapping server
        var chMap = new TcpChannel<String, String>(new InetSocketAddress(portMapActor), sharedKey, ser, ser, true, thisProxyName + "->" + mapActorName);
        var mapActor = ActorSystem.anonymous().<String, String>newRemoteActorWithReturn(mapActorName, chMap, ser);

        // Creates an alias resolver, used to find which proxy knows the actor
        ActorSystem.setAliasResolver(actor -> {
            String proxyFromMap = null;
            try {
                proxyFromMap = mapActor.sendMessageReturn("GET|" + actor).get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println(e);
            }

            return proxyFromMap;
        });

        // Updates the mapping actor with the new information regarding actors joining and leaving
        TcpReceiver.setListener((actorName, operation) -> {
            if (operation == TcpReceiver.ActorOperation.LEFT)
                mapActor.sendMessage("DELETE|" + actorName);
            else if (operation == TcpReceiver.ActorOperation.JOIN)
                mapActor.sendMessage("PUT|" + actorName + "|" + thisProxyName);
        });
    }
}
