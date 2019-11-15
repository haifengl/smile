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

package smile.vq;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import smile.math.MathEx;
import smile.util.IntPair;

/**
 * Balanced Iterative Reducing and Clustering using Hierarchies. BIRCH performs
 * hierarchical clustering over particularly large data. An advantage of
 * BIRCH is its ability to incrementally and dynamically cluster incoming,
 * multi-dimensional metric data points in an attempt to produce the high
 * quality clustering for a given set of resources (memory and time constraints).
 * <p>
 * BIRCH has several advantages. For example, each clustering decision is made
 * without scanning all data points and currently existing clusters. It
 * exploits the observation that data space is not usually uniformly occupied
 * and not every data point is equally important. It makes full use of
 * available memory to derive the finest possible sub-clusters while minimizing
 * I/O costs. It is also an incremental method that does not require the whole
 * data set in advance.
 * <p>
 * This implementation produces a clustering in three steps. First step
 * builds a CF (clustering feature) tree by a single scan of database.
 * The second step clusters the leaves of CF tree by hierarchical clustering.
 * Then the user can use the learned model to classify input data in the final
 * step. In total, we scan the database twice.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Tian Zhang, Raghu Ramakrishnan, and Miron Livny. BIRCH: An Efficient Data Clustering Method for Very Large Databases. SIGMOD, 1996.</li>
 * </ol>
 * 
 * @see smile.clustering.HierarchicalClustering
 *
 * @author Haifeng Li
 */
public class BIRCH implements VectorQuantizer {
    private static final long serialVersionUID = 2L;

    /**
     * The branching factor of non-leaf nodes.
     */
    public final int B;
    /**
     * The number of CF entries in the leaf nodes.
     */
    public final int L;
    /**
     * THe maximum radius of a sub-cluster.
     */
    public final double T;
    /**
     * The dimensionality of data.
     */
    public final int d;
    /**
     * The root of CF tree.
     */
    private Node root;

    /**
     * The Clustering Feature (CF) vector of the cluster is defined as a
     * triple: CF = (N, LS, SS).
     */
    private class ClusteringFeature implements Serializable {
        /** The number of observations. */
        private int n;
        /** The sum of observations. */
        private double[] sum = new double[d];
        /** The square sum of observations. */
        private double[] ss = new double[d];

        /**
         * Constructor.
         *
         * @param x the first observation added to this CF.
         */
        public ClusteringFeature(double[] x) {
            n = 1;
            System.arraycopy(x, 0, sum, 0, d);
            for (int i = 0; i < d; i++) {
                ss[i] = x[i] * x[i];
            }
        }

        /**
         * Constructor.
         *
         * @param clusters sub-clusters.
         */
        public ClusteringFeature(ClusteringFeature... clusters) {
            n = 0;
            for (ClusteringFeature cluster : clusters) {
                n += cluster.n;
                for (int i = 0; i < d; i++) {
                    sum[i] += cluster.sum[i];
                    ss[i] += cluster.ss[i];
                }
            }
        }

        /** Returns the centroid of CF. */
        public double[] centroid() {
            double[] centroid = new double[d];
            for (int i = 0; i < d; i++) {
                centroid[i] = sum[i] / n;
            }

            return centroid;
        }

        /** Returns the radius of CF. */
        public double radius() {
            double r = 0.0;
            for (int i = 0; i < d; i++) {
                double mu = sum[i] / n;
                r += ss[i] / n - mu * mu;
            }
            return Math.sqrt(r);
        }

        /** Returns the radius of CF with an additional observation. */
        public double radius(double[] x) {
            int n1 = n + 1;
            double r = 0.0;
            for (int i = 0; i < d; i++) {
                double mu = (sum[i] + x[i]) / n1;
                r += (ss[i] + x[i] * x[i]) / n1 - mu * mu;
            }
            return Math.sqrt(r);
        }

        /**
         * Adds an observation to the CF. No check of radius and split.
         * */
        public void update(double[] x) {
            n = n + 1;
            for (int i = 0; i < d; i++) {
                sum[i] += x[i];
                ss[i] += x[i] * x[i];
            }
        }

