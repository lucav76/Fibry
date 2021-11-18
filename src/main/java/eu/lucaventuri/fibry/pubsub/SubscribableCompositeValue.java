package eu.lucaventuri.fibry.pubsub;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actors can subscribe to this object and be notified when its composite value changes.
 * One particular characteristic is that this object hols an array of items, and the subscriber will be notified
 * of a change only if all the elements are not null, so effectively this class will wait until all the elements are not null
 * before sending any message
 * */
public class SubscribableCompositeValue<V> {
    private final PubSub<V[]> pubSub;
    private final String topic;
    private final AtomicInteger maxSubscribers = new AtomicInteger(1000);
    private final V[] composeValue;
    private static final AtomicInteger progressive = new AtomicInteger();

    public SubscribableCompositeValue(PubSub<V[]> pubSub, Class<V> clazz, int numElements) {
        this.pubSub = pubSub;
        this.topic = "__SubscribableValue__" + progressive.incrementAndGet() + "__";
        this.composeValue = (V[]) Array.newInstance(clazz, numElements);

        assert pubSub != null;
    }

    public SubscribableCompositeValue(Class<V> clazz, int numElements) {
        // Same thread is recommended if there is no contention (e.g. only one thread changing the value), to reduce latency
        this(PubSub.sameThread(), clazz, numElements);
    }

    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers.set(maxSubscribers);
    }

    public PubSub.Subscription subscribe(Consumer<V[]> consumer) {
        return pubSub.subscribe(topic, consumer, maxSubscribers.get());
    }

    public V[] get() {
        return Arrays.copyOf(composeValue, composeValue.length);
    }

    public SubscribableCompositeValue<V> put(int position, V value) {
        composeValue[position] = value;

        if (isComposedValueReady())
            pubSub.publish(topic, composeValue);

        return this;
    }

    private boolean isComposedValueReady() {
        for (V v : composeValue) {
            if (v == null)
                return false;
        }

        return true;
    }

    public SubscribableCompositeValue<V> clear() {
        Arrays.fill(composeValue, null);

        return this;
    }
}
