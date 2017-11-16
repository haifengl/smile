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
package smile.clustering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import smile.clustering.linkage.Linkage;
import smile.clustering.linkage.WardLinkage;
import smile.math.Math;

/**
 * Balanced Iterative Reducing and Clustering using Hierarchies. BIRCH performs
 * hierarchical clustering over particularly large datasets. An advantage of
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
 * Then the user can use the learned model to cluster input data in the final
 * step. In total, we scan the database twice.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Tian Zhang, Raghu Ramakrishnan, and Miron Livny. BIRCH: An Efficient Data Clustering Method for Very Large Databases. SIGMOD, 1996.</li>
 * </ol>
 * 
 * @see HierarchicalClustering
 * @see KMeans
 * 
 * @author Haifeng Li
 */
public class BIRCH implements Clustering<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Branching factor. Maximum number of children nodes.
     */
    private int B;
    /**
     * Maximum radius of a sub-cluster.
     */
    private double T;
    /**
     * The dimensionality of data.
     */
    private int d;
    /**
     * The root of CF tree.
     */
    private Node root;
    /**
     * Leaves of CF tree as representatives of all data points.
     */
    private double[][] centroids;

    /**
     * Internal node of CF tree.
     */
    class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The number of observations
         */
        int n;
        /**
         * The sum of the observations
         */
        double[] sum;
        /**
         * The number of children.
         */
        int numChildren;
        /**
         * Children nodes.
         */
        Node[] children;
        /**
         * Parent node.
         */
        Node parent;

        /**
         * Constructor of root node
         */
        Node() {
            n = 0;
            sum = new double[d];
            parent = null;
            numChildren = 0;
            children = new Node[B];
        }

        /**
         * Calculates the distance between x and CF center
         */
        double distance(double[] x) {
            double dist = 0.0;
            for (int i = 0; i < d; i++) {
                double d = sum[i] / n - x[i];
                dist += d * d;
            }
            return Math.sqrt(dist);
        }

        /**
         * Calculates the distance between CF centers
         */
        double distance(Node node) {
            double dist = 0.0;
            for (int i = 0; i < d; i++) {
                double d = sum[i] / n - node.sum[i] / node.n;
                dist += d * d;
            }
            return Math.sqrt(dist);
        }

        /**
         * Returns the leaf node closest to the given data.
         */
        Leaf search(double[] x) {
            int index = 0;
            double smallest = children[0].distance(x);

            // find the closest child node to this data point
            for (int i = 1; i < numChildren; i++) {
                double dist = children[i].distance(x);
                if (dist < smallest) {
                    index = i;
                    smallest = dist;
                }
            }

            if (children[index].numChildren == 0) {
                return (Leaf) children[index];
            } else {
                return children[index].search(x);
            }
        }

        /**
         * Adds data to this node.
         */
        void update(double[] x) {
            n++;

            for (int i = 0; i < d; i++) {
                sum[i] += x[i];
            }
        }

        /**
         * Adds data to the node.
         */
        void add(double[] x) {
            update(x);

            int index = 0;
            double smallest = children[0].distance(x);

            // find the closest child node to this data point
            for (int i = 1; i < numChildren; i++) {
                double dist = children[i].distance(x);
                if (dist < smallest) {
                    index = i;
                    smallest = dist;
                }
            }

            if (children[index] instanceof Leaf) {
                if (smallest > T) {
                    add(new Leaf(x));
                } else {
                    children[index].add(x);
                }
            } else {
                children[index].add(x);
            }
        }

        /**
         * Add a node as children. Split this node if the number of children
         * reach the Branch Factor.
         */
        void add(Node node) {
            if (numChildren < B) {
                children[numChildren++] = node;
                node.parent = this;
            } else {
                if (parent == null) {
                    parent = new Node();
                    parent.add(this);
                    root = parent;
                } else {
                    parent.n = 0;
                    Arrays.fill(parent.sum, 0.0);
                }

                parent.add(split(node));

                for (int i = 0; i < parent.numChildren; i++) {
                    parent.n += parent.children[i].n;
                    for (int j = 0; j < d; j++) {
                        parent.sum[j] += parent.children[i].sum[j];
                    }
                }
            }
        }

        /**
         * Split the node and return a new node to add into the parent
         */
        Node split(Node node) {
            double farest = 0.0;
            int c1 = 0, c2 = 0;
            double[][] dist = new double[numChildren + 1][numChildren + 1];
            for (int i = 0; i < numChildren; i++) {
                for (int j = i + 1; j < numChildren; j++) {
                    dist[i][j] = children[i].distance(children[j]);
                    dist[j][i] = dist[i][j];
                    if (farest < dist[i][j]) {
                        c1 = i;
                        c2 = j;
                        farest = dist[i][j];
                    }
                }

                dist[i][numChildren] = children[i].distance(node);
                dist[numChildren][i] = dist[i][numChildren];
                if (farest < dist[i][numChildren]) {
                    c1 = i;
                    c2 = numChildren;
                    farest = dist[i][numChildren];
                }
            }

            int nc = numChildren;
            Node[] child = children;

            // clean up this node.
            numChildren = 0;
            n = 0;
            Arrays.fill(sum, 0.0);

            Node brother = new Node();
            for (int i = 0; i < nc; i++) {
                if (dist[i][c1] < dist[i][c2]) {
                    add(child[i]);
                } else {
                    brother.add(child[i]);
                }
            }

            if (dist[nc][c1] < dist[nc][c2]) {
                add(node);
            } else {
                brother.add(node);
            }

            for (int i = 0; i < numChildren; i++) {
                n += children[i].n;
                for (int j = 0; j < d; j++) {
                    sum[j] += children[i].sum[j];
                }
            }

            for (int i = 0; i < brother.numChildren; i++) {
                brother.n += brother.children[i].n;
                for (int j = 0; j < d; j++) {
                    brother.sum[j] += brother.children[i].sum[j];
                }
            }

            return brother;
        }
    }

    /**
     * Leaf node of CF tree.
     */
    class Leaf extends Node {
        /**
         * The cluster label of the leaf node.
         */
        int y;

        /**
         * Constructor.
         */
        Leaf(double[] x) {
            n = 1;
            System.arraycopy(x, 0, sum, 0, d);
        }

        @Override
        void add(double[] x) {
            n++;
            for (int i = 0; i < d; i++) {
                sum[i] += x[i];
            }
        }
    }

    /**
     * Constructor.
     * @param d the dimensionality of data.
     * @param B the branching factor. Maximum number of children nodes.
     * @param T the maximum radius of a sub-cluster.
     */
    public BIRCH(int d, int B, double T) {
        this.d = d;
        this.B = B;
        this.T = T;
    }

    /**
     * Add a data point into CF tree.
     */
    public void add(double[] x) {
        if (root == null) {
            root = new Node();
            root.add(new Leaf(x));
            root.update(x);
        } else {
            root.add(x);
        }
    }

    /**
     * Returns the branching factor, which is the maximum number of children nodes.
     */
    public int getBrachingFactor() {
        return B;
    }

    /**
     * Returns the maximum radius of a sub-cluster.
     */
    public double getMaxRadius() {
        return T;
    }

    /**
     * Returns the dimensionality of data.
     */
    public int dimension() {
        return d;
    }
    
    /**
     * Clustering leaves of CF tree into k clusters.
     * @param k the number of clusters.
     * @return the number of non-outlier leaves.
     */
    public int partition(int k) {
        return partition(k, 0);
    }

    /**
     * Clustering leaves of CF tree into k clusters.
     * @param k the number of clusters.
     * @param minPts a CF leaf will be treated as outlier if the number of its
     * points is less than minPts.
     * @return the number of non-outlier leaves.
     */
    public int partition(int k, int minPts) {
        ArrayList<Leaf> leaves = new ArrayList<>();
        ArrayList<double[]> centers = new ArrayList<>();
        Queue<Node> queue = new LinkedList<>();
        queue.offer(root);

        for (Node node = queue.poll(); node != null; node = queue.poll()) {
            if (node.numChildren == 0) {
                if (node.n >= minPts) {
                    double[] x = new double[d];
                    for (int i = 0; i < d; i++) {
                        x[i] = node.sum[i] / node.n;
                    }
                    centers.add(x);
                    leaves.add((Leaf) node);
                } else {
                    Leaf leaf = (Leaf) node;
                    leaf.y = OUTLIER;
                }
            } else {
                for (int i = 0; i < node.numChildren; i++) {
                    queue.offer(node.children[i]);
                }
            }
        }

        int n = centers.size();
        centroids = centers.toArray(new double[n][]);

        if (n > k) {
            double[][] proximity = new double[n][];
            for (int i = 0; i < n; i++) {
                proximity[i] = new double[i + 1];
                for (int j = 0; j < i; j++) {
                    proximity[i][j] = Math.distance(centroids[i], centroids[j]);
                }
            }

            Linkage linkage = new WardLinkage(proximity);
            HierarchicalClustering hc = new HierarchicalClustering(linkage);

            int[] y = hc.partition(k);
            for (int i = 0; i < n; i++) {
                leaves.get(i).y = y[i];
            }
        } else {
            for (int i = 0; i < n; i++) {
                leaves.get(i).y = i;
            }
        }

        return n;
    }

    /**
     * Cluster a new instance to the nearest CF leaf. After building the 
     * CF tree, the user should call {@link #partition(int)} method first
     * to clustering leaves. Then they call this method to clustering new
     * data.
     * 
     * @param x a new instance.
     * @return the cluster label, which is the label of nearest CF leaf.
     * Note that it may be {@link #OUTLIER}.
     */
    @Override
    public int predict(double[] x) {
        if (centroids == null) {
            throw new IllegalStateException("Call partition() first!");
        }
        
        Leaf leaf = root.search(x);
        return leaf.y;
    }

    /**
     * Returns the representatives of clusters.
     * 
     * @return the representatives of clusters
     */
    public double[][] centroids() {
        if (centroids == null) {
            throw new IllegalStateException("Call partition() first!");
        }
        
        return centroids;
    }
}
