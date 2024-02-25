package eu.lucaventuri.collections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static eu.lucaventuri.collections.NodeLinkedList.Node;

/**
 * This object contains a map grouped by class and a list; so it can be accessed in both ways.
 * This object in intended to be used to filter messages based on their class and on some other attribute
 */
public class ClassifiedMap {
    private final NodeLinkedList<Object> list = new NodeLinkedList<>();
    private final ConcurrentHashMap<Class, NodeLinkedList<Node<Object>>> mapByClass = new ConcurrentHashMap<>();

    public boolean addToTail(Object obj) {
        assert obj != null;
        verify();

        try {
            if (obj == null)
                return false;

            Node<Object> node = list.addToTail(obj);

            mapByClass.computeIfAbsent(obj.getClass(), k -> new NodeLinkedList<>()).addToTail(node);

            return true;
        } finally {
            verify();
        }
    }

    public boolean addToTailConverted(Object obj, Class convertedClass) {
        assert obj != null;
        verify();

        try {
            if (obj == null)
                return false;

            Node<Object> node = list.addToTail(obj);

            mapByClass.computeIfAbsent(convertedClass, k -> new NodeLinkedList<>()).addToTail(node);

            return true;
        } finally {
            verify();
        }
    }

    public <T> T peekHead() {
        verify();

        return (T) list.peekHead();
    }

    public <T> T removeHead() {
        verify();

        try {
            Node<Object> n = list.removeHeadNode();

            if (n == null)
                return null;

            NodeLinkedList<Node<Object>> classList = mapByClass.get(n.value.getClass());

            if (classList != null) {
                classList.removeFirstByValue(n);
                if (classList.isEmpty())
                    mapByClass.remove(n.value.getClass());
            }

            return (T) n.value;
        } finally {
            verify();
        }
    }

    public <T> T removeHeadConverted(Function<T, ?> converter) {
        verify();

        try {
            Node<Object> n = list.removeHeadNode();

            if (n == null)
                return null;

            var convertedClass = converter.apply((T) n.value).getClass();
            NodeLinkedList<Node<Object>> classList = mapByClass.get(convertedClass);

            if (classList != null) {
                classList.removeFirstByValue(n);
                if (classList.isEmpty())
                    mapByClass.remove(convertedClass);
            }

            return (T) n.value;
        } finally {
            verify();
        }
    }

    private void verify() {
        assert (mapByClass.isEmpty() && list.isEmpty()) || (!mapByClass.isEmpty() && !list.isEmpty());
    }

    void deepVerify() {
        assert (mapByClass.size() == list.asListFromHead().size());
    }

    public <T> T scanAndChoose(Class<T> cls, Predicate<T> filter) {
        verify();

        try {
            T value = scanList(filter, mapByClass.get(cls));

            if (value != null)
                return value;

            for (Class clz : mapByClass.keySet()) {
                if (cls != clz && cls.isAssignableFrom(clz)) {
                    value = scanList(filter, mapByClass.get(clz));

                    if (value != null)
                        return value;
                }
            }

            return null;
        } finally {
            verify();
        }
    }

    private <T> T scanList(Predicate<T> filter, NodeLinkedList<Node<Object>> listByClass) {
        try {
            if (listByClass == null)
                return null;

            /// use AsNodeIterator
            for (Node<Node<Object>> n : listByClass.asNodesIterable()) {
                if (filter.test((T) n.value.value)) {
                    listByClass.remove(n);
                    //list.remove(n);
                    // TODO: Can it become faster?
                    list.removeFirstByValue(n.value);
                    return (T) n.value.value;
                }
            }

            return null;
        } finally {
            verify();
        }
    }

    public boolean isEmpty() {
        //verify();

        return list.isEmpty();
    }

    public <K, E extends K, T> K scanAndChooseAndConvert(Class<E> cls, Predicate<E> filter, Function<T, K> converter) {
        verify();

        try {
            K value = scanAndCovertList(cls, filter, converter, mapByClass.get(cls));

            if (value != null)
                return value;

            for (Class clz : mapByClass.keySet()) {
                if (cls.isAssignableFrom(clz)) {
                    value = scanAndCovertList(clz, filter, converter, mapByClass.get(clz));

                    if (value != null)
                        return value;
                }
            }

            return null;
        } finally {
            verify();
        }
    }

    private <K, E extends K, T> K scanAndCovertList(Class cls, Predicate<E> filter, Function<T, K> converter, NodeLinkedList<Node<Object>> listByClass) {
        if (listByClass == null)
            return null;

        try {
            for (Node<Node<Object>> n : listByClass.asNodesIterable()) {
                K valueConverted = converter.apply((T) n.value.value);

                if (valueConverted != null && filter.test((E) valueConverted)) {
                    listByClass.remove(n);
                    //list.remove(n);
                    list.removeFirstByValue(n.value);
                    return valueConverted;
                }
            }

            return null;
        } finally {
            verify();
        }
    }
}
