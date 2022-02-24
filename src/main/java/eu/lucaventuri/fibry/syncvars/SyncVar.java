package eu.lucaventuri.fibry.syncvars;

import eu.lucaventuri.fibry.Actor;
import eu.lucaventuri.fibry.ActorSystem;
import eu.lucaventuri.fibry.pubsub.PubSub;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Concurrency primitive that can be used to broadcast the value of a variable.
 * It works also in a distributed environment, as long as the listeners are properly registered as remote actors.
 *
 * Writes from distributed nodes are also possible, sending messages to perform compareAndSetValue() or setValue().
 * It's recommended to use either one method or the other, and that's one reason why you cna create two types of actor
 */
public class SyncVar<T> extends ValueBroadcaster<T> {
    public SyncVar(PubSub<OldAndNewValue<T>> pubSub) {
        super(pubSub);
    }

    public SyncVar() {
        super(PubSub.sameThread());
    }

    public void setValue(T newValue) {
        T oldValue = value.getAndSet(newValue);

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, new OldAndNewValue<>(oldValue, newValue));
    }

    public boolean compareAndSetValue(T expectedValue, T newValue) {
        if (!value.compareAndSet(expectedValue, newValue))
            return false;

        if (pubSub != null)
            pubSub.publish(PubSub.DEFAULT_TOPIC, new OldAndNewValue<>(expectedValue, newValue));

        return true;
    }

    public Actor<OldAndNewValue<T>, Boolean, Void> newCompareAndSetActor() {
        return ActorSystem.anonymous().newActorWithReturn(compareAndSetLogic());
    }

    public Actor<OldAndNewValue<T>, Boolean, Void> newCompareAndSetActor(String name) {
        return ActorSystem.named(name).newActorWithReturn(compareAndSetLogic());
    }

    public Function<OldAndNewValue<T>, Boolean> compareAndSetLogic() {
        return v -> compareAndSetValue(v.getOldValue(), v.getNewValue());
    }

    public Actor<T, Void, Void> newSetValueActor() {
        return ActorSystem.anonymous().newActor(setValueLogic());
    }

    public Actor<T, Void, Void> newSetValueActor(String name) {
        return ActorSystem.named(name).newActor(setValueLogic());
    }

    public Consumer<T> setValueLogic() {
        return this::setValue;
    }
}
