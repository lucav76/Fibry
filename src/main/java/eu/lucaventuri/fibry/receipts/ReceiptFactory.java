package eu.lucaventuri.fibry.receipts;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface ReceiptFactory {
    <T, F> Receipt<T, F> newReceipt(T message);

    <T, F> Receipt<T, F> get(String id);

    <T, F> void save(Receipt<T, F> receipt);

    static ReceiptFactory fromMap() {
        return new ReceiptFactory() {
            private final ConcurrentHashMap<String, Receipt> map = new ConcurrentHashMap<>();
            private final AtomicInteger progressiveId = new AtomicInteger();

            @Override
            public <T, F> Receipt<T, F> newReceipt(T message) {
                var receipt = new Receipt<T, F>("" + progressiveId, message);

                save(receipt);

                return receipt;
            }

            @Override
            public <T, F> Receipt<T, F> get(String id) {
                return (Receipt<T, F>) map.get(id);
            }

            @Override
            public <T, F> void save(Receipt<T, F> receipt) {
                map.put(receipt.id, receipt);
            }
        };
    }
}
