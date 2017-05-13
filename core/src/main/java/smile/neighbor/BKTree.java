/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.neighbor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import smile.math.distance.Metric;

/**
 * A BK-tree is a metric tree specifically adapted to discrete metric spaces.
 * For simplicity, let us consider integer discrete metric d(x,y). Then, BK-tree
 * is defined in the following way. An arbitrary element a is selected as root
 * root. Root may have zero or more subtrees. The k-th subtree is
 * recursively built of all elements b such that d(a,b) = k. BK-trees can be
 * used for approximate string matching in a dictionary.
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 * You may change this behavior with <code>setIdenticalExcluded</code>. Note that
 * you may observe weird behavior with String objects. JVM will pool the string literal
 * objects. So the below variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data that you
 * type explicitly in the code, this will cause problems. Fortunately, the data would be
 * read from secondary storage in production.
 * </p>
 *
 * <h2>References</h2>
 * <ol>
 * <li> W. Burkhard and R. Keller. Some approaches to best-match file searching. CACM, 1973. </li>
 * </ol>
 *
 * @param <E> the type of data objects in the tree.
 *
 * @author Haifeng Li
 */
public class BKTree<E> implements RNNSearch<E, E> {
    
    /**
     * The root in the BK-tree.
     */
    class Node {
        /**
         * The datum object.
         */
        E object;
        /**
         * The index of datum in the dataset.
         */
        int index;
        /**
         * The children nodes. Note that the i-<i>th</i> root's distance to
         * the parent is i.
         */
        ArrayList<Node> children;

        /**
         * Constructor.
         * @param object the datum object.
         * @param index the index of datum in the dataset.
         */
        Node(int index, E object) {
            this.index = index;
            this.object = object;
        }

        /**
         * Add a datum into the subtree.
         * @param datum the datum object.
         */
        private void add(E datum) {
            int d = (int) distance.d(object, datum);
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
                Node node = new Node(count++, datum);
                children.set(d, node);
            } else {
                child.add(datum);
            }
        }
    }

    /**
     * The root root of BK-tree.
     */
    private Node root;
    /**
     * The distance metric. Note that the metric must be a discrete distance,
     * e.g. edit distance, Hamming distance, Lee distance, Jaccard distance,
     * and taxonomic distance, etc.
     */
    private Metric<E> distance;
    /**
     * The number of nodes in the tree.
     */
    private int count = 0;
    /**
     * Whether to exclude query object self from the neighborhood.
     */
    private boolean identicalExcluded = true;

    /**
     * Constructor.
     * @param distance the metric used to build BK-tree. Note that the metric
     * must be a discrete distance, e.g. edit distance, Hamming distance, Lee
     * distance, Jaccard distance, and taxonomic distance, etc.
     */
    public BKTree(Metric<E> distance) {
        this.distance = distance;
    }

    /**
     * Add a dataset into BK-tree.
     * @param data the dataset to insert into the BK-tree.

     */
    public void add(E[] data) {
        for (E datum : data) {
            add(datum);
        }
    }

    /**
     * Add a dataset into BK-tree.
     * @param data the dataset to insert into the BK-tree.
     */
    public void add(Collection<E> data) {
        for (E datum : data) {
            add(datum);
        }
    }

    @Override
    public String toString() {
        return String.format("BK-Tree (%s)", distance);
    }

    /**
     * Add a datum into the BK-tree.
     */
    public void add(E datum) {
        if (root == null) {
            root = new Node(count++, datum);
        } else {
            root.add(datum);
        }
    }

    /**
     * Set if exclude query object self from the neighborhood.
     */
    public BKTree<E> setIdenticalExcluded(boolean excluded) {
        identicalExcluded = excluded;
        return this;
    }

    /**
     * Get whether if query object self be excluded from the neighborhood.
     */
    public boolean isIdenticalExcluded() {
        return identicalExcluded;
    }

    /**
     * Do a range search in the given subtree.
     * @param node the root of subtree.
     * @param q the query object.
     * @param k the range of query.
     * @param neighbors the returned results of which d(x, target) &le; k.
     */
    private void search(Node node, E q, int k, List<Neighbor<E, E>> neighbors) {
        int d = (int) distance.d(node.object, q);

        if (d <= k) {
            if (node.object != q || !identicalExcluded) {
                neighbors.add(new Neighbor<>(node.object, node.object, node.index, d));
            }
        }

        if (node.children != null) {
            int start = Math.max(1, d-k);
            int end = Math.min(node.children.size(), d+k+1);
            for (int i = start; i < end; i++) {
                Node child = node.children.get(i);
                if (child != null) {
                    search(child, q, k, neighbors);
                }
            }
        }
    }

    @Override
    public void range(E q, double radius, List<Neighbor<E, E>> neighbors) {
        if (radius != (int) radius) {
            throw new IllegalArgumentException("The parameter radius has to be an integer: " + radius);
        }
        
        search(root, q, (int) radius, neighbors);
    }

    /**
     * Search the neighbors in the given radius of query object, i.e.
     * d(q, v) &le; radius.
     *
     * @param q 	the query object.
     * @param radius	the radius of search range from target.
     * @param neighbors	the list to store found neighbors in the given range on output.
     */
    public void range(E q, int radius, List<Neighbor<E, E>> neighbors) {
        search(root, q, radius, neighbors);
    }
}
