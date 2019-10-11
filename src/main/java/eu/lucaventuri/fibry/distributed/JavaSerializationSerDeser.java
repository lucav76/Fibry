package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;

import java.io.*;

public class JavaSerializationSerDeser<T, R> implements RemoteActorChannel.SerDeser<T, R> {
    private final int maxDeserializationObjectSize;

    public JavaSerializationSerDeser(int maxDeserializationObjectSize) {
        this.maxDeserializationObjectSize = maxDeserializationObjectSize;
    }

    @Override
    public byte[] serialize(T object) {
        try (
                var buf = new ByteArrayOutputStream();
                var os = new ObjectOutputStream(buf)) {

            os.writeObject(object);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serializeToString(T object) {
        throw new UnsupportedOperationException("Java serialization is not meant to be converted to a string. Please specify in the channel to use Base64 encoding");
    }

    @Override
    public R deserialize(byte[] object) {
        try (
                var buf = new ByteArrayInputStream(object);
                var is = new ObjectInputStream(buf)) {

                return (R) is.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public R deserializeString(String object) {
        throw new UnsupportedOperationException("Java serialization is not meant to be converted to a string. Please specify in the channel to use Base64 encoding");
    }
}
