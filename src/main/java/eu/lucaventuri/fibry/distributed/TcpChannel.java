package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.*;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** TCP channel, that can put in communication the actors in a server with the actors in anther one
 * The target host can basically have two configurations:
 * - Direct: the whole target server is considered a single actor from the point of view of the sender
 * - Proxy: the target server can host multiple actors (and therefore we need to specify which one we are looking for);
 *   proxies can also in principle forward the message to other servers
 *
 *  Some considerations: in a way, the actors need to be aware of how to route the requests to remote actors; in pracitce
 *  it means that:
 *  - client actors are connected to a proxy
 *  - if there is a single proxy, it will know all the actors
 *  - if there are multiple proxies
 *    - each proxy should be connected to all other proxies
 *    - you need a mechanism (e.g. Redis) to know which proxy holds which actor, so they can route the message
 *
 *  More complex structure can be created, if needed.
 *
 *  Typically, the actors need a way to know some other actors, but this is left to the implementation; for example,
 *  in a chat of a social network, the social network will provide a list of friends, and these will be the actors.
 * */
public class TcpChannel<T, R> implements RemoteActorChannel<T, R> {
    private final InetSocketAddress address;
    private final ConsumerEx<SocketChannel, IOException> channelAuthorizer;
    private final TcpActorSender<R> senderActor;
    private final boolean sendTargetActorName;
    private final ChannelSerializer<T> ser;
    private final ChannelDeserializer<R> deser;
    private final MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);
    private volatile SocketChannel channel = null;
    private volatile boolean reconnect = true;
    private final String channelName;


    /**
     * Creates a channel using TCP IP
     * @param address Target host
     * @param channelAuthorizer Object that can initialize the channel, typically demonstrating that the connecting server is authorized and trusted
     * @param ser Serializer, to be able to transmit the messages
     * @param deser Deserializer, to be able to receive the messages
     * @param sendTargetActorName True if we need to send the target actor name; this is mandatory for proxies (as we need to identify the target actor)
     * @param channelName Unique channel name:
     *   - client actors (which host one remote actor per channel), must use their name (e.g. alice), to be reachable
     *   - proxies when connecting to other proxies should use the "source-&gt;target" convention or similar, to help debugging
     */
    public TcpChannel(InetSocketAddress address, ConsumerEx<SocketChannel, IOException> channelAuthorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName, String channelName) {
        this.address = address;
        senderActor = new TcpActorSender<>(worker -> {
                try {
                    return worker.apply(getChannel());
                } catch (IOException e) {
                    channel = null;
                }
            return null;
        }, msgReg);
        this.channelAuthorizer = channelAuthorizer;
        this.sendTargetActorName = sendTargetActorName;
        this.ser = ser;
        this.deser = deser;
        this.channelName = channelName;
    }

    /** Constructor with default authorizer */
    public TcpChannel(InetSocketAddress address, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName, String channelName) {
        this(address, getChallengeSenderAuthorizer(sharedKey, channelName), ser, deser, sendTargetActorName, channelName);
    }

    synchronized SocketChannel getChannel() throws IOException {
        if (channel != null)
            return channel;

        if (!reconnect)
            return null;

        channel = SocketChannel.open(address);
        channelAuthorizer.accept(channel);

        // FIXME: there should be a way to delete the old actors
        Stereotypes.def().runOnce(() -> {
            TcpReceiver.receiveFromAuthorizedChannel(ser, deser, false, null, channel, msgReg, null, senderActor);
        });

        return channel;
    }

    public void drop() {
        var ch = channel;

        channel = null;

        if (ch != null)
            SystemUtils.close(ch.socket());
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    public void ensureConnection() throws IOException {
        getChannel();
    }

    private static ConsumerEx<SocketChannel, IOException> getChallengeSenderAuthorizer(String sharedKey, String channelName) {
        return ch -> {
            assert ch.isBlocking();

            int challenge = NetworkUtils.readInt32(ch);
            String str = challenge + sharedKey;
            byte[] answer = EasyCrypto.hash512(str);
            ByteBuffer buf = ByteBuffer.wrap(answer);

            NetworkUtils.writeInt16(ch, (short) answer.length);
            ch.write(buf);
            ch.write(NetworkUtils.asBufferWithLength32(channelName));
        };
    }

    static FunctionEx<SocketChannel, String, IOException> getChallengeReceiverAuthorizer(String sharedKey) {
        return ch -> {
            Random rnd = new Random();
            var challenge = rnd.nextInt();
            byte[] expected = EasyCrypto.hash512(challenge + sharedKey);

            NetworkUtils.writeInt32(ch, challenge);
            var answerLen = NetworkUtils.readInt16(ch);
            var answer = NetworkUtils.readFully(ch, answerLen);

            if (!Arrays.equals(expected, answer.array())) {
                ch.socket().close();
                throw new IOException("Invalid handshake answer!");
            }
            var channelNameLen = NetworkUtils.readInt32(ch);

            return NetworkUtils.readFullyAsString(ch, channelNameLen);
        };
    }

    @Override
    public CompletableFuture<R> sendMessageReturn(String remoteActorName, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, T message) {
        // FIXME: implements properly
        try {
            verifyRemoteActorName(remoteActorName);

            var msgSerialized = ser.serializeToString(message);

            return senderActor.sendMessageReturn(MessageHolder.newWithReturn(sendTargetActorName ? (remoteActorName + "|" + msgSerialized) : msgSerialized)).get();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void sendMessage(String remoteActorName, ChannelSerializer<T> ser, T message) throws IOException {
        verifyRemoteActorName(remoteActorName);

        var msgSerialized = ser.serializeToString(message);
        senderActor.sendMessage(MessageHolder.newVoid(sendTargetActorName ? (remoteActorName + "|" + msgSerialized) : msgSerialized));
    }

    private void verifyRemoteActorName(String remoteActorName) {
        if (remoteActorName == null)
            throw new IllegalArgumentException("Remote actor name required");

        if (remoteActorName.contains("|"))
            throw new IllegalArgumentException("Remote actor name cannot contain the character '|'");
    }

    @Override
    public ChannelSerializer<T> getDefaultChannelSerializer() {
        return ser;
    }

    @Override
    public ChannelDeserializer<R> getDefaultChannelDeserializer() {
        return deser;
    }
}
