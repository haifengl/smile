/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
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
package smile.neighbor;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;

/**
 * Random projection trees. The Random Projection Tree structures are space
 * partitioning data structures that automatically adapt to various notions
 * of intrinsic dimensionality of data.
 *
 * @author Karl Li
 */
public class RandomProjectionTree implements KNNSearch<double[], double[]> {
    // Used for a floating point "nearly zero" comparison
    private static final float EPS = 1e-8F;
    private final double[][] data;
    private final Node root;
    private final int leafSize;
    private final boolean angular;

    private RandomProjectionTree(double[][] data, Node root, int leafSize, boolean angular) {
        this.data = data;
        this.root = root;
        this.leafSize = leafSize;
        this.angular = angular;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Neighbor<double[], double[]>[] search(double[] q, int k) {
        if (k > leafSize) {
            throw new IllegalArgumentException("k must be <= leafSize");
        }

        Node leaf = root.search(q);
        int[] samples = leaf.samples();
        Neighbor<double[], double[]>[] neighbors = (Neighbor<double[], double[]>[]) Array.newInstance(Neighbor.class, samples.length);
        for (int i = 0; i < samples.length; i++) {
            int index = samples[i];
            double[] x = data[index];
            double dist = angular ? 1 - MathEx.cosine(q, x) : MathEx.distance(q, x);
            neighbors[i] = Neighbor.of(x, index, dist);
        }
        Arrays.sort(neighbors);
        return samples.length <= k ? neighbors : Arrays.copyOf(neighbors, k);
    }

    record Node(int[] samples, double[] hyperplane, double offset, Node leftChild, Node rightChild) {
        boolean isLeaf() {
            return leftChild == null && rightChild == null;
        }

        /** Returns the number of nodes in this subtree. */
        int numNodes() {
            return 1 + (leftChild != null ? leftChild.numNodes() : 0) + (rightChild != null ? rightChild.numNodes() : 0);
        }

        /** Returns the number of leaf nodes in this subtree. */
        int numLeaves() {
            return isLeaf() ? 1 : (leftChild != null ? leftChild.numLeaves() : 0) + (rightChild != null ? rightChild.numLeaves() : 0);
        }

        Node search(double[] point) {
            if (isLeaf()) return this;
            int node = 0;
            boolean rightSide = isRightSide(point, hyperplane, offset);
            return rightSide ? rightChild.search(point) : leftChild.search(point);
        }

        int[] recursiveFlatten(double[][] hyperplanes, double[] offsets, int[][] children, int[][] indices, int nodeNum, int leafNum) {
            if (isLeaf()) {
                children[nodeNum] = new int[]{-leafNum, -1};
                indices[leafNum] = samples;
                return new int[]{nodeNum, leafNum + 1};
            } else {
                hyperplanes[nodeNum] = hyperplane;
                offsets[nodeNum] = offset;
                int[] flattenInfo = leftChild.recursiveFlatten(hyperplanes, offsets, children, indices, nodeNum + 1, leafNum);
                children[nodeNum] = new int[] {nodeNum + 1, flattenInfo[0] + 1};
                return rightChild.recursiveFlatten(hyperplanes, offsets, children, indices, flattenInfo[0] + 1, flattenInfo[1]);
            }
        }
    }

    RandomProjectionForest.FlatTree flatten() {
        int numNodes = root.numNodes();
        int numLeaves = root.numLeaves();

        double[][] hyperplanes = new double[numNodes][];
        double[] offsets = new double[numNodes];
        int[][] children = new int[numNodes][];
        int[][] indices = new int[numLeaves][];
        root.recursiveFlatten(hyperplanes, offsets, children, indices, 0, 0);
        return new RandomProjectionForest.FlatTree(hyperplanes, offsets, children, indices);
    }

    record Split(int[] leftSamples, int[] rightSamples, double[] hyperplane, double offset) {

    }

    private static double[] normalize(double[] x) {
        double norm = MathEx.norm(x);
        if (Math.abs(norm) < EPS) {
            norm = 1;
        }

        int n = x.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = x[i] / norm;
        }

        return y;
    }

    static boolean isRightSide(double[] point, double[] hyperplane, double offset) {
        double margin = offset;
        for (int i = 0; i < point.length; i++) {
            margin += hyperplane[i] * point[i];
        }

        return Math.abs(margin) < EPS ? MathEx.random() < 0.5 : margin < 0;
    }

