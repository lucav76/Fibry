package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.net.*;
import java.util.Collection;
import java.util.List;

/** Registry using UDP multicast */
public class MulticastActorRegistry extends BaseActorRegistry {
    private final int multicastPort;
    private final DatagramSocket clientSocket = new DatagramSocket();
    private final InetAddress multicastGroup;
    private final int localPort;
    private final InetAddress localAddress;

    public MulticastActorRegistry(InetAddress multicastGroup, int multicastPort, int msRefresh, int msGraceSendRefresh, int msCleanRemoteActors, int msGgraceCleanRemoteActors) throws IOException {
        super(msRefresh, msGraceSendRefresh, msCleanRemoteActors, msGgraceCleanRemoteActors);

        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;
        this.localPort = clientSocket.getLocalPort();
        this.localAddress = clientSocket.getLocalAddress();

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
                System.err.println(e);
            }
        }

        String info = (new String(packet.getData(), 0, packet.getLength()));
        onActorsInfo(List.of(stringToAction(info)));
    }


    @Override
    protected void sendActorsInfo(Collection<ActorAction> actions) {
        for (var action : actions) {
            Exceptions.logShort(() -> {
                var buf = actionToString(action).getBytes();
                var packet = new DatagramPacket(buf, buf.length, multicastGroup, multicastPort);

                clientSocket.send(packet);
            });
        }
    }

    private String actionToString(ActorAction action) {
        if (action.action==RegistryAction.JOINING)
            return "J";
        String actionCode = action.action == RegistryAction.REGISTER ? "R|" : "D|";

        return actionCode + action.id + "|" + action.info;
    }

    private ActorAction stringToAction(String info) {
        char code = info.charAt(0);

        if (code=='J') {
            if (info.length()>1)
                throw new IllegalArgumentException("Wrong format [1]");

            return new ActorAction(RegistryAction.JOINING, null, null);
        }

        if (code != 'R' && code != 'D')
            throw new IllegalArgumentException("Wrong action code: " + code);
        if (info.charAt(1) != '|')
            throw new IllegalArgumentException("Wrong format [2]");

        int endInfo = info.indexOf('|', 2);

        if (endInfo < 0)
            throw new IllegalArgumentException("Wrong format [3]");

        RegistryAction action = code == 'R' ? RegistryAction.REGISTER : RegistryAction.DEREGISTER;
        String id = info.substring(2, endInfo);

        return new ActorAction(action, id, info.substring(endInfo + 1));
    }

    @Override
    public void close() throws Exception {
        super.close();
        clientSocket.close();
    }
}
