package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.ActorUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;

public class HttpExampleMini {
    public static void main(String[] args) throws IOException {
        int port = 18000;

        System.out.println("Fibers available: " + ActorUtils.areFibersAvailable());
        Stereotypes.def().embeddedHttpServer(port, new Stereotypes.HttpStringWorker("/", ex -> "Hello world from Fibry!"));
        System.out.println("Listening on http://localhost:" + port);
    }
}
