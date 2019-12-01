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
import java.util.Arrays;
import java.util.List;

import smile.math.MathEx;
import smile.sort.HeapSelect;

/**
 * A KD-tree (short for k-dimensional tree) is a space-partitioning dataset
 * structure for organizing points in a k-dimensional space. KD-trees are
 * a useful dataset structure for nearest neighbor searches. The kd-tree is a
 * binary tree in which every node is a k-dimensional point. Every non-leaf
 * node generates a splitting hyperplane that divides the space into two
 * subspaces. Points left to the hyperplane represent the left sub-tree of
 * that node and the points right to the hyperplane by the right sub-tree.
 * The hyperplane direction is chosen in the following way: every node split
 * to sub-trees is associated with one of the k-dimensions, such that the
 * hyperplane is perpendicular to that dimension vector. So, for example, if
 * for a particular split the "x" axis is chosen, all points in the subtree
 * with a smaller "x" value than the node will appear in the left subtree and
 * all points with larger "x" value will be in the right sub tree.
 * <p>
 * KD-trees are not suitable for efficiently finding the nearest neighbor
 * in high dimensional spaces. As a general rule, if the dimensionality is D,
 * then number of points in the dataset, N, should be N &gt;&gt; 2<sup>D</sup>.
 * Otherwise, when kd-trees are used with high-dimensional dataset, most of the
 * points in the tree will be evaluated and the efficiency is no better than
 * exhaustive search, and approximate nearest-neighbor methods should be used
 * instead.
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 *
 * @param <E> the type of data objects in the tree.
 *
 * @author Haifeng Li
 */
public class KDTree <E> implements NearestNeighborSearch<double[], E>, KNNSearch<double[], E>, RNNSearch<double[], E>, Serializable {
    private static final long serialVersionUID = 2L;

    /**
     * The root in the KD-tree.
     */
    class Node implements Serializable {

        /**
         * Number of dataset stored in this node.
         */
        int count;
        /**
         * The smallest point index stored in this node.
         */
        int index;
        /**
         * The index of coordinate used to split this node.
         */
        int split;
        /**
         * The cutoff used to split the specific coordinate.
         */
        double cutoff;
        /**
         * The child node which values of split coordinate is less than the cutoff value.
         */
        Node lower;
        /**
         * The child node which values of split coordinate is greater than or equal to the cutoff value.
         */
        Node upper;

        /**
         * If the node is a leaf node.
         */
        boolean isLeaf() {
            return lower == null && upper == null;
        }
    }
    /**
     * The keys of data objects.
     */
    private double[][] keys;
    /**
     * The data objects.
     */
    private E[] data;
    /**
     * The root node of KD-Tree.
     */
    private Node root;
    /**
     * The index of objects in each nodes.
     */
    private int[] index;

    /**
     * Constructor.
     * @param key the keys of data objects.
     * @param data the data objects.
     */
    public KDTree(double[][] key, E[] data) {
        if (key.length != data.length) {
            throw new IllegalArgumentException("The array size of keys and data are different.");
        }

        this.keys = key;
        this.data = data;

        int n = key.length;
        index = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = i;
        }

