package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.FunctionEx;
import eu.lucaventuri.common.NetworkUtils;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TcpReceiver {
    public static Map<String, SocketChannel> openChannels = new ConcurrentHashMap<>();

    public static <T, R> void startTcpReceiverProxy(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation) throws IOException {
        startTcpReceiver(port, TcpChannel.getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, null);
    }

    public static <T, R> void startTcpReceiverSingleActor(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String receivingActor) throws IOException {
        startTcpReceiver(port, TcpChannel.getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, receivingActor);
    }

    private static <T, R> void startTcpReceiver(int port, FunctionEx<SocketChannel, String, IOException> authorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName) throws IOException {
        MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);

        Stereotypes.def().tcpAcceptor(port, socket -> {
            var ch = socket.getChannel();
            assert ch != null;
            final String channelName;

            try {
                channelName = authorizer.apply(ch);

                openChannels.put(channelName, ch);
                System.out.println("Accepted connection from " + channelName);
            } catch (IOException e) {
                System.err.println(e);
                return;
            }

            receiveFromAuthorizedChannel(ser, deser, deliverBeforeActorCreation, targetActorName, ch, msgReg, channelName);
        }, true);
    }

    static <T, R> void receiveFromAuthorizedChannel(ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, SocketChannel ch, MessageRegistry<R> msgReg, String channelName) {
        while (true) {
            if (!receiveSingleMessage(ch, ser, deser, deliverBeforeActorCreation, targetActorName, msgReg, channelName))
                return;
        }
    }

    private static <T, R> boolean receiveSingleMessage(SocketChannel ch, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, MessageRegistry<R> msgReg, String channelName) {
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
                    ActorSystem.<R, T>sendMessageReturn(actorName, message, deliverBeforeActorCreation).whenComplete((value, exception) -> {
                        MessageHolder<R> answer = exception != null ? MessageHolder.newException(messageId, exception) : MessageHolder.newAnswer(messageId, ser.serializeToString(value));
                        boolean keepReconnecting = true;
                        var effectiveChannel = ch;

                        for (int i = 0; keepReconnecting ? true : i < TcpChannel.retries.length; i++) {
                            if (effectiveChannel != null) {
                                try {
                                    answer.writeMessage(effectiveChannel, msgReg);

                                    return;
                                } catch (Throwable t) {
                                    /* Silence */
                                }

                                if (channelName != null) {
                                    openChannels.remove(channelName, effectiveChannel);
                                }
                            }

                            // Spread reconnections from multiple actors, in case of network issue
                            int retryTime = i < TcpChannel.retries.length ? TcpChannel.retries[i] : TcpChannel.retries[TcpChannel.retries.length - 1];

                            SystemUtils.sleep((int) (retryTime / 2 + Math.random() * retryTime / 2));
                            effectiveChannel = openChannels.get(channelName);
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
