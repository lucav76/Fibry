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

public class TcpChannel<T, R> implements RemoteActorChannel<T, R> {
    final static int[] retries = {0, 100, 500, 1000, 5000, 30000};
    private final static boolean keepReconnecting = true;
    private final InetSocketAddress address;
    private final ConsumerEx<SocketChannel, IOException> channelAuthorizer;
    private final TcpActorSender<R> actor;
    private final boolean sendTargetActorName;
    private final ChannelSerializer<T> ser;
    private final ChannelDeserializer<R> deser;
    private final MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);
    private volatile SocketChannel channel = null;
    private volatile boolean reconnect = true;

    interface ChannelProvider<R> {
        CompletableFuture<R> useChannelRetries(FunctionEx<SocketChannel, CompletableFuture<R>, IOException> worker);
    }

    /**
     * Channel based on TCP IP
     *
     * @param address Target host
     * @param channelAuthorizer Initializer of the channel
     */
    public TcpChannel(InetSocketAddress address, ConsumerEx<SocketChannel, IOException> channelAuthorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName) {
        this.address = address;
        actor = new TcpActorSender<>(worker -> {
            for (int i = 0; keepReconnecting ? true : i < retries.length; i++) {
                try {
                    return worker.apply(getChannel());
                } catch (IOException e) {
                    channel = null;
                }
                // Spread reconnections from multiple actors, in case of network issue
                int retryTime = i < retries.length ? retries[i] : retries[retries.length - 1];

                SystemUtils.sleep((int) (retryTime / 2 + Math.random() * retryTime / 2));
            }

            return null;
        }, msgReg);
        this.channelAuthorizer = channelAuthorizer;
        this.sendTargetActorName = sendTargetActorName;
        this.ser = ser;
        this.deser = deser;
    }

    /** Constructor with default authorizer */
    public TcpChannel(InetSocketAddress address, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName, String channelName) {
        this(address, getChallengeSenderAuthorizer(sharedKey, channelName), ser, deser, sendTargetActorName);
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
            TcpReceiver.receiveFromAuthorizedChannel(ser, deser, false, null, channel, msgReg, null);
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

            return actor.sendMessageReturn(MessageHolder.newWithReturn(sendTargetActorName ? (remoteActorName + "|" + msgSerialized) : msgSerialized)).get();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void sendMessage(String remoteActorName, ChannelSerializer<T> ser, T message) throws IOException {
        verifyRemoteActorName(remoteActorName);

        var msgSerialized = ser.serializeToString(message);
        actor.sendMessage(MessageHolder.newVoid(sendTargetActorName ? (remoteActorName + "|" + msgSerialized) : msgSerialized));
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
