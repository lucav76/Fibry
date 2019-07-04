package eu.lucaventuri.collections;

import eu.lucaventuri.jmacs.Stereotypes;

import java.io.IOException;
import java.io.OutputStream;

public class ManualTests {
    public static void main(String[] args) throws IOException {
        int port = 9091;

        Stereotypes.threads().embeddedHttpServer(port, new Stereotypes.HttpWorker("/", ex -> {
            String response = "Hi there!";
            ex.sendResponseHeaders(200, response.getBytes().length);//response code and length
            OutputStream os = ex.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }));

        System.out.println("Waiting on port http://localhost:" + port + "/");
    }
}
