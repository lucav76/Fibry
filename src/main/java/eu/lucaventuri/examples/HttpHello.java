package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.jmacs.ActorUtils;
import eu.lucaventuri.jmacs.Stereotypes;
import eu.lucaventuri.jmacs.Stereotypes.HttpStringWorker;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpHello {
    public static void main(String[] args) throws IOException {
        int port = 9091;
        AtomicInteger numCalls = new AtomicInteger();

        Stereotypes.auto().embeddedHttpServer(port, new Stereotypes.HttpStringWorker("/", ex -> "Hello world!"),
                new HttpStringWorker("/cnt", ex -> "Hello world - " + numCalls.incrementAndGet()),
                new HttpStringWorker("/wait1", ex -> {
                    SystemUtils.sleep(1_000);
                    return "Waited 1s";
                }),
                new HttpStringWorker("/wait10", ex -> {
                    SystemUtils.sleep(10_000);
                    return "Waited 10s";
                }),
                new HttpStringWorker("/wait60", ex -> {
                    SystemUtils.sleep(60_000);
                    return "Waited 60s";
                }));

        System.out.println("Waiting on http://localhost:" + port);
        System.out.println("Fibers available: " + ActorUtils.areFibersAvailable());
    }
}
