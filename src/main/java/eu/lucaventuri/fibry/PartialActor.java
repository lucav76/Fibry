package eu.lucaventuri.fibry;

import java.util.function.Consumer;

public interface PartialActor<T, S> {
    public void sendMessage(T message);
    public void execAsync(Consumer<PartialActor<T, S>> worker);
    public void execAsync(Runnable worker);
    public void sendPoisonPill();
    public S getState();
    public void askExit();
}
