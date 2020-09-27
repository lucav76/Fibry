package eu.lucaventuri.fibry.receipts;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface ReceiptFactory {
    default <T, F> ImmutableReceipt<T> newReceipt(T message) {
        return newReceipt(message, new ImmutableProgress());
    }

    <T, F> ImmutableReceipt<T> newReceipt(T message, ImmutableProgress progress);

    default <T, F> CompletableReceipt<T, F> newCompletableReceipt(T message) {
        return new CompletableReceipt<>(newReceipt(message, new ImmutableProgress()));
    }

    default <T, F> CompletableReceipt<T, F> newCompletableReceipt(String type, T message, ImmutableProgress progress) {
        return new CompletableReceipt<>(newReceipt(message, progress));
    }

    <T> ImmutableReceipt<T> get(String id);

    <T> void save(ImmutableReceipt<T> receipt);

    default <T> ImmutableReceipt<T> refresh(ImmutableReceipt<T> receipt) {
        return get(receipt.getReceiptId());
    }

    default <T, F> CompletableReceipt<T, F> refresh(CompletableReceipt<T, F> receipt) {
        receipt.setReceipt(refresh(receipt.getReceipt()));

        return receipt;
    }

    static ReceiptFactory fromMap() {
        return new ReceiptFactory() {
            private final ConcurrentHashMap<String, ImmutableReceipt> map = new ConcurrentHashMap<>();
            private final AtomicInteger progressiveId = new AtomicInteger();

            @Override
            public <T, F> ImmutableReceipt<T> newReceipt(T message, ImmutableProgress progress) {
                var receipt = new ImmutableReceipt<T>("" + progressiveId.incrementAndGet(), message, progress);

                save(receipt);

                return receipt;
            }

            @Override
            public <T> ImmutableReceipt<T> get(String id) {
                return (ImmutableReceipt<T>) map.get(id);
            }

            @Override
            public <T> void save(ImmutableReceipt<T> receipt) {
                map.put(receipt.getReceiptId(), receipt);
            }
        };
    }
}
