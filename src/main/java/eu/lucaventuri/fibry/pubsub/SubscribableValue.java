package eu.lucaventuri.fibry.pubsub;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Actors can subscribe to this object and be notified when its value changes */
public class SubscribableValue<T> {
    private final PubSub<T> pubSub;
    private final String topic;
    private final AtomicInteger maxSubscribers = new AtomicInteger(1000);
    private final AtomicReference<T> value = new AtomicReference<>();
    private static final AtomicInteger progressive = new AtomicInteger();

    public SubscribableValue(PubSub<T> pubSub) {
        this.pubSub = pubSub;
        this.topic = "__SubscribableValue__" + progressive.incrementAndGet() + "__";

        assert pubSub != null;
    }

    public SubscribableValue() {
        // Same thread is recommended if there is no contention (e.g. only one thread changing the value), to reduce latency
        this(PubSub.sameThread());
    }

    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers.set(maxSubscribers);
    }

    public PubSub.Subscription subscribe(Consumer<T> consumer) {
        return pubSub.subscribe(topic, consumer, maxSubscribers.get());
    }

    public T get() {
        return value.get();
    }

    public SubscribableValue<T> set(T newValue) {
        value.set(newValue);
        pubSub.publish(topic, newValue);

        return this;
    }
}
