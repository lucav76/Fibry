package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.common.NameValuePair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actors can subscribe to this object and be notified when its value changes */
public class SubscribableMap<K, V> {
    private final PubSub<NameValuePair<K, V>> pubSub;
    private final String topic;
    private final AtomicInteger maxSubscribers = new AtomicInteger(1000);
    private final Map<K, V> map = new ConcurrentHashMap<>();
    private static final AtomicInteger progressive = new AtomicInteger();

    public SubscribableMap(PubSub<NameValuePair<K, V>> pubSub) {
        this.pubSub = pubSub;
        this.topic = "__SubscribableValue__" + progressive.incrementAndGet() + "__";

        assert pubSub != null;
    }

    public SubscribableMap() {
        // Same thread is recommended if there is no contention (e.g. only one thread changing the value), to reduce latency
        this(PubSub.sameThread());
    }

    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers.set(maxSubscribers);
    }

    public PubSub.Subscription subscribe(Consumer<NameValuePair<K, V>> consumer) {
        return pubSub.subscribe(topic, consumer, maxSubscribers.get());
    }

    public V get(K key) {
        return map.get(key);
    }

    public SubscribableMap<K, V> put(K key, V value) {
        map.put(key, value);
        pubSub.publish(topic, new NameValuePair<>(key, value));

        return this;
    }

    public SubscribableMap<K, V> remove(K key) {
        map.remove(key);
        pubSub.publish(topic, new NameValuePair<>(key, null));

        return this;
    }
}
