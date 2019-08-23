package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.Stereotypes;

import java.util.function.Consumer;

/** Most extreme: one actor per message per subscriber, plus one actor per topic. In this way, messages don't block ach other.
 * Subscribers need to be thread safe as multiple messages can run concurrently.
 * This is recommended only if fibers are available or if the number of messages is relatively low, as it will create many (hopefully) short-lived threads.
 * */
public class PubSubOneActorPerMessage<T> extends PubSubOneActorPerTopic<T> {
    @Override
    public Subscription subscribe(String topic, Consumer<T> consumer) {
        return super.subscribe(topic, message -> Stereotypes.def().runOnce(() -> consumer.accept(message)));
    }
}
