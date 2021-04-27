package eu.lucaventuri.fibry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Strategy used to create the actors */
public enum CreationStrategy {
    /** One thread per actor */
    THREAD {
        @Override
        public <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            new Thread(actor::processMessages).start();

            return actor;
        }

        @Override
        public Executor newExecutor() {
            // Executed every task in a new thread
            return run -> new Thread(run).start();
        }
    },
    /** One fiber per actor */
    FIBER {
        @Override
        public <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            ActorUtils.runAsFiber(actor::processMessages);

            return actor;
        }

        @Override
        public Executor newExecutor() {
            // Executed every task in a new virtual thread / fiber
            return ActorUtils.newFibersExecutor();
        }
    },
    /** If fibers are available, then it uses FIBER else it uses THREAD */
    AUTO {
        @Override
        public <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor) {
            return ActorUtils.areFibersAvailable() ? FIBER.start(actor) : THREAD.start(actor);
        }

        @Override
        public Executor newExecutor() {
            return ActorUtils.areFibersAvailable() ? FIBER.newExecutor() : THREAD.newExecutor();
        }
    };

    /** Starts an actor */
    public abstract <T, R, S> BaseActor<T, R, S> start(BaseActor<T, R, S> actor);
    public abstract Executor newExecutor();

    /** Return the strategies that are available */
    public Iterable<CreationStrategy> available() {
        List<CreationStrategy> list = new ArrayList<>();

        list.add(THREAD);
        if (ActorUtils.areFibersAvailable())
            list.add(FIBER);

        return list;
    }
}
