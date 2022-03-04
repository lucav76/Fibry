package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.function.Consumer;

/** Actor able to store the last value of objects in a map (typically received as a message from a SyncMap) and broadcast it to other actors */
public class SyncMapConsumer<T> extends MapBroadcaster<T> implements Consumer<OldAndNewValueWithName<T>> {
    public SyncMapConsumer(PubSub<OldAndNewValueWithName<T>> pubSub) {
        super(pubSub);
    }

    public SyncMapConsumer() {
        super(PubSub.sameThread());
    }

    @Override
    public void accept(OldAndNewValueWithName<T> message) {
        map.put(message.getName(), message.getNewValue());

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, message);
    }
}
