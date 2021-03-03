package eu.lucaventuri.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public final class NetworkUtils {
    private NetworkUtils() {

    }

    public static ByteBuffer asBufferWithLength32(String str) {
        var bytes = str==null ? new byte[0] : str.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(bytes.length + 4);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(bytes.length);
        buf.put(bytes);
        buf.flip();

        return buf;
    }

    public static ByteBuffer readFully(SocketChannel ch, int size) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(size);

        while (buf.remaining() > 0) {
            if (ch.read(buf) < 0)
                break;
        }

        if (buf.remaining() > 0)
            throw new IOException("Channel interrupted!");

        buf.flip();

        return buf;
    }

    public static String readFullyAsString(SocketChannel ch, int size) throws IOException {
        return new String(readFully(ch, size).array());
    }

    public static byte readInt8(SocketChannel ch) throws IOException {
        return readFully(ch, 1).get();
    }

    public static short readInt16(SocketChannel ch) throws IOException {
        return readFully(ch, 2).getShort();
    }

    public static int readInt32(SocketChannel ch) throws IOException {
        return readFully(ch, 4).getInt();
    }

    public static long readInt64(SocketChannel ch) throws IOException {
        return readFully(ch, 8).getLong();
    }

    public static float readFloat(SocketChannel ch) throws IOException {
        return readFully(ch, 4).getFloat();
    }

    public static double readDouble(SocketChannel ch) throws IOException {
        return readFully(ch, 8).getDouble();
    }

    public static void writeInt16(SocketChannel ch, short value) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(2);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putShort(value);
        buf.flip();

        ch.write(buf);
    }

    public static void writeInt32(SocketChannel ch, int value) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(4);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(value);
        buf.flip();

        ch.write(buf);
    }

    public static void writeInt64(SocketChannel ch, long value) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(8);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putLong(value);
        buf.flip();

        ch.write(buf);
    }

    public static void writeFloat(SocketChannel ch, float value) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(4);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putFloat(value);
        buf.flip();

        ch.write(buf);
    }

    public static void writeDouble(SocketChannel ch, double value) throws IOException {
        assert ch.isBlocking();

        ByteBuffer buf = ByteBuffer.allocate(8);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putDouble(value);
        buf.flip();

        ch.write(buf);
    }
}
