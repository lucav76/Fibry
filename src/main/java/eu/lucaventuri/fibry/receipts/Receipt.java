package eu.lucaventuri.fibry.receipts;

import java.util.concurrent.CompletableFuture;

/** Generic receipt, that can be used to track what happened to a message */
public class Receipt<T, F> extends CompletableFuture<F> {
    /** Unique id */
    final String id;
    /** Notes, e.g. message for the use or json with more data */
    volatile String notes;
    /** Progress, from 0 to 1 */
    volatile float progressPercent;
    /** Message sent to the actor */
    final T message;

    protected Receipt(String id, T message) {
        this.id = id;
        this.message = message;
        this.progressPercent = 0;
        this.notes = null;
    }

    public float getProgressPercent() {
        if (isDone() && !isCancelled() && !isCompletedExceptionally())
            return 1.0f;

        return progressPercent;
    }

    public void setProgress(float progressPercent, String notes) {
        this.progressPercent = progressPercent;
        this.notes = notes;
    }

    public void setProgress(float progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getId() {
        return id;
    }

    public String getNotes() {
        return notes;
    }

    public T getMessage() {
        return message;
    }
}
