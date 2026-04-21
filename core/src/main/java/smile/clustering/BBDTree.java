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
package smile.clustering;

import java.util.Arrays;
import smile.tensor.ScalarType;
import smile.tensor.Vector;

/**
 * Balanced Box-Decomposition Tree. BBD tree is a specialized k-d tree that
 * vastly speeds up an iteration of k-means. This is used internally by KMeans
 * and batch SOM., and will most likely not need to be used directly.
 * <p>
 * The structure works as follows:
 * <ul>
 * <li> All data are placed into a tree where we choose child nodes by
 *      partitioning all data along a plane parallel to the axis.
 * <li> We maintain for each node, the bounding box of all data stored
 *      at that node.
 * <li> To do a k-means iteration, we need to assign data to clusters and
 *      calculate the sum and the number of data assigned to each cluster.
 *      For each node in the tree, we can rule out some cluster centroids as
 *      being too far away from every single point in that bounding box.
 *      Once only one cluster is left, all data in the node can be assigned
 *      to that cluster in batch.
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>Tapas Kanungo, David M. Mount, Nathan S. Netanyahu, Christine D. Piatko, Ruth Silverman, and Angela Y. Wu.
 * An Efficient k-Means Clustering Algorithm: Analysis and Implementation. IEEE TRANS. PAMI, 2002.</li>
 * </ol>
 *
 * @see KMeans
 * @see smile.vq.SOM
 *
 * @author Haifeng Li
 */
public class BBDTree {

    /**
     * Functional interface for accessing individual data values, abstracting
     * over {@code double[][]} and {@code float[][]} input types.
     */
    @FunctionalInterface
    private interface DataAccessor {
        /**
         * Returns the value at the given row and column of the data set.
         *
         * @param row the row (data point) index.
         * @param col the column (dimension) index.
         * @return the value as a {@code double}.
         */
        double get(int row, int col);
    }

    static class Node {
        /**
         * The number of data stored in this node.
         */
        int size;
        /**
         * The smallest point index stored in this node.
         */
        int index;
        /**
         * The center/mean of bounding box.
         */
        final Vector center;
        /**
         * The half side-lengths of bounding box.
         */
        final Vector radius;
        /**
         * The sum of the data stored in this node.
         */
        final Vector sum;
        /**
         * The min cost for putting all data in this node in 1 cluster.
         */
        double cost;
        /**
         * The child node of lower half box.
         */
        Node lower;
        /**
         * The child node of upper half box.
         */
        Node upper;

        /**
         * Constructor.
         * @param scalarType the scalar type used for the vector fields.
         * @param d the dimension of vector space.
         */
        Node(ScalarType scalarType, int d) {
            center = Vector.zeros(scalarType, d);
            radius = Vector.zeros(scalarType, d);
            sum    = Vector.zeros(scalarType, d);
        }
    }

    /**
     * Root node.
     */
    private final Node root;
    /**
     * The index of data objects.
     */
    private final int[] index;

