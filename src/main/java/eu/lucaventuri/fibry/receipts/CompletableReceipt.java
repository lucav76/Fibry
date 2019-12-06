package eu.lucaventuri.fibry.receipts;

import java.util.concurrent.CompletableFuture;

public class CompletableReceipt<T, F> extends CompletableFuture<F> {
    private volatile ImmutableReceipt<T> receipt;

    public CompletableReceipt(ImmutableReceipt<T> receipt) {
        this.receipt = receipt;
    }

    public ImmutableReceipt<T> getReceipt() {
        return receipt;
    }

    public ImmutableProgress getProgressCorrected() {
        if (isDone() && !isCompletedExceptionally() && !isCancelled())
            return receipt.progress.withProgressPercent(1.0f);

        return receipt.progress;
    }

    public void setReceipt(ImmutableReceipt<T> receipt) {
        this.receipt = receipt;
    }
}
