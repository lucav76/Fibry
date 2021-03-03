package eu.lucaventuri.collections;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thread safe map that cannot grow past a certain size. Entries are evicted in a LRU order.
 * Access is synchronized  on all the operations
 */
public class ConstrainedMapLRU<K, V> {
    private final Map<K, V> map;
    private final int maxSize;

    /**
     * Constructor
     *
     * @param maxSize Maximum size allowed for the map
     */
    public ConstrainedMapLRU(int maxSize, Consumer<Map.Entry<K, V>> onRemoval) {
        map = new LinkedHashMap<>(maxSize, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean toBeRemoved = size() > maxSize;

                if (onRemoval != null && toBeRemoved)
                    onRemoval.accept(eldest);

                return toBeRemoved;
            }
        };
        this.maxSize = maxSize;
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized V put(K key, V value) {
        assert map.size() <= maxSize;

        V oldValue = map.put(key, value);

        assert map.size() <= maxSize;

        return oldValue;
    }

    public synchronized V remove(K key) {
        return map.remove(key);
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized void clear() {
        map.clear();
    }
}
