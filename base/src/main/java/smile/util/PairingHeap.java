/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.util.*;

 /**
  * A pairing heap is a type of heap data structure with relatively simple
  * implementation and excellent practical amortized performance. Pairing
  * heaps are heap-ordered multiway tree structures, and can be considered
  * simplified Fibonacci heaps. They are considered a robust choice for
  * implementing such algorithms as Prim's MST algorithm.
  *
  * @param <E> the type of the heap elements.
  * @author Karl Li
  */
public class PairingHeap<E extends Comparable<E>> implements Queue<E> {
    /**
     * A multiway tree node in the pairing heap.
     */
    public class Node {
        E value;
        Node child = null;
        Node sibling = null;
        Node parent = null;

        /**
         * Constructor.
         * @param value the element value.
         */
        public Node(E value) {
            this.value = value;
        }

        /**
         * Decreases the value of an element.
         * @param newValue the new value.
         */
        public void decrease(E newValue) {
            if (newValue.compareTo(value) >= 0) {
                throw new IllegalArgumentException("New value is not more extreme");
            }

            value = newValue;
            cutMeld(this);
        }

        /**
         * Add a child node.
         * @param node the child node.
         */
        void addChild(Node node) {
            node.parent = this;
            node.sibling = child;
            child = node;
        }
    }

    /** The root of multiway tree. */
    private Node root;
    /** The number of elements. */
    private int size;

    /** Constructor. */
    public PairingHeap() {
        root = null;
        size = 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Adds a new element to the pairing heap.
     * @param value a new element.
     * @return a Node corresponding to the newly added element.
     */
    public Node addNode(E value) {
        ++size;
        Node node = new Node(value);
        root = root == null ? node : meld(root, node);
        return node;
    }

    /**
     * Rebuilds the pairing heap. Assumes that all elements inside the pairing
     * heap are out of order due to side-channel updates.
     */
    public void rebuild() {
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
        root = root.child == null ? null : twoPassMeld(root.child);
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

    /** Meld two nodes. */
    private Node meld(Node a, Node b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.value.compareTo(b.value) < 0) {
            a.addChild(b);
            return a;
        } else {
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
        start = b.sibling;

        a.sibling = null;
        b.sibling = null;

        return meld(meld(a, b), twoPassMeld(start));
    }

    /** Cut and meld a node, used by Node.decrease(). */
    private void cutMeld(Node node) {
        if (node.parent != null) {
            if (node.value.compareTo(node.parent.value) < 0) {
                Node left = node.parent.child;
                if (left == node) {
                    node.parent.child = node.sibling;
                } else {
                    while (left.sibling != node) {
                        left = left.sibling;
                    }
                    left.sibling = node.sibling;
                }

                node.parent = null;
                node.sibling = null;
                root = meld(root, node);
            }
        }
    }
}
