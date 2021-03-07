package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.NetworkUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

class MessageHolder<R> {
    private final String message;
    private final MessageType type;
    private final long answerId;

    enum MessageType {
        WITH_RETURN((byte) 'R'), VOID((byte) 'V'), ANSWER((byte) 'A'), EXCEPTION((byte) 'E');

        final byte signature;

        MessageType(byte signature) {
            this.signature = signature;
        }

        static MessageType fromSignature(byte signature) {
            for (var value : values()) {
                if (value.signature == signature)
                    return value;
            }

            throw new IllegalArgumentException("Unrecognized MessageType signature " + signature);
        }
    }

    private MessageHolder(String message, MessageType type, long answerId) {
        this.message = message;
        this.type = type;
        this.answerId = answerId;
    }

    CompletableFuture<R> writeMessage(SocketChannel ch, MessageRegistry<R> msgReg) throws IOException {
        final CompletableFuture<R> future;
        if (type == MessageType.WITH_RETURN) {
            var buf = ByteBuffer.allocate(9);
            var idAndFuture = msgReg.getNewFuture();

            //System.out.println("writeMessage(): " + message + " - New " + idAndFuture.id);

            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put(type.signature);
            buf.putLong(idAndFuture.id);
            buf.flip();

            ch.write(buf);

            future = idAndFuture.future;
        } else if (type == MessageType.VOID) {
            ch.write(ByteBuffer.wrap(new byte[]{type.signature}));
            // TODO: it does not wait for the messge to be processed. Id it fine?
            future = null;
        } else if (type == MessageType.ANSWER) {
            var buf = ByteBuffer.allocate(9);

            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put(type.signature);
            buf.putLong(answerId);
            buf.flip();

            ch.write(buf);
            future = null;
        } else if (type == MessageType.EXCEPTION) {
            var buf = ByteBuffer.allocate(9);

            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put(type.signature);
            buf.putLong(answerId);
            buf.flip();

            ch.write(buf);
            future = null;
        } else
            throw new IllegalStateException("Unexpected MessageType " + type);

        ch.write(NetworkUtils.asBufferWithLength32(message));

        return future;
    }

    @Override
    public String toString() {
        return "MessageHolder{" +
                "message='" + message + '\'' +
                ", type=" + type +
                '}';
    }

    static <R> MessageHolder<R> newWithReturn(String message) {
        return new MessageHolder<R>(message, MessageType.WITH_RETURN, 0);
    }

    static <R> MessageHolder<R> newVoid(String message) {
        return new MessageHolder<R>(message, MessageType.VOID, 0);
    }

    static <R> MessageHolder<R> newAnswer(long messageId, String message) {
        return new MessageHolder<R>(message, MessageType.ANSWER, messageId);
    }

    static <R> MessageHolder<R> newException(long messageId, Throwable ex) {
        return new MessageHolder<R>(ex.toString(), MessageType.EXCEPTION, messageId);
    }
}
