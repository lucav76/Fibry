package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.SinkActor;
import eu.lucaventuri.fibry.Stereotypes;

import javax.swing.*;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Publishing happens on dedicated actor (one actor per topic, so only subscribers of the same topic can block each other)
 */
public class PubSubOneActorPerTopic<T> extends PubSubSameThread<T> {
    private final static ConcurrentHashMap<String, SinkActor<List>> actors = new ConcurrentHashMap();

    public PubSubOneActorPerTopic() {
    }

    @Override
    public void publish(String topic, T message) {
        SinkActor<List> actor = actors.get(topic);

        if (actor != null)
            actor.execAsync(() -> super.publish(topic, message));
    }

    @Override
    public Subscription subscribe(String topic, Consumer<T> consumer, int maxSubscribers) {
        Subscription subscription = super.subscribe(topic, consumer, maxSubscribers);

        if (subscription == null)
            return null;

        ensureActor(topic, subscribers.get(topic));

        return () -> {
            subscription.cancel();

            if (!subscribers.contains(topic)) {
                SinkActor<List> actor = actors.get(topic);

                if (actor != null) {
                    actor.askExit();
                    actors.remove(topic);
                }
            }
        };
    }

    private SinkActor<List> ensureActor(String topic, List state) {
        return actors.computeIfAbsent(topic, key -> Stereotypes.def().sink(state));
    }
}
