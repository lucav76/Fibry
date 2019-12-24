package eu.lucaventuri.fibry.distributed;

public interface ChannelSerDeser<T, R> extends ChannelSerializer<T>, ChannelDeserializer<R> {
}
