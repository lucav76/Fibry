package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.fibry.SinkActor;

import java.util.function.Consumer;

/**
 * Very simple Pub/Sub, where you can choose the level of parallelism.
 * If fibers are not enabled, you should probably stick to oneActor(), else oneActorPerTopic() or oneActorPerSubscriber() might be a better choice.
 * sameThread() is synchronous.
 * <p>
 * PubSub objects are independent, so you could have the same topic in two PubSub and they would behave independently.
 */
public interface PubSub<T> {
    interface Subscription extends AutoCloseable {
        void cancel();

        default void close() {
            cancel();
        }
    }

    default Consumer<T> asConsumer(String topic) {
        return message -> publish(topic, message);
    }

    void publish(String topic, T message);

    Subscription subscribe(String topic, Consumer<T> consumer);

    /**
     * No new actors will be created, messages are delivered synchronously in the same thread of the caller, immediately.
     * The publish() operation can therefore take some time to complete, and it is the only strategy that can make the publish() slow.
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> sameThread() {
        return new PubSubSameThread<>();
    }

    /**
     * The message is sent to an actor that will deliver it to the subscribers; all the topic will use the same actor.
     * The function returns immediately as the subscriber will get notified by another actor. However, as there is only one actor, the messages are delivered oe after another, making the process potentially inefficient
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> oneActor() {
        return new PubSubOneActor<>();
    }

    /**
     * The message is sent to an actor that will deliver it to the subscribers; all the topic will use the same actor.
     * The important thing to notice is that you can specify the actor, which means that you can provide an Actor Pool to achieve a better utilization of your CPU.
     * This strategy with an actor pool is recommended for threads.
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> oneActor(SinkActor actor) {
        return new PubSubOneActor<>(actor);
    }

    /**
     * The message is sent to an actor that will deliver it to the subscribers; every topic has a dedicate actor.
     * This works well if the topics usage is kind of uniform, and if there are not huge spikes on a particular topic.
     * This is recommended with threads or if the number of topics is not too high.
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> oneActorPerTopic() {
        return new PubSubOneActorPerTopic<>();
    }

    /**
     * The message is sent to an actor that will deliver it to the subscribers; every topic has a dedicate actor, and there is an additional actor for each subscriber.
     * This allow parallelism at a subscriber level.
     * This strategy is recommended with fibers, unless the number of subscribers is low.
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> oneActorPerSubscriber() {
        return new PubSubOneActorPerSubscriber<>();
    }

    /**
     * The message is sent to an actor that will deliver it to the subscribers, creating a new actor for each message for each subscriber.
     * This allow maximum parallelism, at a message level.
     * This strategy is recommended with fibers, unless the number of messages per second is guaranteed to be low.
     * @param <T> Type of messages
     * @return a new PubSub system
     */
    static <T> PubSub<T> oneActorPerMessage() {
        return new PubSubOneActorPerMessage<>();
    }
}