        /**
         * Adds an observation to the CF. If the radius of CF with additional
         * observation is greater than the radius threshold, don't update
         * the CF and returns a new CF with the observation.
         * */
        public Optional<ClusteringFeature> add(double[] x) {
            if (radius(x) > T) {
                return Optional.of(new ClusteringFeature(x));
            }

            update(x);
            return Optional.empty();
        }

        /**
         * Returns the distance between x and CF centroid.
         */
        public double distance(double[] x) {
            double dist = 0.0;
            for (int i = 0; i < d; i++) {
                double diff = sum[i] / n - x[i];
                dist += diff * diff;
            }
            return Math.sqrt(dist);
        }

        /**
         * Returns the distance between CF centroids.
         */
        public double distance(ClusteringFeature o) {
            double dist = 0.0;
            for (int i = 0; i < d; i++) {
                double diff = sum[i] / n - o.sum[i] / o.n;
                dist += diff * diff;
            }
            return Math.sqrt(dist);
        }
    }

    /** The node interface of CF tree. */
    private abstract class Node implements Serializable {
        /**
         * The clustering feature of observations in the node.
         */
        protected ClusteringFeature cluster;

        /** Constructor. */
        public Node(ClusteringFeature... clusters) {
            cluster = new ClusteringFeature(clusters);
        }

        /** Constructor. */
        public Node(Node... nodes) {
            ClusteringFeature[] clusters = Arrays.stream(nodes).map(node -> node.cluster).toArray(ClusteringFeature[]::new);
            cluster = new ClusteringFeature(clusters);
        }

        /**
         * Returns the leaf CF closest to the given observation.
         */
        public abstract ClusteringFeature nearest(double[] x);

        /**
         * Adds a new observation to the node. If the node is split,
         * returns the added node.
         */
        public abstract Optional<Node> add(double[] x);

        /**
         * Calculates the distance between x and CF center
         */
        public double distance(double[] x) {
            return cluster.distance(x);
        }

        /** Pair-wise distance. */
        public double[][] pdist(ClusteringFeature[] clusters) {
            int k = clusters.length;
            double[][] dist = new double[k][k];
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    dist[i][j] = clusters[i].distance(clusters[j]);
                    dist[j][i] = dist[i][j];
                }
            }

