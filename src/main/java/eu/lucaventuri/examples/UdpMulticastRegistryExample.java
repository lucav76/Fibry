package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;
import eu.lucaventuri.fibry.distributed.ActorRegistry;
import eu.lucaventuri.fibry.distributed.BaseActorRegistry;

import java.io.IOException;
import java.net.InetAddress;

public class UdpMulticastRegistryExample {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Please specify the local port and the name of the actors to create");
            System.exit(1);
        }

        InetAddress address = InetAddress.getByName("224.0.0.0");
        int multicastPort = 10001;
        var reg = ActorRegistry.usingMulticast(address, multicastPort, 15000, 1000, 15000, 30000, action -> {
            if (action.action == BaseActorRegistry.RegistryAction.JOINING)
                return true;

            return action.info.toUpperCase().equals(action.info) || action.info.equals("b");
        });


        for (int i = 1; i < args.length; i++) {
            var name = args[i];
            reg.registerActor(ActorSystem.anonymous().newActor(obj -> {
            }), name);

            System.out.println("Registered actor " + name);
        }

        Stereotypes.auto().schedule(() -> {
            if (reg.getHowManyRemoteActors() > 0) {
                System.out.println("Remote actors: ");
                reg.visitRemoteActors((id, info) -> {
                    System.out.println(info + ": " + id);
                });
            }
        }, 1000);
    }
}
