package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.net.*;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Registry using UDP multicast */
public class MulticastActorRegistry extends BaseActorRegistry {
    private final int multicastPort;
    private final DatagramSocket clientSocket = new DatagramSocket();
    private final InetAddress multicastGroup;
    private final int localPort;
    private static final Logger logger = Logger.getLogger(MulticastActorRegistry.class.getName());

    public MulticastActorRegistry(InetAddress multicastGroup, int multicastPort, int msRefresh, int msGraceSendRefresh, int msCleanRemoteActors, int msGgraceCleanRemoteActors, Predicate<ActorAction> validator) throws IOException {
        super(msRefresh, msGraceSendRefresh, msCleanRemoteActors, msGgraceCleanRemoteActors, validator);

        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;
        this.localPort = clientSocket.getLocalPort();

        Stereotypes.auto().udpMulticastServer(multicastGroup, multicastPort, this::onNewInfo);

        sendActorsInfo(List.of(new ActorAction(RegistryAction.JOINING, null, null)));
    }

    private void onNewInfo(DatagramPacket packet) {
        var address = (InetSocketAddress) packet.getSocketAddress();

        // SKips its own messages
        if (address.getPort() == localPort) {
            try {
                if (SystemUtils.retrieveIPAddresses(true, true).contains(address.getAddress()))
                    return;
            } catch (SocketException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }

        String info = (new String(packet.getData(), 0, packet.getLength()));
        onActorsInfo(List.of(ActorRegistry.ActorAction.fromStringProtocol(info)));
    }


    @Override
    protected void sendActorsInfo(Collection<ActorAction> actions) {
        for (var action : actions) {
            Exceptions.logShort(() -> {
                var buf = ActorRegistry.ActorAction.toStringProtocol(action).getBytes();
                var packet = new DatagramPacket(buf, buf.length, multicastGroup, multicastPort);

                clientSocket.send(packet);
            });
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        clientSocket.close();
    }
}
