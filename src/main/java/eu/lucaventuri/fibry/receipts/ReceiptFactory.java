package eu.lucaventuri.fibry.receipts;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface ReceiptFactory {
    default ImmutableReceipt newReceipt() throws IOException {
        return newReceipt(new ImmutableProgress());
    }

    ImmutableReceipt newReceipt(ImmutableProgress progress) throws IOException;

    default CompletableReceipt newCompletableReceipt() throws IOException {
        return new CompletableReceipt(newReceipt(new ImmutableProgress()));
    }

    default CompletableReceipt newCompletableReceipt(String type, ImmutableProgress progress) throws IOException {
        return new CompletableReceipt(newReceipt(progress));
    }

    ImmutableReceipt get(String id) throws IOException;

    void save(ImmutableReceipt receipt) throws IOException;

    default ImmutableReceipt refresh(ImmutableReceipt receipt) throws IOException {
        return get(receipt.getReceiptId());
    }

    default <T, F> CompletableReceipt<F> refresh(CompletableReceipt<F> receipt) throws IOException {
        receipt.setReceipt(refresh(receipt.getReceipt()));

        return receipt;
    }

    static ReceiptFactory fromMap() {
        return new ReceiptFactory() {
            private final ConcurrentHashMap<String, ImmutableReceipt> map = new ConcurrentHashMap<>();
            private final AtomicInteger progressiveId = new AtomicInteger();

            @Override
            public ImmutableReceipt newReceipt(ImmutableProgress progress) {
                var receipt = new ImmutableReceipt("" + progressiveId.incrementAndGet(), progress);

                save(receipt);

                return receipt;
            }

            @Override
            public ImmutableReceipt get(String id) {
                return map.get(id);
            }

            @Override
            public void save(ImmutableReceipt receipt) {
                map.put(receipt.getReceiptId(), receipt);
            }
        };
    }
}
