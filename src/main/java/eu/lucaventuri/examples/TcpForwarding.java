package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.Stereotypes;
import java.io.IOException;

public class TcpForwarding {
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            showUsageAndExit();

        for (int i = 0; i < args.length; i++) {
            int idxArrow = args[i].indexOf("->");
            int idxColon = args[i].indexOf(':');

            if (idxArrow < 0)
                showUsageAndExit();

            final boolean debug = false;
            int localPort = Integer.parseInt(args[i].substring(0, idxArrow));
            String secondPart = args[i].substring(idxArrow + 2);
            String remoteHost = idxColon >= 0 ? args[i].substring(idxArrow + 2, idxColon) : ((secondPart.charAt(0) >= '0' && secondPart.charAt(0) <= '9') ? null : secondPart);
            int remotePort = idxColon >= 0 ? Integer.parseInt(args[i].substring(idxColon + 1)) : (remoteHost != null ? localPort : Integer.parseInt(secondPart));

            if (remoteHost == null) {
                System.out.println("Local Forwarding port " + localPort + " to " + remotePort);
                Stereotypes.def().forwardLocal(localPort, remotePort, debug, debug, 500);
            } else {
                System.out.println("Forwarding port " + localPort + " to " + remoteHost + ":" + remotePort);
                Stereotypes.def().forwardRemote(localPort, remoteHost, remotePort, debug, debug, 500);
            }
        }
    }

    private static void showUsageAndExit() {
        System.out.println("Usage: TcpForwarding localPort1->remoteHost1:remotePort1 ...");
        System.exit(1);
    }
}
