package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.Exitable;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The group leader of ana actor pool does not process messages by itself; its role is to propagate the exit calls
 */
public class PoolActorLeader<T, R, S> extends Actor<T, R, S> {
    private final MultiExitable groupExit;

    PoolActorLeader(MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, MultiExitable groupExit, Consumer<S> initializer, Consumer<S> finalizer) {
        // TODO: Should we add support for auto healing?
        super(msg -> {
        }, queue, initialState, initializer, finalizer, null, Integer.MAX_VALUE, null, null);
        this.groupExit = groupExit;
    }

    @Override
    public boolean isExiting() {
        return groupExit.isExiting();
    }

    @Override
    public boolean isFinished() {
        return groupExit.isFinished();
    }

    @Override
    public void askExit() {
        groupExit.sendPoisonPill();
        groupExit.askExit();
        super.askExit();
    }

    @Override
    public void askExitAndWait() {
        askExit();
        groupExit.waitForExit();
    }

    @Override
    public void askExitAndWait(long timeout, TimeUnit unit) {
        askExit();
        groupExit.askExitAndWait(timeout, unit);
    }

    @Override
    public void waitForExit() {
        groupExit.waitForExit();

        if (finalizer != null)
            finalizer.accept(state);
    }

    @Override
    public void waitForExit(long timeout, TimeUnit unit) {
        groupExit.waitForExit(timeout, unit);

        if (finalizer != null)
            finalizer.accept(state);
    }

    MiniQueue<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> getQueue() {
        return queue;
    }

    MultiExitable getGroupExit() {
        return groupExit;
    }

    @Override
    public boolean sendPoisonPill() {
        groupExit.sendPoisonPill();
        execAsync(state -> askExit());

        return true;
    }
}
