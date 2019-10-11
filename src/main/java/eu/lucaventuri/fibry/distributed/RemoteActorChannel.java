package eu.lucaventuri.fibry.distributed;

import java.util.concurrent.CompletableFuture;

/**
 * Interface that needs to be implemented to setup a channel between two actors, which a a prerequisite for creating a distributed actor system
 */
public interface RemoteActorChannel<T, R> {
    CompletableFuture<R> sendMessageReturn(String remoteActorName, Serializer<T> ser, Deserializer<R> deser, T message);

    /** Serializer that can serialize both to String and byte[]. The implementation should be optimized, so that channels should avoid a costly double translation (e.g. from String to byte[] then to String again) */
    interface Serializer<T> {
        byte[] serialize(T object);

        String serializeToString(T object);
    }

    interface Deserializer<R> {
        R deserialize(byte[] object);

        R deserializeString(String object);
    }

    interface SerDeser<T, R> extends Serializer<T>, Deserializer<R> {
    }
}
