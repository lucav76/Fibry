package eu.lucaventuri.fibry.distributed;

public class ObjectSerializerUsingToString<T> implements RemoteActorChannel.Serializer<T> {
    @Override
    public byte[] serialize(T message) {
        return message == null ? null : message.toString().getBytes();
    }

    @Override
    public String serializeToString(T message) {
        return message == null ? null : message.toString();
    }
}
