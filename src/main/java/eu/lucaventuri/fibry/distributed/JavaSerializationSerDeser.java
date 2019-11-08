package eu.lucaventuri.fibry.distributed;

import java.io.*;

/**
 * Can serialize using Java Serialization.
 * It has been added for convenience, but it is deprecated as it is in general considered a good practice to avoid
 * Java serialization, because of its problems and limitations
 */
@Deprecated
public class JavaSerializationSerDeser<T, R> implements RemoteActorChannel.SerDeser<T, R> {
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
