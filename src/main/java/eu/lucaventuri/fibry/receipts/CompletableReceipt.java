package eu.lucaventuri.fibry.receipts;

import java.util.concurrent.CompletableFuture;

public class CompletableReceipt<F> extends CompletableFuture<F> {
    private volatile ImmutableReceipt receipt;

    public CompletableReceipt(ImmutableReceipt receipt) {
        this.receipt = receipt;
    }

    public ImmutableReceipt getReceipt() {
        return receipt;
    }

    public ImmutableProgress getProgressCorrected() {
        if (isDone() && !isCompletedExceptionally() && !isCancelled())
            return receipt.progress.withProgressPercent(1.0f);

        return receipt.progress;
    }

    public void setReceipt(ImmutableReceipt receipt) {
        this.receipt = receipt;
    }
}
