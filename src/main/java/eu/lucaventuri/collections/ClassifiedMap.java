package eu.lucaventuri.collections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** This object contains a map grouped by class and a list; so it can be accessed in both ways.
 * This object in intended to be used to filter messages based on their class and on some other attribute
 * */
public class ClassifiedMap {
    private final LinkedList<Object> list = new LinkedList<>();
    private final ConcurrentHashMap<Class, LinkedList<LinkedList.Node<Object>>> mapByClass= new ConcurrentHashMap<>();

    public boolean addToTail(Object obj) {
        assert obj!=null;
        verify();

        if (obj==null)
            return false;

        LinkedList.Node<Object> node = list.addToTail(obj);

        mapByClass.computeIfAbsent(obj.getClass(), k -> new LinkedList<>()).addToTail(node);

        verify();

        return true;
    }

    public <T> T removeHead() {
        verify();

        LinkedList.Node<Object> n = list.removeHeadNode();

        if (n!=null) {
            LinkedList<LinkedList.Node<Object>> classList = mapByClass.get(n.value.getClass());

            if (classList!=null)
                classList.removeFirstByValue(n);
        }

        verify();

        return (T) n.value;
    }

    private void verify() {
        assert (mapByClass.isEmpty() && list.ieEmpty()) || (!mapByClass.isEmpty() && !list.ieEmpty());
    }

    void deepVerify() {
        assert (mapByClass.size() == list.asListFromHead().size());
    }

    public <T>  T scanAndChoose(Class<T> cls, Predicate<T> filter) {
        verify();
        LinkedList<LinkedList.Node<Object>> listByClass = mapByClass.get(cls);

        if (listByClass==null)
            return null;

        for(LinkedList.Node<Object> n: listByClass) {
            if (filter.test((T)n.value)) {
                listByClass.removeFirstByValue(n);
                list.remove(n);
                return (T) n.value;
            }
        }

        for(Class clz: mapByClass.keySet()) {
            listByClass = mapByClass.get(clz);

            if (cls.isAssignableFrom(clz)) {
                for(LinkedList.Node<Object> n: listByClass) {
                    if (filter.test((T)n.value)) {
                        listByClass.removeFirstByValue(n);
                        list.remove(n);
                        return (T) n.value;
                    }
                }
            }
        }

        return null;
    }

    public boolean isEmpty() {
        verify();

        return list.ieEmpty();
    }
}
