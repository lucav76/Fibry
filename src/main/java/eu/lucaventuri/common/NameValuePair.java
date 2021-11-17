package eu.lucaventuri.common;

import java.util.Objects;

public class NameValuePair<K, V> {
    public final K key;
    public final V value;

    public NameValuePair(K key, V value) {
        this.key = key;
        this.value = value;

        assert key != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NameValuePair<?, ?> that = (NameValuePair<?, ?>) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
