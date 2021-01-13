package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.fibry.BaseActor;

import java.io.IOException;
import java.net.InetAddress;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Registry able to signal the presence of actors, typically to a remote machine; the information are meant to be able to locate the actors,
 * are just text, so it is dependent on the caller to provide proper information; please notice that the actors might
 * need to be located with a different tehcnology, e.g. we could have a Multicast Registry with actors that need to be contacted via TCP using a proxy.
 * In general, it's better if an actor register itself only once in a given registry.
 */
public interface ActorRegistry {
    class ActorAndSupplier {
        final BaseActor actor;
        final Supplier<String> infoSupplier;

        public ActorAndSupplier(BaseActor actor, Supplier<String> infoSupplier) {
            this.actor = actor;
            this.infoSupplier = infoSupplier;
        }
    }

    <T, R, S> void registerActor(BaseActor<T, R, S> actor, Supplier<String> infoSupplier);

    default <T, R, S> void registerActor(BaseActor<T, R, S> actor, String constantInfo) {
        registerActor(actor, () -> constantInfo);
    }

    <T, R, S> void registerActor(ActorAndSupplier... actors);


    <T, R, S> boolean deregisterActor(BaseActor<T, R, S> actor);

    static ActorRegistry usingMulticast(InetAddress address, int multicastPort, int msRefresh, int msGraceSendRefresh, int msCleanRemoteActors, int msGgraceCleanRemoteActors) throws IOException {
        return new MulticastActorRegistry(address, multicastPort, msRefresh, msGraceSendRefresh, msCleanRemoteActors, msGgraceCleanRemoteActors);
    }

    /** Visit all the remote actors, id and info */
    void visitRemoteActors(BiConsumer<String, String> worker);

    int getHowManyRemoteActors();
}
