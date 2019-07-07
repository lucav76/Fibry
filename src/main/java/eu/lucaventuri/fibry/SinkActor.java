package eu.lucaventuri.fibry;

public interface SinkActor<S> {
    public void execAsync(Runnable worker);
    public S getState();
    public void askExit();
    public boolean isExiting();
    public void waitForExit();
}
