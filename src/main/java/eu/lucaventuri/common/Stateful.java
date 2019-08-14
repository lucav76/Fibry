package eu.lucaventuri.common;

public interface Stateful<S> {
    public S getState();
    public void setState(S state);
}
