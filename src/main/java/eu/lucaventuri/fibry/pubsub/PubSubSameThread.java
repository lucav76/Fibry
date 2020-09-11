package eu.lucaventuri.fibry.pubsub;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publishing happen on the thread of the caller (e.g. subscribers can block the caller)
 * Thread safe (e.g. publish() and subscribe() and cancel() can be called from any thread) implementation of PubSub that reuses the thread of the publisher to send the messages
 */
class PubSubSameThread<T> implements PubSub<T> {
    protected final ConcurrentHashMap<String, List<Consumer<T>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, T message) {
        List<Consumer<T>> list = subscribers.get(topic);

        if (list != null)
            for (Consumer<T> consumer : list) {
                consumer.accept(message);
            }
    }

    @Override
    public Subscription subscribe(String topic, Consumer<T> consumer, int maxSubscribers) {
        // We try to make the subscription thread safe without locking on subscribers (for performance) or somehow on topic (for memory usage).

        // If empty, crates with the new consumer
        List<Consumer<T>> topicSubscribers = subscribers.computeIfAbsent(topic, key -> oneElementList(consumer));

        synchronized (topicSubscribers) {
            if (topicSubscribers.size() >= maxSubscribers)
                return null;

            // This can only happen if another thread cleared it, so it also removed it from subscribers. We have to add a new one
            if (topicSubscribers.isEmpty()) {
                subscribers.computeIfAbsent(topic, key -> oneElementList(consumer)); // Just in case another subscriber created it
            } else if (!topicSubscribers.contains(consumer))
                topicSubscribers.add(consumer);
        }

        return () -> {
            synchronized (topicSubscribers) {
                topicSubscribers.remove(consumer);

                if (topicSubscribers.size() == 0) {
                    subscribers.remove(topic);
                }
            }
        };
    }

    @Override
    public int getNumberOfActors(String topic) {
        List<Consumer<T>> topicSubscribers = subscribers.get(topic);

        if (topicSubscribers == null)
            return 0;

        return topicSubscribers.size();
    }

    @Override
    public Collection<Consumer<T>> getActors(String topic) {
        List<Consumer<T>> topicSubscribers = subscribers.get(topic);

        if (topicSubscribers == null)
            return List.of();

        return topicSubscribers;
    }

    @Override
    public void removeTopic(String topic) {
        List<Consumer<T>> topicSubscribers = subscribers.remove(topic);

        if (topicSubscribers == null)
            return;

        topicSubscribers.clear();
    }

    private List<Consumer<T>> oneElementList(Consumer<T> consumer) {
        List<Consumer<T>> newSubscribers = new CopyOnWriteArrayList<>();

        newSubscribers.add(consumer);

        return newSubscribers;
    }
}