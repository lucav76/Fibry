package eu.lucaventuri.examples;

import eu.lucaventuri.jmacs.ActorUtils;
import eu.lucaventuri.jmacs.Stereotypes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class HttpExamples {
    public static void main(String[] args) throws IOException {
        int port = 9091;

        Stereotypes.threads().embeddedHttpServer(port, new Stereotypes.HttpWorker("/", ex -> {
            String response = "Hi there!";
            ex.sendResponseHeaders(200, response.getBytes().length);//response code and length
            OutputStream os = ex.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }));

        Stereotypes.threads().embeddedHttpServer(port+1, new Stereotypes.HttpStringWorker("/", ex -> "Hello world!"));

        Consumer<Integer> master = Stereotypes.auto().workersAsConsumerCreator(System.out::println);

        master.accept(1);
        master.accept(2);
        master.accept(3);

        System.out.println("Waiting on http://localhost:" + port + "/ and on http://localhost:" + (port+1));

        System.out.println("Fibers available: " + ActorUtils.areFibersAvailable());
    }
}
