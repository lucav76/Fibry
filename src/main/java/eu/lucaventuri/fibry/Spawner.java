package eu.lucaventuri.fibry;

import eu.lucaventuri.common.CountingExitable;
import eu.lucaventuri.common.Exitable;

import java.util.function.Consumer;
import java.util.function.Function;

/** Class able to create new actors with a predefined logic */
class Spawner<T, R, S> extends CountingExitable {
    private final Function<T, R> logic;
    private final ActorSystem.NamedStateActorCreator<S> creator;
    private Consumer<S> finalizer = state -> addFinished();

    Spawner(ActorSystem.NamedStateActorCreator<S> creator,  Function<T, R> logic) {
        this.logic = logic;
        this.creator=creator;
    }

    public Actor<T,R,S> spawn() {
        addCreated();

        return creator.newActorWithReturn(logic);
    }

    public Consumer<S> finalizer() {
        return finalizer;
    }
}
