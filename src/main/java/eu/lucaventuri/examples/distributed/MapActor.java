package eu.lucaventuri.examples.distributed;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;
import eu.lucaventuri.fibry.distributed.StringSerDeser;
import eu.lucaventuri.fibry.distributed.TcpReceiver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Example of maping server; this has been created to provide an implementation without dependencies,
 * and for testing, but in production it would be recommended to use Redis or some other Key Value store */
public class MapActor {
    static final int port = 9810;
    static final Map<String, String> map = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("*** Map Actor UP");

        var ser = StringSerDeser.INSTANCE;

        TcpReceiver.startTcpReceiverProxy(port, "secret", ser, ser, false);

        ActorSystem.named("!mapActor!").newActorWithReturn((String cmd) -> {
            String[] parts = cmd.split("\\|");

            if (parts.length < 2)
                return null;
            if (parts.length > 3)
                return null;

            switch (parts[0]) {
                case "GET":
                    if (parts.length != 2)
                        return null;

                    return map.get(parts[1]);
                case "PUT":
                    if (parts.length != 3)
                        return null;

                    return map.put(parts[1], parts[2]);
                case "REMOVE":
                    if (parts.length != 2)
                        return null;

                    return map.remove(parts[1]);
                default:
                    return null;
            }
        });

        // for debugging
        Stereotypes.def().embeddedHttpServer(port + 1,
                new Stereotypes.HttpStringWorker("/list", exchange -> "Actors registered: \n" + getList()));

        System.out.println("*** Map listening on port " + port + " - HTTP on " + (port + 1));
    }

    private static String getList() {
        return map.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n"));
    }
}