    /**
     * Constructs a tree out of the given n data points living in R^d.
     * Uses {@link ScalarType#Float64} (double precision) for internal vectors.
     *
     * @param data the data points.
     */
    public BBDTree(double[][] data) {
        int n = data.length;
        index = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = i;
        }
        root = buildNode((r, c) -> data[r][c], ScalarType.Float64, data[0].length, 0, n);
    }

    /**
     * Constructs a tree out of the given n data points living in R^d.
     * Uses {@link ScalarType#Float32} (single precision) for internal vectors.
     *
     * @param data the data points in single precision.
     */
    public BBDTree(float[][] data) {
        int n = data.length;
        index = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = i;
        }
        root = buildNode((r, c) -> data[r][c], ScalarType.Float32, data[0].length, 0, n);
    }

    /**
     * Builds a k-d tree node from the given range of data points using the
     * supplied accessor, supporting both {@code double[][]} and
     * {@code float[][]} inputs.
     *
     * @param data       accessor for individual data values.
     * @param scalarType the scalar type used for vector fields in each node.
     * @param d          the number of dimensions.
     * @param begin      the first index (inclusive) of the range to build from.
     * @param end        the last index (exclusive) of the range to build from.
     * @return the constructed node.
     */
    private Node buildNode(DataAccessor data, ScalarType scalarType, int d, int begin, int end) {
        // Allocate the node
        Node node = new Node(scalarType, d);

        // Fill in basic info
        node.size  = end - begin;
        node.index = begin;

        // Calculate the bounding box
        Vector lowerBound = Vector.zeros(scalarType, d);
        Vector upperBound = Vector.zeros(scalarType, d);

        for (int i = 0; i < d; i++) {
            lowerBound.set(i, data.get(index[begin], i));
            upperBound.set(i, data.get(index[begin], i));
        }

        for (int i = begin + 1; i < end; i++) {
            for (int j = 0; j < d; j++) {
                double c = data.get(index[i], j);
                if (lowerBound.get(j) > c) {
                    lowerBound.set(j, c);
                }
                if (upperBound.get(j) < c) {
                    upperBound.set(j, c);
                }
            }
        }

        // Calculate bounding box stats
        double maxRadius = -1;
        int splitIndex = -1;
        for (int i = 0; i < d; i++) {
            node.center.set(i, (lowerBound.get(i) + upperBound.get(i)) / 2);
            node.radius.set(i, (upperBound.get(i) - lowerBound.get(i)) / 2);
            if (node.radius.get(i) > maxRadius) {
                maxRadius = node.radius.get(i);
                splitIndex = i;
            }
        }

        // If the max spread is 0, make this a leaf node
        if (maxRadius < 1E-10) {
            node.lower = node.upper = null;
            for (int j = 0; j < d; j++) {
                node.sum.set(j, data.get(index[begin], j));
            }

            if (end > begin + 1) {
                int len = end - begin;
                for (int i = 0; i < d; i++) {
                    node.sum.mul(i, len);
                }
            }

            node.cost = 0;
            return node;
        }

        // Partition the data around the midpoint in this dimension. The
        // partitioning is done in-place by iterating from left-to-right and
        // right-to-left in the same way that partitioning is done in quicksort.
        double splitCutoff = node.center.get(splitIndex);
        int i1 = begin, i2 = end - 1, size = 0;
        while (i1 <= i2) {
            boolean i1Good = (data.get(index[i1], splitIndex) < splitCutoff);
            boolean i2Good = (data.get(index[i2], splitIndex) >= splitCutoff);

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
        node.lower = buildNode(data, scalarType, d, begin, begin + size);
        node.upper = buildNode(data, scalarType, d, begin + size, end);

        // Calculate the new sum and opt cost
        for (int i = 0; i < d; i++) {
            node.sum.set(i, node.lower.sum.get(i) + node.upper.sum.get(i));
        }

        Vector mean = node.lower.sum.scalarType() == ScalarType.Float32
                ? Vector.zeros(ScalarType.Float32, d)
                : Vector.zeros(ScalarType.Float64, d);
        for (int i = 0; i < d; i++) {
            mean.set(i, node.sum.get(i) / node.size);
        }

        node.cost = getNodeCost(node.lower, mean) + getNodeCost(node.upper, mean);
        return node;
    }

    /**
     * Returns the total contribution of all data in the given kd-tree node,
     * assuming they are all assigned to a mean at the given location.
     * <p>
     *   sum_{x \in node} ||x - mean||<sup>2</sup>
     * <p>
     * If c denotes the mean of mass of the data in this node and n denotes
     * the number of data in it, then this quantity is given by
     * <p>
     *   n * ||c - mean||<sup>2</sup> + sum_{x \in node} ||x - c||<sup>2</sup>
     * <p>
     * The sum is precomputed for each node as cost. This formula follows
     * from expanding both sides as dot products.
     */
    private double getNodeCost(Node node, Vector center) {
        int d = center.size();
        double scatter = 0.0;
        for (int i = 0; i < d; i++) {
            double x = (node.sum.get(i) / node.size) - center.get(i);
            scatter += x * x;
        }
        return node.cost + node.size * scatter;
    }

    /**
     * Given k cluster centroids, this method assigns data to nearest centroids.
     * The return value is the distortion to the centroids. The parameter sums
     * will hold the sum of data for each cluster. The parameter counts hold
     * the number of data of each cluster. If membership is
     * not null, it should be an array of size n that will be filled with the
     * index of the cluster [0, k) that each data point is assigned to.
     *
     * @param k         the number of clusters.
     * @param centroids the current centroids of clusters.
     * @param sum       the workspace storing the sum of data in each cluster.
     * @param size      the number of samples in each cluster.
     * @param group     the class labels.
     * @return the distortion.
     */
    public double clustering(int k, Vector[] centroids, Vector[] sum, int[] size, int[] group) {
        Arrays.fill(size, 0);
        int[] candidates = new int[k];
        for (int i = 0; i < k; i++) {
            candidates[i] = i;
            sum[i].fill(0.0);
        }

        double wcss = filter(root, centroids, candidates, k, sum, size, group);

        int d = centroids[0].size();
        for (int i = 0; i < k; i++) {
            if (size[i] > 0) {
                for (int j = 0; j < d; j++) {
                    centroids[i].set(j, sum[i].get(j) / size[i]);
                }
            }
        }

        return wcss / group.length;
    }

    /**
     * This determines which clusters all data that are rooted node will be
     * assigned to, and updates sums, counts and membership (if not null)
     * accordingly. Candidates maintains the set of cluster indices which
     * could possibly be the closest clusters for data in this subtree.
     */
    private double filter(Node node, Vector[] centroids, int[] candidates, int k, Vector[] sum, int[] size, int[] group) {
        int d = centroids[0].size();

        // Determine which mean the node mean is closest to
        double minDist = Vector.squaredDistance(node.center, centroids[candidates[0]]);
        int closest = candidates[0];
        for (int i = 1; i < k; i++) {
            double dist = Vector.squaredDistance(node.center, centroids[candidates[i]]);
            if (dist < minDist) {
                minDist = dist;
                closest = candidates[i];
            }
        }

        // If this is a non-leaf node, recurse if necessary
        if (node.lower != null) {
            // Build the new list of candidates
            int[] newCandidates = new int[k];
            int k2 = 0;

            for (int i = 0; i < k; i++) {
                if (!prune(node.center, node.radius, centroids, closest, candidates[i])) {
                    newCandidates[k2++] = candidates[i];
                }
            }

            // Recurse if there's at least two
            if (k2 > 1) {
                return filter(node.lower, centroids, newCandidates, k2, sum, size, group)
                     + filter(node.upper, centroids, newCandidates, k2, sum, size, group);
            }
        }

        // Assigns all data within this node to a single mean
        for (int i = 0; i < d; i++) {
            sum[closest].set(i, sum[closest].get(i) + node.sum.get(i));
        }

        size[closest] += node.size;

        int last = node.index + node.size;
        for (int i = node.index; i < last; i++) {
            group[index[i]] = closest;
        }

        return getNodeCost(node, centroids[closest]);
    }

    /**
     * Determines whether every point in the box is closer to centroids[bestIndex] than to
     * centroids[testIndex].
     * <p>
     * If x is a point, c_0 = centroids[bestIndex], c = centroids[testIndex], then:
     *       (x-c).(x-c) < (x-c_0).(x-c_0)
     *   <=> (c-c_0).(c-c_0) < 2(x-c_0).(c-c_0)
     * <p>
     * The right-hand side is maximized for a vertex of the box where for each
     * dimension, we choose the low or high value based on the sign of x-c_0 in
     * that dimension.
     */
    private boolean prune(Vector center, Vector radius, Vector[] centroids, int bestIndex, int testIndex) {
        if (bestIndex == testIndex) {
            return false;
        }

        int d = centroids[0].size();

        Vector best = centroids[bestIndex];
        Vector test = centroids[testIndex];
        double lhs = 0.0, rhs = 0.0;
        for (int i = 0; i < d; i++) {
            double diff = test.get(i) - best.get(i);
            lhs += diff * diff;
            if (diff > 0) {
                rhs += (center.get(i) + radius.get(i) - best.get(i)) * diff;
            } else {
                rhs += (center.get(i) - radius.get(i) - best.get(i)) * diff;
            }
        }

        return (lhs >= 2 * rhs);
    }
}
