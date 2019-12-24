package eu.lucaventuri.fibry.distributed;

public interface ChannelDeserializer<R> {
    R deserialize(byte[] object);

    R deserializeString(String object);
}
