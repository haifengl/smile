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
import java.util.List;

import smile.math.Math;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.math.distance.Metric;
import smile.neighbor.CoverTree;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RNNSearch;
import smile.util.MulticoreExecutor;

/**
 * Nonparametric Minimum Conditional Entropy Clustering. This method performs 
 * very well especially when the exact number of clusters is unknown.
 * The method can also correctly reveal the structure of data and effectively
 * identify outliers simultaneously.
 * <p>
 * The clustering criterion is based on the conditional entropy H(C | x), where
 * C is the cluster label and x is an observation. According to Fano's
 * inequality, we can estimate C with a low probability of error only if the
 * conditional entropy H(C | X) is small. MEC also generalizes the criterion
 * by replacing Shannon's entropy with Havrda-Charvat's structural
 * &alpha;-entropy. Interestingly, the minimum entropy criterion based
 * on structural &alpha;-entropy is equal to the probability error of the
 * nearest neighbor method when &alpha;= 2. To estimate p(C | x), MEC employs
 * Parzen density estimation, a nonparametric approach.
 * <p>
 * MEC is an iterative algorithm starting with an initial partition given by
 * any other clustering methods, e.g. k-means, CLARNAS, hierarchical clustering,
 * etc. Note that a random initialization is NOT appropriate.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Haifeng Li, Keshu Zhang, and Tao Jiang. Minimum Entropy Clustering and Applications to Gene Expression Analysis. CSB, 2004. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class MEC <T> extends PartitionClustering<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The range of neighborhood.
     */
    private double radius;
    /**
     * The conditional entropy as the objective function.
     */
    private double entropy;
    /**
     * The neighborhood search data structure.
     */
    private RNNSearch<T,T> nns;

    /**
     * Constructor. Clustering the data.
     * @param data the dataset for clustering.
     * @param distance the distance measure for neighborhood search.
     * @param k the number of clusters. Note that this is just a hint. The final
     * number of clusters may be less.
     * @param radius the neighborhood radius.
     */
    public MEC(T[] data, Distance<T> distance, int k, double radius) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        LinearSearch<T> naive = new LinearSearch<>(data, distance);
        naive.setIdenticalExcluded(false);

        // Initialize clusters with KMeans/CLARANS.
        if (data[0] instanceof double[] && distance instanceof EuclideanDistance) {
            KMeans kmeans = new KMeans((double[][]) data, k, 10, Math.max(1, MulticoreExecutor.getThreadPoolSize()));
            y = kmeans.getClusterLabel();
        } else {
            CLARANS<T> clarans = new CLARANS<>(data, distance, k, Math.min(100, (int) Math.round(0.01 * k * (data.length - k))), Math.max(1, MulticoreExecutor.getThreadPoolSize()));
            y = clarans.getClusterLabel();
        }

        learn(data, naive, k, radius, y);
    }

    /**
     * Constructor. Clustering the data.
     * @param data the dataset for clustering.
     * @param distance the distance measure for neighborhood search.
     * @param k the number of clusters. Note that this is just a hint. The final
     * number of clusters may be less.
     * @param radius the neighborhood radius.
     */
    public MEC(T[] data, Metric<T> distance, int k, double radius) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        CoverTree<T> cover = new CoverTree<>(data, distance);
        cover.setIdenticalExcluded(false);


        // Initialize clusters with KMeans/CLARANS.
        if (data[0] instanceof double[] && distance instanceof EuclideanDistance) {
            KMeans kmeans = new KMeans((double[][]) data, k, 10, Math.max(1, MulticoreExecutor.getThreadPoolSize()));
            y = kmeans.getClusterLabel();
        } else {
            CLARANS<T> clarans = new CLARANS<>(data, distance, k, Math.min(100, (int) Math.round(0.01 * k * (data.length - k))), Math.max(1, MulticoreExecutor.getThreadPoolSize()));
            y = clarans.getClusterLabel();
        }

        learn(data, cover, k, radius, y);
    }

    /**
     * Constructor. Clustering the data.
     * @param data the dataset for clustering.
     * @param nns the neighborhood search data structure.
     * @param k the number of clusters. Note that this is just a hint. The final
     * number of clusters may be less.
     * @param radius the neighborhood radius.
     * @param y the initial clustering labels, which could be produced by any
     * other clustering methods.
     */
    public MEC(T[] data, RNNSearch<T,T> nns, int k, double radius, int[] y) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        learn(data, nns, k, radius, y.clone());
    }

    /**
     * Clustering the data.
     * @param data the dataset for clustering.
     * @param y the initial clustering labels, which could be produced by any
     * other clustering methods.
     * @param nns the data structure for neighborhood search.
     * @param k the number of clusters. Note that this is just a hint. The final
     * number of clusters may be less.
     * @param radius the radius for neighbor range search.
     */
    private void learn(T[] data, RNNSearch<T,T> nns, int k, double radius, int[] y) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        this.k = k;
        this.nns = nns;
        this.radius = radius;
        this.y = y;
        this.size = new int[k];

        int n = data.length;
        for (int i = 0; i < n; i++) {
            size[y[i]]++;
        }

        // Initialize the neighborhood list for each sample.
        double[] px = new double[n];

        // Neighbors of each sample.
        ArrayList<ArrayList<Integer>> neighbors = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ArrayList<Neighbor<T,T>> list = new ArrayList<>();
            nns.range(data[i], radius, list);
            ArrayList<Integer> neighbor = new ArrayList<>(list.size());
            neighbors.add(neighbor);
            for (Neighbor<T,T> nt : list) {
                neighbor.add(nt.index);
            }
            px[i] = (double) list.size() / n;
        }

        // Initialize a posterior probabilities.

        // The count of each cluster in the neighborhood.
        int[][] neighborhoodClusterSize = new int[n][k];
        // The most significant cluster in the neighborhood.
        int[] mostSignificantNeighborhoodCluster = new int[n];

        for (int i = 0; i < n; i++) {
            for (int j : neighbors.get(i)) {
                neighborhoodClusterSize[i][y[j]]++;
            }
        }

        for (int i = 0; i < n; i++) {
            int max = 0;
            for (int j = 0; j < k; j++) {
                if (neighborhoodClusterSize[i][j] > max) {
                    mostSignificantNeighborhoodCluster[i] = j;
                    max = neighborhoodClusterSize[i][j];
                }
            }
        }

        // The number of samples with nonzero conditional entropy.
        entropy = 0.0;
        for (int i = 0; i < n; i++) {
            if (!neighbors.get(i).isEmpty()) {
                int ni = neighbors.get(i).size();
                double m = 0.0;
                for (int j = 0; j < k; j++) {
                    double r = ((double) neighborhoodClusterSize[i][j]) / ni;
                    if (r > 0) {
                        m -= r * Math.log2(r);
                    }
                }

                m *= px[i];
                entropy += m;
            }
        }

        double eps = 1.0;
        while (eps >= 1E-7) {
            for (int i = 0; i < n; i++) {
                if (mostSignificantNeighborhoodCluster[i] != y[i]) {
                    double oldMutual = 0.0;
                    double newMutual = 0.0;

                    for (int neighbor : neighbors.get(i)) {
                        double nk = neighbors.get(neighbor).size();

                        double r1 = (double) neighborhoodClusterSize[neighbor][y[i]] / nk;
                        double r2 = (double) neighborhoodClusterSize[neighbor][mostSignificantNeighborhoodCluster[i]] / nk;
                        if (r1 > 0) {
                            oldMutual -= r1 * Math.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            oldMutual -= r2 * Math.log2(r2) * px[neighbor];
                        }

                        r1 = (neighborhoodClusterSize[neighbor][y[i]] - 1.0) / nk;
                        r2 = (neighborhoodClusterSize[neighbor][mostSignificantNeighborhoodCluster[i]] + 1.0) / nk;
                        if (r1 > 0) {
                            newMutual -= r1 * Math.log2(r1) * px[neighbor];
                        }
                        if (r2 > 0) {
                            newMutual -= r2 * Math.log2(r2) * px[neighbor];
                        }
                    }

                    if (newMutual < oldMutual) {
                        for (int neighbor : neighbors.get(i)) {
                            --neighborhoodClusterSize[neighbor][y[i]];
                            ++neighborhoodClusterSize[neighbor][mostSignificantNeighborhoodCluster[i]];
                            int mi = mostSignificantNeighborhoodCluster[i];
                            int mk = mostSignificantNeighborhoodCluster[neighbor];
                            if (neighborhoodClusterSize[neighbor][mi] > neighborhoodClusterSize[neighbor][mk]) {
                                mostSignificantNeighborhoodCluster[neighbor] = mostSignificantNeighborhoodCluster[i];
                            }
                        }

                        size[y[i]]--;
                        size[mostSignificantNeighborhoodCluster[i]]++;
                        y[i] = mostSignificantNeighborhoodCluster[i];
                    }
                }
            }

            double prevObj = entropy;
            entropy = 0.0;
            for (int i = 0; i < n; i++) {
                if (!neighbors.get(i).isEmpty()) {
                    int ni = neighbors.get(i).size();
                    double m = 0.0;
                    for (int j = 0; j < k; j++) {
                        double r = ((double) neighborhoodClusterSize[i][j]) / ni;
                        if (r > 0) {
                            m -= r * Math.log2(r);
                        }
                    }

                    m *= px[i];
                    entropy += m;
                }
            }

            eps = prevObj - entropy;
        }

        // Collapse clusters by removing clusters with no samples.
        int nk = 0;
        for (int i = 0; i < k; i++) {
            if (size[i] > 0) {
                nk++;
            }
        }

        int[] count = new int[nk];
        for (int i = 0, j = 0; i < k; i++) {
            if (size[i] > 0) {
                count[j] = size[i];
                size[i] = j++;
            }
        }

        for (int i = 0; i < n; i++) {
            y[i] = size[y[i]];
        }

        this.k = nk;
        size = count;
    }

    /**
     * Returns the cluster conditional entropy.
     */
    public double entropy() {
        return entropy;
    }

    /**
     * Returns the radius of neighborhood.
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label. Note that it may be {@link #OUTLIER}.
     */
    @Override
    public int predict(T x) {
        List<Neighbor<T,T>> neighbors = new ArrayList<>();
        nns.range(x, radius, neighbors);

        if (neighbors.isEmpty()) {
            return OUTLIER;
        }

        int[] label = new int[k];
        for (Neighbor<T,T> neighbor : neighbors) {
            int yi = y[neighbor.index];
            label[yi]++;
        }

        return Math.whichMax(label);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("MEC cluster conditional entropy: %.5f%n", entropy));
        sb.append(String.format("Clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        return sb.toString();
    }
}
