package eu.lucaventuri.collections;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * NodeLinkedList that uses Nodes instead of adding values as the Java lists do.
 * As a result, we can add and remove a specific node in O(1), which is our goal.
 * This class is not thread safe, and it is intended to be used from a single thread.
 * In particular it is used as part of the "receive" functionality, so it is called by the actor, which, by definition, runs on a single thread/fiber.
 * Thread safety is achieved using a blocking queue before that.
 */
public class NodeLinkedList<T> implements Iterable<T> {
    private Node<T> head;
    private Node<T> tail;


    @Override
    public Iterator<T> iterator() {
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
        for (Node<T> node : asNodesIterable()) {
            if (node.value != null && node.value.equals(value)) {
                remove(node);

                return node;
            }
        }

        return null;
    }

    public boolean isEmpty() {
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
