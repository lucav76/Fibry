package eu.lucaventuri.fibry;

import java.util.function.Consumer;

public interface PartialActor<T, S> {
    public PartialActor<T, S> sendMessage(T message);
    public void execAsync(Consumer<PartialActor<T, S>> worker);
    public void execAsync(Runnable worker);
    public boolean sendPoisonPill();
    public S getState();
    public void askExit();
    public boolean isExiting();
}
