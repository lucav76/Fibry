package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.common.NameValuePair;
import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Concurrency primitive that can be used to broadcast the value of multiple variables, kept in a map.
 * It works also in a distributed environment, as long as the listeners are properly registered as remote actors.
 *
 * Writes from distributed nodes are also possible, sending messages to perform compareAndSetValue() or setValue().
 * It's recommended to use either one method or the other, and that's one reason why you cna create two types of actor
 */
public class SyncMap<T> extends MapBroadcaster<T> {
    public SyncMap(PubSub<OldAndNewValueWithName<T>> pubSub) {
        super(pubSub);
    }

    public SyncMap() {
        super(PubSub.sameThread());
    }

    public void setValue(String name, T newValue) {
        T oldValue = map.put(name, newValue);

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, new OldAndNewValueWithName<>(name, oldValue, newValue));
    }

    public boolean compareAndSetValue(String name, T expectedValue, T newValue) {
        if (!map.replace(name, expectedValue, newValue))
            return false;

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, new OldAndNewValueWithName<>(name, expectedValue, newValue));

        return true;
    }

    public Actor<OldAndNewValueWithName<T>, Boolean, Void> newCompareAndSetActor() {
        return ActorSystem.anonymous().newActorWithReturn(compareAndSetLogic());
    }

    public Actor<OldAndNewValueWithName<T>, Boolean, Void> newCompareAndSetActor(String name) {
        return ActorSystem.named(name).newActorWithReturn(compareAndSetLogic());
    }

    public Function<OldAndNewValueWithName<T>, Boolean> compareAndSetLogic() {
        return v -> compareAndSetValue(v.getName(), v.getOldValue(), v.getNewValue());
    }

    public Actor<NameValuePair<String, T>, Void, Void> newSetValueActor() {
        return ActorSystem.anonymous().newActor(setValueLogic());
    }

    public Actor<NameValuePair<String, T>, Void, Void> newSetValueActor(String name) {
        return ActorSystem.named(name).newActor(setValueLogic());
    }

    public Consumer<NameValuePair<String, T>> setValueLogic() {
        return nv -> setValue(nv.key, nv.value);
    }
}
