package eu.lucaventuri.fibry.distributed;

import java.util.concurrent.CompletableFuture;

/**
 * Interface that needs to be implemented to setup a "fire and forget" channel between two actors, which is a prerequisite for creating a decoupled distributed actor system  (e.g. with queues)
 */
public interface RemoteActorChannelSendOnly<T> {
    void sendMessage(String remoteActorName, ChannelSerializer<T> ser, T message);
}
