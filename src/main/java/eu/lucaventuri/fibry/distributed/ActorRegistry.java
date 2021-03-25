package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.fibry.BaseActor;

import java.io.IOException;
import java.net.InetAddress;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Registry able to signal the presence of actors (actors discovery), typically to a remote machine; the information are meant to be able to locate the actors,
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

    static ActorRegistry usingMulticast(InetAddress address, int multicastPort, int msRefresh, int msGraceSendRefresh, int msCleanRemoteActors, int msGgraceCleanRemoteActors, Predicate<ActorAction> validator) throws IOException {
        return new MulticastActorRegistry(address, multicastPort, msRefresh, msGraceSendRefresh, msCleanRemoteActors, msGgraceCleanRemoteActors, validator);
    }

    /** Visit all the remote actors, id and info */
    void visitRemoteActors(BiConsumer<String, String> worker);

    int getHowManyRemoteActors();

    public static class ActorAction {
        public final BaseActorRegistry.RegistryAction action;
        public final String id;
        public final String info;

        public ActorAction(BaseActorRegistry.RegistryAction action, String id, String info) {
            this.action = action;
            this.id = id;
            this.info = info;
        }

        public static String toStringProtocol(ActorAction action) {
            if (action.action== BaseActorRegistry.RegistryAction.JOINING)
                return "J";
            String actionCode = action.action == BaseActorRegistry.RegistryAction.REGISTER ? "R|" : "D|";

            return actionCode + action.id + "|" + action.info;
        }

        public static ActorAction fromStringProtocol(String info) {
            char code = info.charAt(0);

            if (code=='J') {
                if (info.length()>1)
                    throw new IllegalArgumentException("Wrong format [1]");

                return new ActorAction(BaseActorRegistry.RegistryAction.JOINING, null, null);
            }

            if (code != 'R' && code != 'D')
                throw new IllegalArgumentException("Wrong action code: " + code);
            if (info.charAt(1) != '|')
                throw new IllegalArgumentException("Wrong format [2]");

            int endInfo = info.indexOf('|', 2);

            if (endInfo < 0)
                throw new IllegalArgumentException("Wrong format [3]");

            BaseActorRegistry.RegistryAction action = code == 'R' ? BaseActorRegistry.RegistryAction.REGISTER : BaseActorRegistry.RegistryAction.DEREGISTER;
            String id = info.substring(2, endInfo);

            return new ActorAction(action, id, info.substring(endInfo + 1));
        }
    }
}
