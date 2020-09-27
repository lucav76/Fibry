package eu.lucaventuri.fibry.receipts;

/** Generic receipt, that can be used to track what happened to a message */
public class ImmutableReceipt {
    /** Unique id */
    private final String receiptId;
    /** Progress */
    final ImmutableProgress progress;

    public ImmutableReceipt(String receiptId, ImmutableProgress progress) {
        this.receiptId = receiptId;
        this.progress = progress;
    }

    public ImmutableProgress getProgress() {
        return progress;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public ImmutableReceipt withProgress(ImmutableProgress newProgress) {
        return new ImmutableReceipt(receiptId, newProgress);
    }

    public ImmutableReceipt withProgressPercent(float newProgressPercent) {
        return new ImmutableReceipt(receiptId, progress.withProgressPercent(newProgressPercent));
    }

    public ImmutableReceipt withProgressPhase(String newProgressPhase) {
        return new ImmutableReceipt(receiptId, progress.withProgressPhase(newProgressPhase));
    }

    public ImmutableReceipt withProgressJson(String newProgressJson) {
        return new ImmutableReceipt(receiptId, progress.withProgressJson(newProgressJson));
    }
}
