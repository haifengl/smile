/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.anomaly;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;

/**
 * Isolation tree.
 *
 * @author Haifeng Li
 */
public class IsolationTree implements Serializable {
    /**
     * Isolation tree node.
     */
    class Node implements Serializable {
        /** The adjusted depth of node in the tree. */
        final double depth;
        /** The normal vector of random hyperplane, uniformly over the unit N-Sphere. */
        final double[] slope;
        /**
         * The intercept point, draw from a uniform distribution
         * over the range of values present at each branching point
         */
        final double[] intercept;
        /** The dot product of slope and intercept. */
        final double bias;
        /** The left child branch. */
        final Node left;
        /** The right child branch. */
        final Node right;

        /**
         * Leaf node constructor.
         * @param depth the adjusted depth of node in the tree.
         */
        Node(double depth) {
            this(depth, null, null, 0.0, null, null);
        }

        /**
         * Constructor.
         * @param depth the adjusted depth of node in the tree.
         * @param slope the normal vector of random hyperplane.
         * @param intercept the intercept point.
         * @param bias the dot product of slope and intercept.
         * @param left the left child branch.
         * @param right the right child branch.
         */
        Node(double depth, double[] slope, double[] intercept, double bias, Node left, Node right) {
            this.depth = depth;
            this.slope = slope;
            this.intercept = intercept;
            this.bias = bias;
            this.left = left;
            this.right = right;
        }

        /**
         * Returns the path length from the root to the leaf node.
         * @param x the sample.
         * @return the path length.
         */
        public double path(double[] x) {
            if (left == null && right == null) {
                return depth;
            } else {
                double dot = MathEx.dot(x, slope);
                if (dot < bias) {
                    return left.path(x);
                } else {
                    return right.path(x);
                }
            }
        }
    }

    /**
     * Tree root node.
     */
    private final Node root;

    /**
     * Constructor.
     *
     * @param data the training data.
     * @param maxDepth the maximum depth of the tree.
     * @param extensionLevel the extension level.
     */
    public IsolationTree(List<double[]> data, int maxDepth, int extensionLevel) {
        root = buildNode(data, maxDepth, extensionLevel, 0);
    }

    /**
     * Returns the path length from the root to the leaf node.
     * @param x the sample.
     * @return the path length.
     */
    public double path(double[] x) {
        return root.path(x);
    }

    /**
     * Builds an isolation tree node.
     * @param data the training data.
     * @param maxDepth the maximum depth of the tree.
     * @param extensionLevel the extension level.
     * @param depth the node depth in the tree.
     * @return the node.
     */
    private Node buildNode(List<double[]> data, int maxDepth, int extensionLevel, int depth) {
        if (depth >= maxDepth || data.size() <= 1) {
            double adjustedDepth = depth;
            if (data.size() > 1) {
                adjustedDepth += IsolationForest.factor(data.size());
            }
            return new Node(adjustedDepth);
        } else {
            double[] min = data.get(0).clone();
            double[] max = data.get(0).clone();
            int p = min.length;
            for (double[] x : data) {
                for (int i = 0; i < p; i++) {
                    if (x[i] < min[i]) min[i] = x[i];
                    else if (x[i] > max[i]) max[i] = x[i];
                }
            }

            // Pick a random point on splitting hyperplane
            double[] intercept = new double[p];
            for (int i = 0; i < p; i++) {
                intercept[i] = MathEx.random(min[i], max[i]);
            }

            // Pick a random normal vector according to specified extension level
            GaussianDistribution gauss = GaussianDistribution.getInstance();
            double[] slope = new double[p];
            for (int i = 0; i < p; i++) {
                slope[i] = gauss.rand();
            }

            int[] index = MathEx.permutate(p);
            for (int i = 0; i < p - extensionLevel - 1; i++) {
                slope[index[i]] = 0.0;
            }

            double bias = MathEx.dot(slope, intercept);
            ArrayList<double[]> leftData = new ArrayList<>();
            ArrayList<double[]> rightData = new ArrayList<>();
            for (double[] x : data) {
                double dot = MathEx.dot(x, slope);
                if (dot < bias) {
                    leftData.add(x);
                } else {
                    rightData.add(x);
                }
            }

            Node left = buildNode(leftData, maxDepth, extensionLevel, depth+1);
            Node right = buildNode(rightData, maxDepth, extensionLevel, depth+1);

            return new Node(depth, slope, intercept, bias, left, right);
        }
    }
}
