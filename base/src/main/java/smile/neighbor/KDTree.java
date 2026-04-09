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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.neighbor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import smile.math.MathEx;
import smile.sort.HeapSelect;

/**
 * A KD-tree (short for k-dimensional tree) is a space-partitioning dataset
 * structure for organizing points in a k-dimensional space. KD-trees are
 * a useful dataset structure for nearest neighbor searches. The kd-tree is a
 * binary tree in which every node is a k-dimensional point. Every non-leaf
 * node generates a splitting hyperplane that divides the space into two
 * subspaces. Points left to the hyperplane represent the left subtree of
 * that node and the points right to the hyperplane by the right subtree.
 * The hyperplane direction is chosen in the following way: every node split
 * to subtrees is associated with one of the k-dimensions, such that the
 * hyperplane is perpendicular to that dimension vector. So, for example, if
 * for a particular split the "x" axis is chosen, all points in the subtree
 * with a smaller "x" value than the node will appear in the left subtree and
 * all points with larger "x" value will be in the right subtree.
 * <p>
 * KD-trees are not suitable for efficiently finding the nearest neighbor
 * in high dimensional spaces. As a general rule, if the dimensionality is D,
 * then number of points in the dataset, N, should be {@code N >>} 2<sup>D</sup>.
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
public class KDTree <E> implements KNNSearch<double[], E>, RNNSearch<double[], E>, Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The root in the KD-tree.
     */
    static class Node implements Serializable {

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
     * The object keys.
     */
    private final double[][] keys;
    /**
     * The data objects.
     */
    private final E[] data;
    /**
     * The root node of KD-Tree.
     */
    private final Node root;
    /**
     * The index of objects in each node.
     */
    private final int[] index;
    /**
     * The dimensionality of the data.
     */
    private final int d;

    /**
     * Constructor.
     * @param key the object keys.
     * @param data the data objects.
     */
    public KDTree(double[][] key, E[] data) {
        if (key.length != data.length) {
            throw new IllegalArgumentException("The array size of keys and data are different.");
        }

        this.keys = key;
        this.data = data;
        this.d = key[0].length;

        int n = key.length;
        index = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = i;
        }

        // Build the tree
        double[] lowerBound = new double[d];
        double[] upperBound = new double[d];
        root = buildNode(0, n, lowerBound, upperBound);
    }

    /**
     * Return a KD-tree of the data.
     * @param data the data objects, which are also used as key.
     * @return KD-tree.
     */
    public static KDTree<double[]> of(double[][] data) {
        return new KDTree<>(data, data);
    }

    @Override
    public String toString() {
        return "KD-Tree";
    }

    /**
     * Returns the number of data points in the KD-tree.
     * @return the number of data points.
     */
    public int size() {
        return keys.length;
    }

    /**
     * Builds a subtree.
     * @param begin the beginning index of samples for the subtree (inclusive).
     * @param end the ending index of samples for the subtree (exclusive).
     * @param lowerBound workspace for the lower bound of each dimension (may be overwritten).
     * @param upperBound workspace for the upper bound of each dimension (may be overwritten).
     */
    private Node buildNode(int begin, int end, double[] lowerBound, double[] upperBound) {
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
                if (lowerBound[j] > c) lowerBound[j] = c;
                if (upperBound[j] < c) upperBound[j] = c;
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
        if (MathEx.isZero(maxRadius, 1E-8)) {
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

        // If either side is empty, make this a leaf node.
        if (size == 0 || size == node.count) {
            node.lower = node.upper = null;
            return node;
        }

        // Create the child nodes. Each child gets its own workspace arrays so
        // the sibling's bounding-box scan does not overwrite the parent's data.
        double[] childLower = new double[d];
        double[] childUpper = new double[d];
        node.lower = buildNode(begin, begin + size, lowerBound, upperBound);
        node.upper = buildNode(begin + size, end, childLower, childUpper);

        return node;
    }

    /**
     * Computes the squared Euclidean distance between {@code q} and {@code p},
     * abandoning the computation early if the accumulated sum already exceeds
     * {@code limit} (a squared distance). Returns the full squared distance
     * when no early exit occurs.
     *
     * @param q     the query vector.
     * @param p     a candidate point.
     * @param limit the current best squared distance (upper bound).
     * @return the squared distance, or a value &gt; {@code limit} on early exit.
     */
    private double squaredDistanceEarlyExit(double[] q, double[] p, double limit) {
        double sum = 0.0;
        for (int i = 0; i < d; i++) {
            double diff = q[i] - p[i];
            sum += diff * diff;
            if (sum >= limit) return sum;   // early exit
        }
        return sum;
    }

    /**
     * NN search – traverses using squared distances to avoid sqrt during search.
     * {@code neighbor.distance} holds the squared distance while recursing;
     * it is converted to true distance by the caller.
     *
     * @param q        the query key.
     * @param node     the root of subtree.
     * @param neighbor the current best neighbor (distance field = squared distance).
     */
    private void search(double[] q, Node node, NeighborBuilder<double[], E> neighbor) {
        if (node.isLeaf()) {
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    double sd = squaredDistanceEarlyExit(q, keys[i], neighbor.distance);
                    if (sd < neighbor.distance) {
                        neighbor.index = i;
                        neighbor.distance = sd;
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

            // Prune: only visit further child if the splitting plane is closer
            // than the current best. Compare squared distances to avoid sqrt.
            if (neighbor.distance >= diff * diff) {
                search(q, further, neighbor);
            }
        }
    }

    /**
     * kNN search – traverses using squared distances to avoid sqrt during search.
     * {@code heap} entries store squared distances while recursing.
     *
     * @param q    the query key.
     * @param node the root of subtree.
     * @param heap the max-heap of k current best neighbors (distance = squared).
     */
    private void search(double[] q, Node node, HeapSelect<NeighborBuilder<double[], E>> heap) {
        if (node.isLeaf()) {
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    NeighborBuilder<double[], E> worst = heap.peek();
                    double sd = squaredDistanceEarlyExit(q, keys[i], worst.distance);
                    if (sd < worst.distance) {
                        worst.distance = sd;
                        worst.index = i;
                        heap.siftDown();
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

            // Prune using squared distance to avoid sqrt.
            if (heap.peek().distance >= diff * diff) {
                search(q, further, heap);
            }
        }
    }

    /**
     * Range search – radius is the true Euclidean radius; internally we compare
     * against {@code radius²} to avoid computing sqrt for every candidate.
     *
     * @param q         the query key.
     * @param node      the root of subtree.
     * @param radius    the true (non-squared) search radius.
     * @param radius2   the squared search radius (= radius * radius).
     * @param neighbors the list of found neighbors in the range.
     */
    private void search(double[] q, Node node, double radius, double radius2,
                        List<Neighbor<double[], E>> neighbors) {
        if (node.isLeaf()) {
            for (int idx = node.index; idx < node.index + node.count; idx++) {
                int i = index[idx];
                if (q != keys[i]) {
                    double sd = squaredDistanceEarlyExit(q, keys[i], radius2);
                    if (sd <= radius2) {
                        neighbors.add(new Neighbor<>(keys[i], data[i], i, Math.sqrt(sd)));
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

            search(q, nearer, radius, radius2, neighbors);

            // Prune using squared distance to avoid sqrt/abs.
            if (radius2 >= diff * diff) {
                search(q, further, radius, radius2, neighbors);
            }
        }
    }

    @Override
    public Neighbor<double[], E> nearest(double[] q) {
        NeighborBuilder<double[], E> neighbor = new NeighborBuilder<>();
        // neighbor.distance holds squared distance during search
        search(q, root, neighbor);
        neighbor.key = keys[neighbor.index];
        neighbor.value = data[neighbor.index];
        // convert squared distance to true distance
        neighbor.distance = Math.sqrt(neighbor.distance);
        return neighbor.toNeighbor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<double[], E>[] search(double[] q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > keys.length) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        HeapSelect<NeighborBuilder<double[], E>> heap = new HeapSelect<>(NeighborBuilder.class, k);
        for (int i = 0; i < k; i++) {
            heap.add(new NeighborBuilder<>());
        }

        // heap entries hold squared distances during search
        search(q, root, heap);
        heap.sort();

        // Convert squared distances to true distances and assemble result array.
        // Use a plain loop instead of Stream to avoid allocation overhead.
        NeighborBuilder<double[], E>[] builders = heap.toArray();
        Neighbor<double[], E>[] result = new Neighbor[builders.length];
        for (int i = 0; i < builders.length; i++) {
            NeighborBuilder<double[], E> nb = builders[i];
            nb.key = keys[nb.index];
            nb.value = data[nb.index];
            nb.distance = Math.sqrt(nb.distance);
            result[i] = nb.toNeighbor();
        }
        return result;
    }

    @Override
    public void search(double[] q, double radius, List<Neighbor<double[], E>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        search(q, root, radius, radius * radius, neighbors);
    }
}
