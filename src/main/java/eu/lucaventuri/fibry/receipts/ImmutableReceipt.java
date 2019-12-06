package eu.lucaventuri.fibry.receipts;

/** Generic receipt, that can be used to track what happened to a message */
public class ImmutableReceipt<T> {
    /** Unique id */
    private final String id;
    /** Progress */
    final ImmutableProgress progress;
    /** Message sent to the actor */
    private final T message;

    protected ImmutableReceipt(String id, T message, ImmutableProgress progress) {
        this.id = id;
        this.progress = progress;
        this.message = message;
    }

    public ImmutableProgress getProgress() {
        return progress;
    }

    public String getId() {
        return id;
    }

    public T getMessage() {
        return message;
    }

    public ImmutableReceipt<T> withProgress(ImmutableProgress newProgress) {
        return new ImmutableReceipt<>(id, message, newProgress);
    }

    public ImmutableReceipt<T> withProgressPercent(float newProgressPercent) {
        return new ImmutableReceipt<>(id, message, progress.withProgressPercent(newProgressPercent));
    }

    public ImmutableReceipt<T> withProgressPhase(String newProgressPhase) {
        return new ImmutableReceipt<>(id, message, progress.withProgressPhase(newProgressPhase));
    }

    public ImmutableReceipt<T> withProgressJson(String newProgressJson) {
        return new ImmutableReceipt<>(id, message, progress.withProgressJson(newProgressJson));
    }
}
