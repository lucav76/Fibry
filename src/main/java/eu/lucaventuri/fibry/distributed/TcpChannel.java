package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.ConsumerEx;
import eu.lucaventuri.common.EasyCrypto;
import eu.lucaventuri.common.NetworkUtils;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class TcpChannel<T, R> implements RemoteActorChannel<T, R> {
    private final static int[] retries = {0, 100, 500, 1000, 5000, 30000};
    private final InetSocketAddress address;
    private final ConsumerEx<SocketChannel, IOException> channelAuthorizer;
    private final TcpActorSender actor;
    private final boolean sendTargetActorName;
    private final ChannelSerializer<T> ser;
    private final ChannelDeserializer<R> deser;
    private final MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);


    private class TcpActorSender extends CustomActorWithResult<MessageHolder<R>, CompletableFuture<R>, Void> {
        private SocketChannel channel = null;

        protected TcpActorSender() {
            super(new FibryQueue<>(), null, null, Integer.MAX_VALUE);

            closeStrategy = CloseStrategy.SEND_POISON_PILL;
            CreationStrategy.AUTO.start(this);
        }

        @Override
        protected CompletableFuture<R> onMessage(MessageHolder<R> message) {
            for (int i = 0; i < retries.length; i++) {
                try {
                    return message.writeMessage(getChannel(), msgReg);
                } catch (IOException e) {
                    channel = null;
                    SystemUtils.sleep(retries[i]);
                }
            }

            return null;
        }

        SocketChannel getChannel() throws IOException {
            if (channel != null)
                return channel;

            channel = SocketChannel.open(address);
            channelAuthorizer.accept(channel);

            // FIXME: there should be a way to delete the old actors
            Stereotypes.def().runOnce(() -> {
                receiveFromAuthorizedChannel(ser, deser, false, null, channel, msgReg);
            });

            return channel;
        }
    }

    /**
     * Channel based on TCP IP
     *
     * @param address Target host
     * @param channelAuthorizer Initializer of the channel
     */
    public TcpChannel(InetSocketAddress address, ConsumerEx<SocketChannel, IOException> channelAuthorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName) {
        this.address = address;
        actor = new TcpActorSender();
        this.channelAuthorizer = channelAuthorizer;
        this.sendTargetActorName = sendTargetActorName;
        this.ser = ser;
        this.deser = deser;
    }

    /** Constructor with default authorizer */
    public TcpChannel(InetSocketAddress address, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean sendTargetActorName) {
        this(address, getChallengeSenderAuthorizer(sharedKey), ser, deser, sendTargetActorName);
    }

    private static ConsumerEx<SocketChannel, IOException> getChallengeSenderAuthorizer(String sharedKey) {
        return ch -> {
            assert ch.isBlocking();

            int challenge = NetworkUtils.readInt32(ch);
            String str = challenge + sharedKey;
            byte[] answer = EasyCrypto.hash512(str);
            ByteBuffer buf = ByteBuffer.wrap(answer);

            NetworkUtils.writeInt16(ch, (short) answer.length);
            ch.write(buf);
        };
    }

    private static ConsumerEx<SocketChannel, IOException> getChallengeReceiverAuthorizer(String sharedKey) {
        return ch -> {
            Random rnd = new Random();
            var challenge = rnd.nextInt();
            byte[] expected = EasyCrypto.hash512(challenge + sharedKey);

            NetworkUtils.writeInt32(ch, challenge);
            var answerLen = NetworkUtils.readInt16(ch);
            var answer = NetworkUtils.readFully(ch, answerLen);

            if (!Arrays.equals(expected, answer.array())) {
                throw new IOException("Invalid handshake answer!");
            }
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

    public static <T, R> void startTcpReceiverProxy(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation) throws IOException {
        startTcpReceiver(port, getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, null);
    }

    public static <T, R> void startTcpReceiverSingleActor(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String receivingActor) throws IOException {
        startTcpReceiver(port, getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, receivingActor);
    }

    private static <T, R> void startTcpReceiver(int port, ConsumerEx<SocketChannel, IOException> authorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName) throws IOException {
        MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);

        Stereotypes.def().tcpAcceptor(port, socket -> {
            var ch = socket.getChannel();
            assert ch != null;

            try {
                authorizer.accept(ch);
            } catch (IOException e) {
                System.err.println(e);
                return;
            }

            receiveFromAuthorizedChannel(ser, deser, deliverBeforeActorCreation, targetActorName, ch, msgReg);
        }, true);
    }

    private static <T, R> void receiveFromAuthorizedChannel(ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, SocketChannel ch, MessageRegistry<R> msgReg) {
        while (true) {
            if (!receiveSingleMessage(ch, ser, deser, deliverBeforeActorCreation, targetActorName, msgReg))
                return;
        }
    }

    private static <T, R> boolean receiveSingleMessage(SocketChannel ch, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, MessageRegistry<R> msgReg) {
        try {
            byte type = NetworkUtils.readInt8(ch);
            MessageHolder.MessageType msgType = MessageHolder.MessageType.fromSignature(type);
            if (msgType == MessageHolder.MessageType.WITH_RETURN || msgType == MessageHolder.MessageType.VOID) {
                boolean messageWithReturn = msgType == MessageHolder.MessageType.WITH_RETURN;
                long messageId = messageWithReturn ? NetworkUtils.readInt64(ch) : -1;
                int len = NetworkUtils.readInt32(ch);
                String str = NetworkUtils.readFullyAsString(ch, len);
                final String actorName;
                final R message;

                if (targetActorName != null) {
                    actorName = targetActorName;
                    message = deser.deserializeString(str);
                } else {
                    int idx = str.indexOf('|');

                    if (idx < 0) {
                        System.err.println("Invalid message header");

                        return false;
                    }

                    actorName = str.substring(0, idx);
                    message = deser.deserializeString(str.substring(idx + 1));
                }

                if (messageWithReturn) {
                    // FIXME: we need a way to identify the sending channel (e.g. JWT) and send the answer back also if the channel breaks.
                    ActorSystem.<R, T>sendMessageReturn(actorName, message, deliverBeforeActorCreation).whenComplete((value, exception) -> {
                        MessageHolder<R> answer = exception != null ? MessageHolder.newException(messageId, exception) : MessageHolder.newAnswer(messageId, ser.serializeToString(value));

                        try {
                            answer.writeMessage(ch, msgReg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } else {
                    ActorSystem.sendMessage(actorName, message, deliverBeforeActorCreation);
                }
            } else {
                // FIXME: manae answer and exception
                var answerId = NetworkUtils.readInt64(ch);
                int len = NetworkUtils.readInt32(ch);
                String str = NetworkUtils.readFullyAsString(ch, len);

                if (msgType == MessageHolder.MessageType.ANSWER)
                    msgReg.completeFuture(answerId, deser.deserializeString(str));
                else
                    msgReg.completeExceptionally(answerId, new IOException(str));

                return true;
            }
        } catch (IOException e) {
            System.err.println("Error while reading a message: " + e);

            return false;
        }
        return true;
    }
}
