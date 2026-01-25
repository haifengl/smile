/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import smile.math.distance.Metric;

/**
 * A BK-tree is a metric tree specifically adapted to discrete metric spaces.
 * For simplicity, let us consider integer discrete metric d(x,y). Then,
 * BK-tree is defined in the following way. An arbitrary element <code>a</code>
 * is selected as root. Root may have zero or more subtrees. The k-th subtree
 * is recursively built of all elements <code>a</code> such that
 * <code>d(a,b) = k</code>. BK-trees can be used for approximate string
 * matching in a dictionary.
 * <p>
 * By default, the query object (reference equality) is excluded from the
 * neighborhood. You may change this behavior with
 * <code>setIdenticalExcluded</code>. Note that you may observe weird behavior
 * with String objects. JVM will pool the string literal objects. So the below
 * variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data
 * that you type explicitly in the code, this will cause problems. Fortunately,
 * the data would be generally read from secondary storage in production.
 *
 * <h2>References</h2>
 * <ol>
 * <li> W. Burkhard and R. Keller. Some approaches to best-match file searching. CACM, 1973. </li>
 * </ol>
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public class BKTree<K, V> implements RNNSearch<K, V>, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
    
    /**
     * The root in the BK-tree.
     */
    class Node implements Serializable {
        /**
         * The object key.
         */
        final K key;
        /**
         * The data object.
         */
        final V value;
        /**
         * The index of datum in the dataset.
         */
        final int index;
        /**
         * The children nodes. The i-th child's distance to
         * the parent is i.
         */
        ArrayList<Node> children;

        /**
         * Constructor.
         * @param key the data key.
         * @param value the data object.
         * @param index the index of datum in the dataset.
         */
        Node(int index, K key, V value) {
            this.index = index;
            this.key = key;
            this.value = value;
        }

        /**
         * Add a datum into the subtree.
         * @param key the data key.
         * @param value the data object.
         */
        private void add(K key, V value) {
            int d = (int) distance.d(this.key, key);
            if (d == 0) {
                return;
            }

            if (children == null) {
                children = new ArrayList<>();
            }

            while (children.size() <= d) {
                children.add(null);
            }

            Node child = children.get(d);
            if (child == null) {
                Node node = new Node(count++, key, value);
                children.set(d, node);
            } else {
                child.add(key, value);
            }
        }

        /**
         * Range search in the subtree.
         * @param q the query object.
         * @param k the range of query.
         * @param neighbors the returned results of which {@code d(x, target) <= k}.
         */
        private void search(K q, int k, List<Neighbor<K, V>> neighbors) {
            int d = (int) distance.d(key, q);

            if (d <= k && key != q) {
                neighbors.add(new Neighbor<>(key, value, index, d));
            }

            if (children != null) {
                int start = Math.max(1, d-k);
                int end = Math.min(children.size(), d+k+1);
                for (int i = start; i < end; i++) {
                    Node child = children.get(i);
                    if (child != null) {
                        child.search(q, k, neighbors);
                    }
                }
            }
        }
    }

    /**
     * The root of BK-tree.
     */
    private Node root;
    /**
     * The distance metric. Note that the metric must be a discrete distance,
     * e.g. edit distance, Hamming distance, Lee distance, Jaccard distance,
     * and taxonomic distance, etc.
     */
    private final Metric<K> distance;
    /**
     * The number of nodes in the tree.
     */
    private int count = 0;

    /**
     * Constructor.
     * @param distance the metric used to build BK-tree. Note that the metric
     * must be a discrete distance, e.g. edit distance, Hamming distance, Lee
     * distance, Jaccard distance, and taxonomic distance, etc.
     */
    public BKTree(Metric<K> distance) {
        this.distance = distance;
    }

    /**
     * Return a BK-tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance the metric used to build BK-tree. Note that the metric
     * must be a discrete distance, e.g. edit distance, Hamming distance, Lee
     * distance, Jaccard distance, and taxonomic distance, etc.
     * @param <T> the type of keys and values.
     * @return BK-tree.
     */
    public static <T> BKTree<T, T> of(T[] data, Metric<T> distance) {
        BKTree<T, T> tree = new BKTree<>(distance);
        for (T key : data) {
            tree.add(key, key);
        }
        return tree;
    }

    /**
     * Return a BK-tree of the data.
     * @param data the data objects, which are also used as key.
     * @param distance the metric used to build BK-tree. Note that the metric
     * must be a discrete distance, e.g. edit distance, Hamming distance, Lee
     * distance, Jaccard distance, and taxonomic distance, etc.
     * @param <T> the type of keys and values.
     * @return BK-tree.
     */
    public static <T> BKTree<T, T> of(List<T> data, Metric<T> distance) {
        BKTree<T, T> tree = new BKTree<>(distance);
        for (T key : data) {
            tree.add(key, key);
        }
        return tree;
    }

    @Override
    public String toString() {
        return String.format("BK-Tree (%s)", distance);
    }

    /**
     * Adds a datum into the BK-tree.
     * @param key the data key.
     * @param value the data object.
     */
    public void add(K key, V value) {
        if (root == null) {
            root = new Node(count++, key, value);
        } else {
            root.add(key, value);
        }
    }

    /**
     * Adds a dataset into BK-tree.
     * @param data the dataset to insert into the BK-tree.
     */
    public void add(Map<K, V> data) {
        for (Map.Entry<K, V> entry : data.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void search(K q, double radius, List<Neighbor<K, V>> neighbors) {
        if (radius <= 0 || radius != (int) radius) {
            throw new IllegalArgumentException("The parameter radius has to be an integer: " + radius);
        }
        
        root.search(q, (int) radius, neighbors);
    }

    /**
     * Search the neighbors in the given radius of query object, i.e.
     * {@code d(q, v) <= radius}.
     *
     * @param q the query object.
     * @param radius the radius of search range from target.
     * @param neighbors the list to store found neighbors in the given range on output.
     */
    public void search(K q, int radius, List<Neighbor<K, V>> neighbors) {
        root.search(q, radius, neighbors);
    }
}