            return dist;
        }

        /** Pair-wise distance. */
        public double[][] pdist(Node[] nodes) {
            ClusteringFeature[] clusters = Arrays.stream(nodes).map(node -> node.cluster).toArray(ClusteringFeature[]::new);
            return pdist(clusters);
        }
    }

    /**
     * A CF tree is a height balanced tree.
     */
    private class InternalNode extends Node {
        /**
         * The children nodes.
         */
        private Node[] children;
        /**
         * The number of children.
         */
        private int k;

        /**
         * Constructor of root node
         */
        public InternalNode(Node... nodes) {
            super(nodes);
            k = nodes.length;
            children = new Node[B];
            System.arraycopy(nodes, 0, children, 0, nodes.length);
        }

        @Override
        public ClusteringFeature nearest(double[] x) {
            int index = 0;
            double nearest = children[0].distance(x);

            // find the closest child node to this data point
            for (int i = 1; i < k; i++) {
                double dist = children[i].distance(x);
                if (dist < nearest) {
                    index = i;
                    nearest = dist;
                }
            }

            return children[index].nearest(x);
        }

        @Override
        public Optional<Node> add(double[] x) {
            int index = 0;
            double nearest = children[0].distance(x);

            // find the closest child node to this data point
            for (int i = 1; i < k; i++) {
                double dist = children[i].distance(x);
                if (dist < nearest) {
                    index = i;
                    nearest = dist;
                }
            }

            Optional<Node> sister = children[index].add(x);

            if (sister.isPresent()) {
                if (k < B) {
                    children[k++] = sister.get();
                } else {
                    return Optional.of(split(sister.get()));
                }
            }

            cluster.update(x);
            return Optional.empty();
        }

        /**
         * Split the node and return a new node to add into the parent
         */
        private Node split(Node node) {
            Node[] nodes = new Node[B+1];
            System.arraycopy(children, 0, nodes, 0, B);
            nodes[B] = node;

            double[][] dist = pdist(nodes);
            IntPair farthest = MathEx.whichMax(dist);

            k = 0;
            int n = 0;
            Node[] sister = new Node[B];
            for (int i = 0; i <= B; i++) {
                if (dist[i][farthest.i] < dist[i][farthest.j]) {
                    children[k++] = nodes[i];
                } else {
                    sister[n++] = nodes[i];
                }
            }

            for (int i = k; i < B; i++) {
                this.children[i] = null;
            }
            this.cluster = new ClusteringFeature(Arrays.stream(children).limit(k).map(child -> child.cluster).toArray(ClusteringFeature[]::new));

            return new InternalNode(Arrays.copyOf(sister, n));
        }
    }

    /**
     * The leaf node of CF tree.
     */
    private class Leaf extends Node {
        private ClusteringFeature[] clusters;
        private int k;

        /**
         * Constructor.
         */
        public Leaf(ClusteringFeature... clusters) {
            super(clusters);
            k = clusters.length;
            this.clusters = new ClusteringFeature[L];
            System.arraycopy(clusters, 0, this.clusters, 0, clusters.length);
        }

        /**
         * Constructor.
         */
        public Leaf(double[] x) {
            this(new ClusteringFeature(x));
        }

        @Override
        public ClusteringFeature nearest(double[] x) {
            int index = 0;
            double nearest = clusters[0].distance(x);

            // find the closest child node to this data point
            for (int i = 1; i < k; i++) {
                double dist = clusters[i].distance(x);
                if (dist < nearest) {
                    index = i;
                    nearest = dist;
                }
            }

            return clusters[index];
        }

        @Override
        public Optional<Node> add(double[] x) {
            ClusteringFeature cluster = nearest(x);
            Optional<ClusteringFeature> sister = cluster.add(x);
            if (sister.isPresent()) {
                if (k < L) {
                    clusters[k++] = sister.get();
                } else {
                    return Optional.of(split(sister.get()));
                }
            }

            this.cluster.update(x);
            return Optional.empty();
        }

        /**
         * Splits the node and returns a new sister node.
         */
        private Node split(ClusteringFeature cluster) {
            ClusteringFeature[] clusters = new ClusteringFeature[L+1];
            System.arraycopy(this.clusters, 0, clusters, 0, L);
            clusters[L] = cluster;

            double[][] dist = pdist(clusters);
            IntPair farthest = MathEx.whichMax(dist);

            k = 0;
            int n = 0;
            ClusteringFeature[] sister = new ClusteringFeature[L];
            for (int i = 0; i <= L; i++) {
                if (dist[i][farthest.i] < dist[i][farthest.j]) {
                    this.clusters[k++] = clusters[i];
                } else {
                    sister[n++] = clusters[i];
                }
            }

            for (int i = k; i < L; i++) {
                this.clusters[i] = null;
            }
            this.cluster = new ClusteringFeature(Arrays.copyOf(this.clusters, k));

            return new Leaf(Arrays.copyOf(sister, n));
        }
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param B the branching factor of non-leaf nodes, i.e. the maximum number
     *          of children nodes.
     * @param L the number entries in the leaf nodes.
     * @param T the maximum radius of a sub-cluster.
     */
    public BIRCH(int d, int B, int L, double T) {
        this.d = d;
        this.B = B;
        this.L = L;
        this.T = T;
    }

    @Override
    public void update(double[] x) {
        if (root == null) {
            root = new Leaf(x);
        } else {
            Optional<Node> sister = root.add(x);
            sister.ifPresent(child -> root = new InternalNode(root, child));
        }
    }

    @Override
    public double[] quantize(double[] x) {
        ClusteringFeature cluster = root.nearest(x);
        return cluster.centroid();
    }

    /** Returns the cluster centroids of leaf nodes. */
    public double[][] centroids() {
        ArrayList<double[]> list = new ArrayList<>();
        centroids(root, list);
        return list.toArray(new double[list.size()][]);
    }

    /** Collects the centroids of leaf nodes in the subtree. */
    private void centroids(Node node, ArrayList<double[]> list) {
        if (node instanceof Leaf) {
            Leaf leaf = (Leaf) node;
            for (int i = 0; i < leaf.k; i++)
            list.add(leaf.clusters[i].centroid());
        } else {
            InternalNode parent = (InternalNode) node;
            for (int i = 0; i < parent.k; i++) {
                centroids(parent.children[i], list);
            }
        }
    }
}
