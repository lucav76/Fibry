package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Class holding a value and a list of subscribers */
public abstract class ValueBroadcaster<T> {
    protected final AtomicReference<T> value = new AtomicReference<>();
    protected final PubSub<OldAndNewValue<T>> pubSub;

    public ValueBroadcaster(PubSub<OldAndNewValue<T>> pubSub) {
        this.pubSub = pubSub;
    }

    public T getValue() {
        return value.get();
    }

    public PubSub.Subscription subscribe(Consumer<OldAndNewValue<T>> consumer) {
        return pubSub.subscribe(PubSub.DEFAULT_TOPIC, consumer);
    }

    public PubSub.Subscription subscribe(OldAndNewValue.OldAndNewValueConsumer<T> consumer) {
        return pubSub.subscribe(PubSub.DEFAULT_TOPIC, consumer.asConsumer());
    }
}