    /**
     * Given a set of sample indices for data points, create a random
     * hyperplane to split the data. This particular split uses cosine
     * distance to determine the hyperplane.
     *
     * @param data the data points.
     * @param samples the sample indices.
     *
     * @return the split object.
     */
    private static Split angularSplit(double[][] data, int[] samples) {
        int dim = data[0].length;

        // Select two random points, set the hyperplane between them
        int leftIndex = MathEx.randomInt(samples.length);
        int rightIndex = MathEx.randomInt(samples.length);
        if (leftIndex == rightIndex && ++rightIndex == samples.length) {
            rightIndex = 0;
        }

        double[] left = normalize(data[samples[leftIndex]]);
        double[] right = normalize(data[samples[rightIndex]]);

        for (int d = 0; d < dim; ++d) {
            left[d] -= right[d];
        }

        double[] hyperplane = normalize(left);

        int numLeft = 0;
        int numRight = 0;
        boolean[] rightSide = new boolean[samples.length];
        for (int i = 0; i < samples.length; ++i) {
            rightSide[i] = isRightSide(data[samples[i]], hyperplane, 0);
            if (rightSide[i]) {
                ++numRight;
            } else {
                ++numLeft;
            }
        }

        return split(numLeft, numRight, samples, rightSide, hyperplane, 0);
    }


    /**
     * Given a set of sample indices for data points, create a random
     * hyperplane to split the data. This particular split uses Euclidean
     * distance to determine the hyperplane.
     *
     * @param data the data points.
     * @param samples the sample indices.
     *
     * @return the split object.
     */
    private static Split euclideanSplit(double[][] data, int[] samples) {
        int dim = data[0].length;

        // Select two random points, set the hyperplane between them
        int leftIndex = MathEx.randomInt(samples.length);
        int rightIndex = MathEx.randomInt(samples.length);
        if (leftIndex == rightIndex && ++rightIndex == samples.length) {
            rightIndex = 0;
        }

        double[] left = normalize(data[samples[leftIndex]]);
        double[] right = normalize(data[samples[rightIndex]]);

        for (int d = 0; d < dim; ++d) {
            left[d] -= right[d];
        }

        double offset = 0;
        double[] hyperplane = new double[dim];

        for (int d = 0; d < dim; ++d) {
            double ld = left[d];
            double rd = right[d];
            double delta = ld - rd;
            hyperplane[d] = delta;
            offset -= delta * (ld + rd);
        }
        offset /= 2;

        int numLeft = 0;
        int numRight = 0;
        boolean[] rightSide = new boolean[samples.length];
        for (int i = 0; i < samples.length; ++i) {
            rightSide[i] = isRightSide(data[samples[i]], hyperplane, offset);
            if (rightSide[i]) {
                ++numRight;
            } else {
                ++numLeft;
            }
        }

        return split(numLeft, numRight, samples, rightSide, hyperplane, offset);
    }

    private static Split split(int numLeft, int numRight, int[] samples, boolean[] rightSide, double[] hyperplane, double offset) {
        if (numLeft == 0 || numRight == 0) return null;

        int[] leftSamples = new int[numLeft];
        int[] rightSamples = new int[numRight];
        for (int i = 0, l = 0, r = 0; i < rightSide.length; ++i) {
            if (rightSide[i]) {
                rightSamples[r++] = samples[i];
            } else {
                leftSamples[l++] = samples[i];
            }
        }
        return new Split(leftSamples, rightSamples, hyperplane, offset);
    }

    private static Node makeEuclideanTree(double[][] data, int[] samples, int leafSize) {
        if (samples.length <= leafSize) {
            return new Node(samples, null, 0, null, null);
        }

        Split split = euclideanSplit(data, samples);
        if (split == null) {
            return new Node(samples, null, 0, null, null);
        }

        Node leftNode = makeEuclideanTree(data, split.leftSamples, leafSize);
        Node rightNode = makeEuclideanTree(data, split.rightSamples, leafSize);
        return new Node(null, split.hyperplane, split.offset, leftNode, rightNode);
    }

    private static Node makeAngularTree(double[][] data, int[] samples, int leafSize) {
        if (samples.length <= leafSize) {
            return new Node(samples, null, 0, null, null);
        }

        Split split = angularSplit(data, samples);
        if (split == null) {
            return new Node(samples, null, 0, null, null);
        }

        Node leftNode = makeAngularTree(data, split.leftSamples, leafSize);
        Node rightNode = makeAngularTree(data, split.rightSamples, leafSize);
        return new Node(null, split.hyperplane, split.offset, leftNode, rightNode);
    }

    /**
     * Builds a random projection tree.
     * @param data the data set.
     * @param leafSize The maximum size of leaf node.
     * @param angular true for cosine metric, otherwise Euclidean.
     * @return A random projection tree.
     */
    public static RandomProjectionTree of(double[][] data, int leafSize, boolean angular) {
        int[] samples = IntStream.range(0, data.length).toArray();
        Node root = angular ? makeAngularTree(data, samples, leafSize) : makeEuclideanTree(data, samples, leafSize);
        return new RandomProjectionTree(data, root, leafSize, angular);
    }
}