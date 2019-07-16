package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.MultiExitable;
import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.functional.Either3;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** THe group leader of ana actor pool does not process messages by itself; its role is to propagate the exit calls */
public class PoolActorLeader<T, R, S> extends Actor<T, R, S> {
    private final MultiExitable groupExit;

    PoolActorLeader(BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> queue, S initialState, MultiExitable groupExit) {
        super(msg -> {
        }, queue, initialState);
        this.groupExit = groupExit;
    }

    @Override
    void processMessages() {
        Exceptions.log(() -> {
            SystemUtils.sleep(10);
        });
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
        groupExit.askExit();
    }

    @Override
    public void askExitAndWait() {
        groupExit.askExitAndWait();
    }

    @Override
    public void askExitAndWait(long timeout, TimeUnit unit) {
        groupExit.askExitAndWait(timeout, unit);
    }

    @Override
    public void waitForExit() {
        groupExit.waitForExit();
    }

    @Override
    public void waitForExit(long timeout, TimeUnit unit) {
        groupExit.waitForExit(timeout, unit);
    }

    BlockingDeque<Either3<Consumer<PartialActor<T, S>>, T, MessageWithAnswer<T, R>>> getQueue() {
        return queue;
    }

    MultiExitable getGroupExit() {
        return groupExit;
    }
}
