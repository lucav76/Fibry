package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;

public class UdpExampleMini {
    public static void main(String[] args) throws IOException {
        int port = 18000;

        var actor = Stereotypes.def().udpServerString(port, message -> {
            System.out.println("UDP message received: " + message);
        });

        new DatagramSocket().send(new DatagramPacket("Test".getBytes(), 4, InetAddress.getByName("localhost"), port));

        SystemUtils.sleep(1);
        actor.askExitAndWait();
    }
}
