package eu.lucaventuri.fibry.receipts;

/** Generic receipt, that can be used to track what happened to a message */
public class ImmutableReceipt<T> {
    /** Unique id */
    private final String receiptId;
    /** Progress */
    final ImmutableProgress progress;
    /** Message sent to the actor */
    private final T message;

    protected ImmutableReceipt(String receiptId, T message, ImmutableProgress progress) {
        this.receiptId = receiptId;
        this.progress = progress;
        this.message = message;
    }

    public ImmutableProgress getProgress() {
        return progress;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public T getMessage() {
        return message;
    }

    public ImmutableReceipt<T> withProgress(ImmutableProgress newProgress) {
        return new ImmutableReceipt<>(receiptId, message, newProgress);
    }

    public ImmutableReceipt<T> withProgressPercent(float newProgressPercent) {
        return new ImmutableReceipt<>(receiptId, message, progress.withProgressPercent(newProgressPercent));
    }

    public ImmutableReceipt<T> withProgressPhase(String newProgressPhase) {
        return new ImmutableReceipt<>(receiptId, message, progress.withProgressPhase(newProgressPhase));
    }

    public ImmutableReceipt<T> withProgressJson(String newProgressJson) {
        return new ImmutableReceipt<>(receiptId, message, progress.withProgressJson(newProgressJson));
    }
}
