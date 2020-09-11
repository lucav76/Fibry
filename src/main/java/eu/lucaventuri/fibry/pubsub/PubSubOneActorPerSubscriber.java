package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.function.Consumer;

/**
 * One dedicated actor for each subscriber, plus one actor per topic. In this way, subscribers cannot block each other.
 * This is overkill if your consumer is already an actor
 */
public class PubSubOneActorPerSubscriber<T> extends PubSubOneActorPerTopic<T> {
    @Override
    public Subscription subscribe(String topic, Consumer<T> consumer, int maxSubscribers) {
        Actor<T, Void, Void> subscriberActor = ActorSystem.anonymous().newActor(consumer);
        Subscription subscription = super.subscribe(topic, subscriberActor, maxSubscribers);

        if (subscription == null)
            return subscription;

        return () -> {
            subscription.cancel();
            subscriberActor.askExit();
            subscriberActor.sendPoisonPill();
        };
    }
}