        // Build the tree
        int d = keys[0].length;
        double[] lowerBound = new double[d];
        double[] upperBound = new double[d];
        root = buildNode(0, n, lowerBound, upperBound);
    }

    @Override
    public String toString() {
        return "KD-Tree";
    }

    /**
     * Builds a sub-tree.
     * @param begin the beginning index of samples for the subtree (inclusive).
     * @param end the ending index of samples for the subtree (exclusive).
     * @param lowerBound the work space of lower bound of each dimension of samples.
     * @param upperBound the work space of upper bound of each dimension of samples.
     */
    private Node buildNode(int begin, int end, double[] lowerBound, double[] upperBound) {
        int d = keys[0].length;

        // Allocate the node
        Node node = new Node();

        // Fill in basic info
        node.count = end - begin;
        node.index = begin;

        // Calculate the bounding box
        double[] key = keys[index[begin]];
        System.arraycopy(key, 0, lowerBound, 0, d);
        System.arraycopy(key, 0, upperBound, 0, d);

        for (int i = begin + 1; i < end; i++) {
            key = keys[index[i]];
            for (int j = 0; j < d; j++) {
                double c = key[j];
                if (lowerBound[j] > c) {
                    lowerBound[j] = c;
                }
                if (upperBound[j] < c) {
                    upperBound[j] = c;
                }
            }
        }

        // Calculate bounding box stats
        double maxRadius = -1;
        for (int i = 0; i < d; i++) {
            double radius = (upperBound[i] - lowerBound[i]) / 2;
            if (radius > maxRadius) {
                maxRadius = radius;
                node.split = i;
                node.cutoff = (upperBound[i] + lowerBound[i]) / 2;
            }
        }

        // If the max spread is 0, make this a leaf node
        if (maxRadius == 0) {
            node.lower = node.upper = null;
            return node;
        }

        // Partition the data around the midpoint in this dimension. The
        // partitioning is done in-place by iterating from left-to-right and
        // right-to-left in the same way as quicksort.
        int i1 = begin, i2 = end - 1, size = 0;
        while (i1 <= i2) {
            boolean i1Good = (keys[index[i1]][node.split] < node.cutoff);
            boolean i2Good = (keys[index[i2]][node.split] >= node.cutoff);

            if (!i1Good && !i2Good) {
                int temp = index[i1];
                index[i1] = index[i2];
                index[i2] = temp;
                i1Good = i2Good = true;
            }

            if (i1Good) {
                i1++;
                size++;
            }

            if (i2Good) {
                i2--;
            }
        }

        // Create the child nodes
        node.lower = buildNode(begin, begin + size, lowerBound, upperBound);
        node.upper = buildNode(begin + size, end, lowerBound, upperBound);

        return node;
    }

    /**
     * Returns the nearest neighbors of the given target starting from the give
     * tree node.
     *
     * @param q    the query key.
     * @param node the root of subtree.
     * @param neighbor the current nearest neighbor.
     */
    private void search(double[] q, Node node, NeighborBuilder<double[], E> neighbor) {
        if (node.isLeaf()) {
            // look at all the instances in this leaf
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    double distance = MathEx.distance(q, keys[i]);
                    if (distance < neighbor.distance) {
                        neighbor.index = i;
                        neighbor.distance = distance;
                    }
                }
            }
        } else {
            Node nearer, further;
            double diff = q[node.split] - node.cutoff;
            if (diff < 0) {
                nearer = node.lower;
                further = node.upper;
            } else {
                nearer = node.upper;
                further = node.lower;
            }

            search(q, nearer, neighbor);

            // now look in further half
            if (neighbor.distance >= diff) {
                search(q, further, neighbor);
            }
        }
    }

    /**
     * Returns (in the supplied heap object) the k nearest
     * neighbors of the given target starting from the give
     * tree node.
     *
     * @param q    the query key.
     * @param node the root of subtree.
     * @param heap the heap object to store/update the kNNs found during the search.
     */
    private void search(double[] q, Node node, HeapSelect<NeighborBuilder<double[], E>> heap) {
        if (node.isLeaf()) {
            // look at all the instances in this leaf
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    double distance = MathEx.distance(q, keys[i]);
                    NeighborBuilder<double[], E> datum = heap.peek();
                    if (distance < datum.distance) {
                        datum.distance = distance;
                        datum.index = i;
                        heap.heapify();
                    }
                }
            }
        } else {
            Node nearer, further;
            double diff = q[node.split] - node.cutoff;
            if (diff < 0) {
                nearer = node.lower;
                further = node.upper;
            } else {
                nearer = node.upper;
                further = node.lower;
            }

            search(q, nearer, heap);

            // now look in further half
            if (heap.peek().distance >= diff) {
                search(q, further, heap);
            }
        }
    }

    /**
     * Returns the neighbors in the given range of search target from the give
     * tree node.
     *
     * @param q the query key.
     * @param node the root of subtree.
     * @param radius the radius of search range from target.
     * @param neighbors the list of found neighbors in the range.
     */
    private void search(double[] q, Node node, double radius, List<Neighbor<double[], E>> neighbors) {
        if (node.isLeaf()) {
            // look at all the instances in this leaf
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    double distance = MathEx.distance(q, keys[i]);
                    if (distance <= radius) {
                        neighbors.add(new Neighbor<>(keys[i], data[i], i, distance));
                    }
                }
            }
        } else {
            Node nearer, further;
            double diff = q[node.split] - node.cutoff;
            if (diff < 0) {
                nearer = node.lower;
                further = node.upper;
            } else {
                nearer = node.upper;
                further = node.lower;
            }

            search(q, nearer, radius, neighbors);

            // now look in further half
            if (radius >= Math.abs(diff)) {
                search(q, further, radius, neighbors);
            }
        }
    }

    @Override
    public Neighbor<double[], E> nearest(double[] q) {
        NeighborBuilder<double[], E> neighbor = new NeighborBuilder<>();
        search(q, root, neighbor);
        neighbor.key = keys[neighbor.index];
        neighbor.value = data[neighbor.index];
        return neighbor.toNeighbor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<double[], E>[] knn(double[] q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > keys.length) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        HeapSelect<NeighborBuilder<double[], E>> heap = new HeapSelect<>(k);
        for (int i = 0; i < k; i++) {
            heap.add(new NeighborBuilder<>());
        }

        search(q, root, heap);
        heap.sort();

        return Arrays.stream(heap.toArray())
                .map(neighbor -> {
                    neighbor.key = keys[neighbor.index];
                    neighbor.value = data[neighbor.index];
                    return neighbor.toNeighbor();
                }).toArray(Neighbor[]::new);
    }

    @Override
    public void range(double[] q, double radius, List<Neighbor<double[], E>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        search(q, root, radius, neighbors);
    }
}
