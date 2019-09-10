package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.SinkActorSingleTask;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BlinkingExample {
    public static void main(String[] args) throws IOException {
        int port = 9099;
        AtomicInteger blinking = new AtomicInteger();
        AtomicInteger on = new AtomicInteger();
        AtomicInteger off = new AtomicInteger();
        InetAddress addr = InetAddress.getLocalHost();

        Stereotypes.auto().schedule(() -> {
            System.out.println("Blink: " + blinking.get() + " - ON: " + on.get() + " - OFF: " + off.get());
        }, 1000);

        Stereotypes.auto().tcpAcceptor(port, socket -> {
            try {
                int numBlinks = socket.getInputStream().read();

                blinking.incrementAndGet();
                off.incrementAndGet();

                for (int i = 0; i < numBlinks; i++) {
                    on.incrementAndGet();
                    off.decrementAndGet();
                    SystemUtils.randomSleep(100, 1000);
                    on.decrementAndGet();
                    off.incrementAndGet();
                    SystemUtils.randomSleep(100, 1000);
                }
                blinking.decrementAndGet();
            } catch (IOException e) {

                e.printStackTrace();
            }
        }, null, true);

        SystemUtils.sleep(10);
        int numActors = 200;
        int numCallsPerActor = 50;
        long start = System.currentTimeMillis();
        List<SinkActorSingleTask<Void>> listActors = new ArrayList<>();

        for (int i = 0; i < numActors; i++) {
            listActors.add(Stereotypes.auto().runOnce(() -> {
                for (int j = 0; j < numCallsPerActor; j++) {
                    try (Socket clientSocket = new Socket(addr, port)) {
                        clientSocket.getOutputStream().write(30);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }

        System.out.println("Actors created - waiting for them to finish");

        for (SinkActorSingleTask<Void> actor : listActors)
            actor.waitForExit();

        long end = System.currentTimeMillis();
        System.out.println("Contacting " + (numActors * numCallsPerActor) + " LEDs required " + (end - start) + " ms ");
    }
}
