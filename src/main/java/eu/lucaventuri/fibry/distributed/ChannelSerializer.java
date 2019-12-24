package eu.lucaventuri.fibry.distributed;

/**
 * Serializer that can serialize both to String and byte[]. The implementation should be optimized, so that channels should avoid a costly double translation (e.g. from String to byte[] then to String again)
 */
public interface ChannelSerializer<T> {
    byte[] serialize(T object);

    String serializeToString(T object);
}
