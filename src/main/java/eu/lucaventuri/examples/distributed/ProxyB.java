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
        ProxyA.startProxy(9802, "proxyB", 9801, "proxyA", 9810, "!mapActor!", "secret");
    }
}
