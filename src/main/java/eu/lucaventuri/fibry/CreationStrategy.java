package eu.lucaventuri.fibry;

import java.util.ArrayList;
import java.util.List;

/** Strategy used to create the actors */
public enum CreationStrategy {
    /** One thread per actor */
    THREAD {
        @Override
        <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            new Thread(actor::processMessages).start();

            return actor;
        }
    },
    /** One fiber per actor */
    FIBER {
        @Override
        <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            ActorUtils.runAsFiber(() -> {
                actor.processMessages();
            });

            return actor;
        }
    },
    /** If fibers are available, the it uses FIBER else it uses THREAD */
    AUTO {
        @Override
        <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            return ActorUtils.areFibersAvailable() ? FIBER.start(actor) : THREAD.start(actor);
        }
    };

    /** Starts an actor */
    abstract <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor);

    /** Return the strategies that are available */
    public Iterable<CreationStrategy> available() {
        List<CreationStrategy> list = new ArrayList<>();

        list.add(THREAD);
        if (ActorUtils.areFibersAvailable())
            list.add(FIBER);

        return list;
    }
}
