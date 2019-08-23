package eu.lucaventuri.fibry.pubsub;

import eu.lucaventuri.fibry.SinkActor;
import eu.lucaventuri.fibry.Stereotypes;

/** Publishing happens on dedicated actors (one actor publish all the topics, subscribers will not block the caller, but they will all run ont he same actor) */
public class PubSubOneActor<T> extends PubSubSameThread<T> {
    private final SinkActor actor;

    public PubSubOneActor(SinkActor actor) {
        this.actor = actor;
    }

    public PubSubOneActor() {
        this.actor = Stereotypes.def().sink(null);
    }

    @Override
    public void publish(String topic, T message) {
        actor.execAsync(() -> super.publish(topic, message));
    }
}
