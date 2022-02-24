package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.function.Consumer;

/** Actor able to store the last value (typically received as a message from a SyncVar) and broadcast it to other actors */
public class SyncVarConsumer<T> extends ValueBroadcaster<T> implements Consumer<OldAndNewValue<T>> {
    public SyncVarConsumer(PubSub<OldAndNewValue<T>> pubSub) {
        super(pubSub);
    }

    public SyncVarConsumer() {
        super(PubSub.sameThread());
    }

    @Override
    public void accept(OldAndNewValue<T> message) {
        value.set(message.getNewValue());

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, message);
    }
}
