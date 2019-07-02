package eu.lucaventuri.collections;

import java.util.ArrayList;
import java.util.List;
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

        if (obj==null)
            return false;

        LinkedList.Node<Object> node = list.addToTail(obj);

        mapByClass.computeIfAbsent(obj.getClass(), k -> new LinkedList<>()).addToTail(node);

        return true;
    }

    public <T> T removeHead() {
        LinkedList.Node<Object> n = list.removeHeadNode();

        if (n!=null) {
            LinkedList<LinkedList.Node<Object>> classList = mapByClass.get(n.value.getClass());

            if (classList!=null)
                classList.removeFirstByValue(n);
        }

        return (T) n.value;
    }

    public <T>  T scanAndChoose(Class<T> cls, Predicate<T> filter) {
        LinkedList<LinkedList.Node<Object>> listByClass = mapByClass.get(cls);

        if (listByClass==null)
            return null;

        for(Object o: listByClass) {
            if (filter.test((T)o))
                return (T) o;
        }

        return null;
    }
}
