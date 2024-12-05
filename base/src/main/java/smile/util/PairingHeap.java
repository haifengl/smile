/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

import java.util.*;

 /**
  * A pairing heap is a type of heap data structure with relatively simple
  * implementation and excellent practical amortized performance. Pairing
  * heaps are heap-ordered multiway tree structures, and can be considered
  * simplified Fibonacci heaps. They are considered a robust choice for
  * implementing such algorithms as Prim's MST algorithm.
  * 
  * @author Karl Li
  */
public class PairingHeap<E extends Comparable<E>> implements Queue<E> {
    private class Node {
        E value;
        Node child = null;
        Node sibling = null;
        Node parent = null;

        public Node(E value) {
            this.value = value;
        }

        public void addChild(Node node) {
            node.parent = this;
            node.sibling = child;
            child = node;
        }
    }

    private Node root = null;
    private int size = 0;

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean add(E value) {
        addNode(value);
        return true;
    }

    @Override
    public boolean offer(E value) {
        return add(value);
    }

    @Override
    public E peek() {
        return root == null ? null : root.value;
    }

    @Override
    public E element() {
        if (isEmpty()) throw new NoSuchElementException();
        return root.value;
    }

    @Override
    public E poll() {
        return isEmpty() ? null : remove();
    }

    @Override
    public E remove() {
        if (isEmpty()) throw new NoSuchElementException();
        E value = root.value;
        if (root.child == null) {
            root = null;  // No children, so the heap is now empty
        } else {
            root = twoPassMeld(root.child);  // Perform two-pass melding on children
        }
        --size;
        return value;
    }

    @Override
    public void clear() {
        root = null;
        size = 0;
    }

    @Override
    public boolean addAll(java.util.Collection<? extends E> c) {
        for (var e : c) {
            add(e);
        }
        return true;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * Rebuilds the pairing heap. Assumes that all elements inside the pairing
     * heap are out of order.
     */
    public void updatePriorities() {
        if (root == null) return;

        Node node = root;
        Deque<Node> queue = new LinkedList<>();
        queue.offer(node.child);
        node.child = null;

        while (!queue.isEmpty()) {
            node = queue.poll();

            if (node.sibling != null) {
                queue.offer(node.sibling);
            }

            if (node.child != null) {
                queue.offer(node.child);
            }

            node.child = null;
            node.sibling = null;
            node.parent = null;
            root = meld(root, node);
        }
    }

    /**
     * Updates the priority of an element already in the pairing
     * heap by replacing the element refered to by the Node with
     * newValue, which must be more extreme than the old value.
     * @param node an existing node in the heap.
     * @param newValue the new value with more extreme priority.
     */
    public void update(Node node, E newValue) {
        if (newValue.compareTo(node.value) >= 0) {
            throw new IllegalArgumentException("New value is not more extreme");
        }

        node.value = newValue;
        if (node != root) {
            cutMeld(node);
        }
    }

    /**
     * Adds a new element to the pairing heap.
     * @param value a new element.
     * @return a Node corresponding to the newly added element.
     */
    private Node addNode(E value) {
        ++size;
        Node node = new Node(value);
        if (root == null) {
            root = node;
        } else {
            root = meld(root, node);
        }
        return node;
    }

    /** Meld two nodes. */
    private Node meld(Node a, Node b) {
        if (a == null) return b;
        if (b == null) return a;
        if (b.value.compareTo(a.value) < 0) {
            a.addChild(b);
            return a;
        }
        else {
            b.addChild(a);
            return b;
        }
    }

    /** Two-pass melding. */
    private Node twoPassMeld(Node start) {
        if (start == null || start.sibling == null) return start;
        start.parent = null;

        Node a = start;
        Node b = start.sibling;
        start = b == null ? null : b.sibling;

        a.sibling = null;
        b.sibling = null;

        return meld(meld(a, b), twoPassMeld(start));
    }
    /** Cut and meld a node. */
    void cutMeld(Node node) {
        if (node.parent != null) {
            if (node.parent.child == node) {
                node.parent.child = node.sibling;
            } else {
                Node sibling = node.parent.child;
                while (sibling.sibling != node) {
                    sibling = sibling.sibling;
                }
                sibling.sibling = node.sibling;
            }
            node.parent = null;
            node.sibling = null;
            root = meld(root, node);
        }
    }
}
