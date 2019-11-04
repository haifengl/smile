/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.neighbor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import smile.math.distance.Metric;

/**
 * A BK-tree is a metric tree specifically adapted to discrete metric spaces.
 * For simplicity, let us consider integer discrete metric d(x,y). Then, BK-tree
 * is defined in the following way. An arbitrary element a is selected as root.
 * Root may have zero or more subtrees. The k-th subtree is
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
public class BKTree<E> implements RNNSearch<E, E>, Serializable {
    private static final long serialVersionUID = 2L;
    
    /**
     * The root in the BK-tree.
     */
    class Node implements Serializable {
        /**
         * The datum object.
         */
        E object;
        /**
         * The index of datum in the dataset.
         */
        int index;
        /**
         * The children nodes. The i-th child's distance to
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
     * The root of BK-tree.
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
     * Do a range search in the given subtree.
     * @param node the root of subtree.
     * @param q the query object.
     * @param k the range of query.
     * @param neighbors the returned results of which d(x, target) &le; k.
     */
    private void search(Node node, E q, int k, List<Neighbor<E, E>> neighbors) {
        int d = (int) distance.d(node.object, q);

        if (d <= k && node.object != q) {
            neighbors.add(new Neighbor<>(node.object, node.object, node.index, d));
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
        if (radius <= 0 || radius != (int) radius) {
            throw new IllegalArgumentException("The parameter radius has to be an integer: " + radius);
        }
        
        search(root, q, (int) radius, neighbors);
    }

    /**
     * Search the neighbors in the given radius of query object, i.e.
     * d(q, v) &le; radius.
     *
     * @param q the query object.
     * @param radius the radius of search range from target.
     * @param neighbors the list to store found neighbors in the given range on output.
     */
    public void range(E q, int radius, List<Neighbor<E, E>> neighbors) {
        search(root, q, radius, neighbors);
    }
}
