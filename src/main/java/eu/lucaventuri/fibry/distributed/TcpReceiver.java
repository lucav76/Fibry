package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.FunctionEx;
import eu.lucaventuri.common.NetworkUtils;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class TcpReceiver {
    private static final Map<String, SocketChannel> openChannels = new ConcurrentHashMap<>();
    private static volatile BiConsumer<String, ActorOperation> listener;

    public enum ActorOperation {
        JOIN,
        LEFT
    }

    /** A proxy can send to any named actor; the name of the actor will be part of the message  */
    public static <T, R> void startTcpReceiverProxy(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation) throws IOException {
        startTcpReceiver(port, TcpChannel.getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, null);
    }

    /** Messages sent to a single actor */
    public static <T, R> void startTcpReceiverDirectActor(int port, String sharedKey, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String receivingActor) throws IOException {
        startTcpReceiver(port, TcpChannel.getChallengeReceiverAuthorizer(sharedKey), ser, deser, deliverBeforeActorCreation, receivingActor);
    }

    private static <T, R> void startTcpReceiver(int port, FunctionEx<SocketChannel, String, IOException> authorizer, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName) throws IOException {
        MessageRegistry<R> msgReg = new MessageRegistry<>(50_000);

        Stereotypes.def().tcpAcceptorFromChannel(port, socket -> {
            var ch = socket.getChannel();
            assert ch != null;
            final String channelName;

            try {
                channelName = authorizer.apply(ch);

                openChannels.put(channelName, ch);
                if (listener != null)
                    listener.accept(channelName, ActorOperation.JOIN);
            } catch (IOException e) {
                System.err.println(e);
                return;
            }

            // TODO: verify that it supports returning values  (it should)
            createSendingChannel(msgReg, channelName);

            receiveFromAuthorizedChannel(ser, deser, deliverBeforeActorCreation, targetActorName, ch, msgReg, channelName, null);
        }, true, 3_000);
    }

    private static <R> void createSendingChannel(MessageRegistry<R> msgReg, String channelName) {
        if (!ActorSystem.isActorAvailable(channelName)) {
            var sendingActor = new TcpActorSender<>(worker -> {
                try {
                    var ch = openChannels.get(channelName);

                    if (ch != null)
                        return worker.apply(ch);
                } catch (IOException e) {
                    openChannels.remove(channelName);
                    if (listener != null)
                        listener.accept(channelName, ActorOperation.LEFT);
                }
                return null;
            }, msgReg);
            sendingActor.registerAsNamedActor(channelName);
        }
    }

    static <T, R> void receiveFromAuthorizedChannel(ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, SocketChannel ch, MessageRegistry<R> msgReg, String channelName, TcpActorSender<R> senderActor) {
        while (true) {
            if (!receiveSingleMessage(ch, ser, deser, deliverBeforeActorCreation, targetActorName, msgReg, channelName, senderActor))
                return;
        }
    }

    private static <T, R> boolean receiveSingleMessage(SocketChannel ch, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, boolean deliverBeforeActorCreation, String targetActorName, MessageRegistry<R> msgReg, String senderActorName, TcpActorSender<R> senderActor) {
        try {
            byte type = NetworkUtils.readInt8(ch);
            MessageHolder.MessageType msgType = MessageHolder.MessageType.fromSignature(type);

            if (msgType == MessageHolder.MessageType.WITH_RETURN || msgType == MessageHolder.MessageType.VOID) {
                boolean messageWithReturn = msgType == MessageHolder.MessageType.WITH_RETURN;
                long originalMessageId = messageWithReturn ? NetworkUtils.readInt64(ch) : -1;
                int len = NetworkUtils.readInt32(ch);
                String str = NetworkUtils.readFullyAsString(ch, len);
                final String actorName;
                final R message;
                final String messageString;

                if (targetActorName != null) {
                    actorName = targetActorName;
                    messageString = str;
                } else {
                    int idx = str.indexOf('|');

                    if (idx < 0) {
                        System.err.println("Invalid message header: " + str);

                        return false;
                    }

                    actorName = str.substring(0, idx);
                    messageString = str.substring(idx + 1);
                }

                message = deser.deserializeString(messageString);

                final Object messageToSend;

                if (openChannels.get(actorName) != null) // Send to proxy, as MessageHolder
                    messageToSend = messageWithReturn ? MessageHolder.newWithReturn(actorName + "|" + messageString) : MessageHolder.newVoid(actorName + "|" + messageString);
                else
                    messageToSend = message;

                if (messageWithReturn) {
                    ActorSystem.<Object, T>sendMessageReturn(actorName, messageToSend, deliverBeforeActorCreation).whenComplete((value, exception) -> {
                        assert (senderActor == null && senderActorName != null) || (senderActor != null && senderActorName == null);

                        try {
                            T valueToSend = value instanceof Future ? (T) ((Future<?>) value).get() : value;

                            MessageHolder<R> answer = exception != null ? MessageHolder.newException(originalMessageId, exception) : MessageHolder.newAnswer(originalMessageId, ser.serializeToString(valueToSend));

                            if (senderActor != null)
                                senderActor.onMessage(answer);
                            else
                                ActorSystem.sendMessage(senderActorName, answer, false); // retries managed by the actor
                        } catch (InterruptedException | ExecutionException e) {
                            System.err.println(e);
                        }
                    });
                } else {
                    ActorSystem.sendMessage(actorName, messageToSend, deliverBeforeActorCreation);
                }
            } else {
                // FIXME: manage answer and exception
                var answerId = NetworkUtils.readInt64(ch);
                int len = NetworkUtils.readInt32(ch);
                String str = NetworkUtils.readFullyAsString(ch, len);

                if (msgType == MessageHolder.MessageType.ANSWER) {
                    msgReg.completeFuture(answerId, deser.deserializeString(str));
                } else
                    msgReg.completeExceptionally(answerId, extractException(str));

                return true;
            }
        } catch (IOException e) {
            //System.err.println("Error while reading a message: " + e);

            return false;
        }
        return true;
    }

    private static Throwable extractException(String str) {
        int idx = str.indexOf(":");

        if (idx > 0) {
            String className = str.substring(0, idx);
            try {
                Class clazz = Class.forName(className);
                var constructor = clazz.getConstructor(String.class);

                return (Throwable) constructor.newInstance(str.substring(idx + 1));
            } catch (Throwable e) {
            }
        }
        return new IOException(str);
    }

    public static void setListener(BiConsumer<String, ActorOperation> newListener) {
        listener = newListener;
    }
}
