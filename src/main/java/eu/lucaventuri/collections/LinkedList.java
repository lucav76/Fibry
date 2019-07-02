package eu.lucaventuri.collections;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LinkedList that uses Nodes instead of adding values as the Java lists do.
 * As a result, we can add and remove a specific node in O(1), which is our goal.
 */
public class LinkedList<T> implements Iterable<T> {
    private final AtomicReference<Node<T>> head = new AtomicReference<>();
    private final AtomicReference<Node<T>> tail = new AtomicReference<>();
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
            private Node<T> cur = head.get();

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
                    private Node<T> cur = head.get();

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
            if (node.value != null && node.value.equals(value))
                return node;
        }

        return null;
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

        Node<T> prevTail = tail.getAndSet(n);
        n.prev = prevTail;

        if (prevTail != null) {
            prevTail.next = n;
        } else
            head.compareAndSet(null, n);

        verify();

        return n;
    }

    // FIXME: is it 100% thread safe?
    public void remove(Node<T> node) {
        verify();

        head.compareAndExchange(node, node.next);
        tail.compareAndExchange(node, node.prev);

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
        boolean updated;
        Node<T> prevHead;

        do {
            prevHead = head.get();

            while (prevHead == null) {
                Node<T> prevTail = tail.get();

                if (prevTail == null)
                    return null;  // Fine: list is empty

                // Not fine: let's find the current head...
                Node<T> newHead = recoverHead(prevTail);

                head.compareAndSet(null, newHead);
                prevHead = head.get();
            }

            // Head available
            Node<T> newHead = prevHead.next;

            updated = head.compareAndSet(prevHead, newHead);

            if (updated) {
                if (newHead != null)
                    newHead.prev = null;
                else {
                    if (!tail.compareAndSet(prevHead, null)) {
                        // Not cool: somebody appended to the tail. Let's fix the head.

                        head.compareAndSet(null, recoverHead(tail.get()));
                    }
                }

            }
        } while (!updated);

        verify();

        return prevHead;
    }

    private Node<T> recoverHead(Node<T> prevTail) {
        Node<T> newHead = prevTail;

        while (prevTail != null) {
            newHead = prevTail;
            prevTail = prevTail.prev;
        }
        return newHead;
    }

    public T removeHead() {
        Node<T> node = removeHeadNode();

        return node == null ? null : node.value;
    }

    public T peekHead() {
        Node<T> node = head.get();

        return node == null ? null : node.value;
    }

    public T peekTail() {
        Node<T> node = tail.get();

        return node == null ? null : node.value;
    }

    List<T> asListFromHead() {
        List<T> list = new ArrayList<>();
        Node<T> n = head.get();

        while (n != null) {
            list.add(n.value);
            n = n.next;
        }

        return list;
    }

    List<T> asListFromTail() {
        List<T> list = new ArrayList<>();
        Node<T> n = tail.get();

        while (n != null) {
            list.add(n.value);
            n = n.prev;
        }

        return list;
    }
}
