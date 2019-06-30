package eu.lucaventuri.common;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentHashSet {
    private ConcurrentHashSet() {}
    public static <K> Set<K> build() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }
}
