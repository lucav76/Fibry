package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Class holding a map of values and a list of subscribers */
public abstract class MapBroadcaster<T> {
    protected final ConcurrentHashMap<String, T> map = new ConcurrentHashMap<>();
    protected final PubSub<OldAndNewValueWithName<T>> pubSub;

    public MapBroadcaster(PubSub<OldAndNewValueWithName<T>> pubSub) {
        this.pubSub = pubSub;
    }

    public T getValue(String name) {
        return map.get(name);
    }

    public PubSub.Subscription subscribe(Consumer<OldAndNewValueWithName<T>> consumer) {
        return pubSub.subscribe(PubSub.DEFAULT_TOPIC, consumer);
    }

    public PubSub.Subscription subscribe(OldAndNewValueWithName.OldAndNewValueWithNameConsumer<T> consumer) {
        return pubSub.subscribe(PubSub.DEFAULT_TOPIC, consumer.asConsumer());
    }
}
