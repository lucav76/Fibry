package eu.lucaventuri.fibry;

import eu.lucaventuri.common.Stateful;

import java.util.function.Consumer;

public interface PartialActor<T, S>  extends Stateful<S> {
    public PartialActor<T, S> sendMessage(T message);
    public void execAsync(Consumer<PartialActor<T, S>> worker);
    public void execAsync(Runnable worker);
    public boolean sendPoisonPill();
    public void askExit();
    public boolean isExiting();
}
