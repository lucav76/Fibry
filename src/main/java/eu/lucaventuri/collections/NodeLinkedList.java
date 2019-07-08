package eu.lucaventuri.collections;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NodeLinkedList that uses Nodes instead of adding values as the Java lists do.
 * As a result, we can add and remove a specific node in O(1), which is our goal.
 */
public class NodeLinkedList<T> implements Iterable<T> {
    private Node<T> head;
    private Node<T> tail;
    private AtomicReference<Thread> threadRemove = new AtomicReference<>();

    private void verifyRemoveThread() {
        Thread curThread = Thread.currentThread();
        threadRemove.compareAndSet(null, curThread);


        if (curThread != threadRemove.get())
            throw new ConcurrentModificationException("Remove operations and iterators must be called from the same thread");
    }


    @Override
    public Iterator<T> iterator() {
        verifyRemoveThread();

        return new Iterator<T>() {
            private Node<T> cur = head;

            @Override
            public boolean hasNext() {
                return cur != null;
            }

            @Override
            public T next() {
                T value = cur.value;

                cur = cur.next;
                return value;
            }
        };
    }

    public Iterable<Node<T>> asNodesIterable() {
        verifyRemoveThread();

        return new Iterable<Node<T>>() {
            @Override
            public Iterator<Node<T>> iterator() {
                return new Iterator<Node<T>>() {
                    private Node<T> cur = head;

                    @Override
                    public boolean hasNext() {
                        return cur != null;
                    }

                    @Override
                    public Node<T> next() {
                        Node<T> value = cur;

                        cur = cur.next;
                        return value;
                    }
                };
            }
        };
    }

    public Node<T> removeFirstByValue(T value) {
        verifyRemoveThread();

        for (Node<T> node : asNodesIterable()) {
            if (node.value != null && node.value.equals(value)) {
                remove(node);

                return node;
            }
        }

        return null;
    }

    public boolean ieEmpty() {
        return head == null;
    }

    public static class Node<T> {
        public final T value;
        private Node<T> next;
        private Node<T> prev;

        Node(T value) {
            this.value = value;
        }
    }


    private void verify() {
        //assert (tail.get()==null && head.get() == null) || (tail.get() != null && head.get() != null);
    }

    // FIXME: is it 100% thread safe?
    public Node<T> addToTail(T value) {
        verify();

        Node<T> n = new Node<>(value);
        Node<T> prevTail = tail;
        tail = n;
        n.prev = prevTail;

        if (prevTail != null) {
            prevTail.next = n;
        } else
            head = n;

        verify();

        return n;
    }

    // FIXME: is it 100% thread safe?
    public void remove(Node<T> node) {
        verify();

        if (head == node)
            head = node.next;
        if (tail == node)
            tail = node.prev;

        // TODO: it would be nice to assert that it is part of this list...
        if (node.prev != null)
            node.prev.next = node.next;

        if (node.next != null)
            node.next.prev = node.prev;

        node.prev = node.next = null;

        verify();
    }

    // FIXME: is it 100% thread safe? What happens if a thread insert while another one is removing?
    public Node<T> removeHeadNode() {
        verify();

        // TODO: it would be nice to assert that it is part of this list...
        Node<T> prevHead = head;

        if (prevHead == null)
            return null;

        // Head available
        Node<T> newHead = prevHead.next;

        head = newHead;

        if (newHead != null)
            newHead.prev = null;
        else
            tail = null;

        verify();

        return prevHead;
    }

    public T removeHead() {
        Node<T> node = removeHeadNode();

        return node == null ? null : node.value;
    }

    public T peekHead() {
        Node<T> node = head;

        return node == null ? null : node.value;
    }

    public T peekTail() {
        Node<T> node = tail;

        return node == null ? null : node.value;
    }

    List<T> asListFromHead() {
        List<T> list = new ArrayList<>();
        Node<T> n = head;

        while (n != null) {
            list.add(n.value);
            n = n.next;
        }

        return list;
    }

    List<T> asListFromTail() {
        List<T> list = new ArrayList<>();
        Node<T> n = tail;

        while (n != null) {
            list.add(n.value);
            n = n.prev;
        }

        return list;
    }
}
