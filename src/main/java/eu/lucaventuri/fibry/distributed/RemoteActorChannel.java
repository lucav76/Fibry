package eu.lucaventuri.fibry.distributed;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface that needs to be implemented to setup a channel between two actors, which is a prerequisite for creating a distributed actor system
 */
public interface RemoteActorChannel<T, R> {
    CompletableFuture<R> sendMessageReturn(String remoteActorName, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, T message);

    default void sendMessage(String remoteActorName, ChannelSerializer<T> ser, ChannelDeserializer<R> deser, T message) throws IOException {
        sendMessageReturn(remoteActorName, ser, deser, message);
    }
}
