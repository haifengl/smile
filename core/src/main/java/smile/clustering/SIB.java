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
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.SparseArray;
import smile.data.SparseDataset;
import smile.util.MulticoreExecutor;

/**
 * The Sequential Information Bottleneck algorithm. SIB clusters co-occurrence
 * data such as text documents vs words. SIB is guaranteed to converge to a local
 * maximum of the information. Moreover, the time and space complexity are
 * significantly improved in contrast to the agglomerative IB algorithm.
 * <p>
 * In analogy to K-Means, SIB's update formulas are essentially same as the
 * EM algorithm for estimating finite Gaussian mixture model by replacing
 * regular Euclidean distance with Kullback-Leibler divergence, which is
 * clearly a better dissimilarity measure for co-occurrence data. However,
 * the common batch updating rule (assigning all instances to nearest centroids
 * and then updating centroids) of K-Means won't work in SIB, which has
 * to work in a sequential way (reassigning (if better) each instance then
 * immediately update related centroids). It might be because K-L divergence
 * is very sensitive and the centroids may be significantly changed in each
 * iteration in batch updating rule.
 * <p>
 * Note that this implementation has a little difference from the original
 * paper, in which a weighted Jensen-Shannon divergence is employed as a
 * criterion to assign a randomly-picked sample to a different cluster.
 * However, this doesn't work well in some cases as we experienced probably
 * because the weighted JS divergence gives too much weight to clusters which
 * is much larger than a single sample. In this implementation, we instead
 * use the regular/unweighted Jensen-Shannon divergence.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> N. Tishby, F.C. Pereira, and W. Bialek. The information bottleneck method. 1999.</li>
 * <li> N. Slonim, N. Friedman, and N. Tishby. Unsupervised document classification using sequential information maximization. ACM SIGIR, 2002.</li>
 * <li>Jaakko Peltonen, Janne Sinkkonen, and Samuel Kaski. Sequential information bottleneck for finite data. ICML, 2004.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SIB extends PartitionClustering<double[]> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SIB.class);

    /**
     * The total distortion.
     */
    private double distortion;
    /**
     * The centroids of each cluster.
     */
    private double[][] centroids;

    /**
     * Constructor. Clustering data into k clusters up to 100 iterations.
     * @param data the normalized co-occurrence input data of which each row
     * is a sample with sum 1.
     * @param k the number of clusters.
     */
    public SIB(double[][] data, int k) {
        this(data, k, 100);
    }

    /**
     * Constructor. Clustering data into k clusters.
     * @param data the input data of which each row is a sample.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     */
    public SIB(double[][] data, int k, int maxIter) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid parameter k = " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data.length;
        int d = data[0].length;

        this.k = k;
        size = new int[k];
        centroids = new double[k][d];
        y = seed(data, k, ClusteringDistance.JENSEN_SHANNON_DIVERGENCE);

        for (int i = 0; i < n; i++) {
            size[y[i]]++;
            for (int j = 0; j < d; j++) {
                centroids[y[i]][j] += data[i][j];
            }
        }

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < d; j++) {
                centroids[i][j] /= size[i];
            }
        }
        
        for (int iter = 1, reassignment = n; iter <= maxIter && reassignment > 0; iter++) {
            reassignment = 0;
            
            for (int i = 0; i < n; i++) {
                double nearest = Double.MAX_VALUE;
                int c = -1;
                for (int j = 0; j < k; j++) {
                    double dist = Math.JensenShannonDivergence(data[i], centroids[j]);
                    if (nearest > dist) {
                        nearest = dist;
                        c = j;
                    }
                }

                if (c != y[i]) {
                    int o = y[i];
                    if (size[o] > 1) {
                        int m = size[o] - 1;
                        for (int j = 0; j < d; j++) {
                            centroids[o][j] = (centroids[o][j] * size[o] - data[i][j]) / m;
                            if (centroids[o][j] < 0) {
                                centroids[o][j] = 0;
                            }
                        }
                    } else {
                        Arrays.fill(centroids[o], 0.0);
                    }

                    int m = size[c] + 1;
                    for (int j = 0; j < d; j++) {
                        centroids[c][j] = (centroids[c][j] * size[c] + data[i][j]) / m;
                    }

                    size[o]--;
                    size[c]++;

                    y[i] = c;
                    reassignment++;
                }
            }
        }

        distortion = 0;
        for (int i = 0; i < n; i++) {
            distortion += Math.JensenShannonDivergence(data[i], centroids[y[i]]);
        }            
    }

    /**
     * Constructor. Run SIB clustering algorithm multiple times and return the best one.
     * @param data the input data of which each row is a sample.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param runs the number of runs of SIB algorithm.
     */
    public SIB(double[][] data, int k, int maxIter, int runs) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (runs <= 0) {
            throw new IllegalArgumentException("Invalid number of runs: " + runs);
        }

        List<SIBThread> tasks = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            tasks.add(new SIBThread(data, k, maxIter));
        }

        SIB best = null;

        try {
            List<SIB> clusters = MulticoreExecutor.run(tasks);
            best = clusters.get(0);
            for (int i = 1; i < runs; i++) {
                SIB sib = clusters.get(i);
                if (sib.distortion < best.distortion) {
                    best = sib;
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to run Sequential Information Bottleneck on multi-core", ex);

            best = new SIB(data, k, maxIter);
            for (int i = 1; i < runs; i++) {
                SIB sib = new SIB(data, k, maxIter);
                if (sib.distortion < best.distortion) {
                    best = sib;
                }
            }            
        }

        this.k = best.k;
        this.distortion = best.distortion;
        this.centroids = best.centroids;
        this.y = best.y;
        this.size = best.size;
    }

    /**
     * Initialize clusters with KMeans++ algorithm.
     */
    private static int[] seed(SparseDataset data, int k) {
        int n = data.size();

        int[] y = new int[n];
        SparseArray centroid = data.get(Math.randomInt(n)).x;

        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            D[i] = Double.MAX_VALUE;
        }

        // pick the next center
        for (int i = 1; i < k; i++) {
            for (int j = 0; j < n; j++) {
                double dist = Math.JensenShannonDivergence(data.get(j).x, centroid);
                if (dist < D[j]) {
                    D[j] = dist;
                    y[j] = i - 1;
                }
            }

            double cutoff = Math.random() * Math.sum(D);
            double cost = 0.0;
            int index = 0;
            for (; index < n; index++) {
                cost += D[index];
                if (cost >= cutoff) {
                    break;
                }
            }

            centroid = data.get(index).x;
        }

        for (int j = 0; j < n; j++) {
            // compute the distance between this sample and the current center
            double dist = Math.JensenShannonDivergence(data.get(j).x, centroid);
            if (dist < D[j]) {
                D[j] = dist;
                y[j] = k - 1;
            }
        }

        return y;
    }

    /**
     * Constructor. Clustering data into k clusters up to 100 iterations.
     * @param data the sparse normalized co-occurrence dataset of which each row
     * is a sample with sum 1.
     * @param k the number of clusters.
     */
    public SIB(SparseDataset data, int k) {
        this(data, k, 100);
    }

    /**
     * Constructor. Clustering data into k clusters.
     * @param data the sparse normalized co-occurrence dataset of which each row
     * is a sample with sum 1.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     */
    public SIB(SparseDataset data, int k, int maxIter) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid parameter k = " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        int n = data.size();
        int d = data.ncols();

        this.k = k;
        distortion = Double.MAX_VALUE;
        size = new int[k];
        centroids = new double[k][d];
        y = seed(data, k);

        for (int i = 0; i < n; i++) {
            size[y[i]]++;
            for (SparseArray.Entry e : data.get(i).x) {
                centroids[y[i]][e.i] += e.x;
            }
        }

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < d; j++) {
                centroids[i][j] /= size[i];
            }
        }
        
        for (int iter = 1, reassignment = n; iter <= maxIter && reassignment > 0; iter++) {
            reassignment = 0;
            
            for (int i = 0; i < n; i++) {
                double nearest = Double.MAX_VALUE;
                int c = -1;
                for (int j = 0; j < k; j++) {
                    double dist = Math.JensenShannonDivergence(data.get(i).x, centroids[j]);
                    if (nearest > dist) {
                        nearest = dist;
                        c = j;
                    }
                }

                if (c != y[i]) {
                    int o = y[i];
                    for (int j = 0; j < d; j++) {
                        centroids[c][j] *= size[c];
                        centroids[o][j] *= size[o];
                    }

                    for (SparseArray.Entry e : data.get(i).x) {
                        int j = e.i;
                        double p = e.x;
                        centroids[c][j] += p;
                        centroids[o][j] -= p;
                        if (centroids[o][j] < 0) {
                            centroids[o][j] = 0;
                        }
                    }

                    size[o]--;
                    size[c]++;

                    for (int j = 0; j < d; j++) {
                        centroids[c][j] /= size[c];
                    }

                    if (size[o] > 0) {
                        for (int j = 0; j < d; j++) {
                            centroids[o][j] /= size[o];
                        }
                    }

                    y[i] = c;
                    reassignment++;
                }
            }
        }

        distortion = 0;
        for (int i = 0; i < n; i++) {
            distortion += Math.JensenShannonDivergence(data.get(i).x, centroids[y[i]]);
        }            
    }

    /**
     * Constructor. Run SIB clustering algorithm multiple times and return the best one.
     * @param data the sparse normalized co-occurrence dataset of which each row
     * is a sample with sum 1.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations.
     * @param runs the number of runs of SIB algorithm.
     */
    public SIB(SparseDataset data, int k, int maxIter, int runs) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        if (runs <= 0) {
            throw new IllegalArgumentException("Invalid number of runs: " + runs);
        }

        List<SIBThread> tasks = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            tasks.add(new SIBThread(data, k, maxIter));
        }

        SIB best = null;

        try {
            List<SIB> clusters = MulticoreExecutor.run(tasks);
            best = clusters.get(0);
            for (int i = 1; i < runs; i++) {
                SIB sib = clusters.get(i);
                if (sib.distortion < best.distortion) {
                    best = sib;
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to run Sequential Information Bottleneck on multi-core", ex);

            best = new SIB(data, k, maxIter);
            for (int i = 1; i < runs; i++) {
                SIB sib = new SIB(data, k, maxIter);
                if (sib.distortion < best.distortion) {
                    best = sib;
                }
            }            
        }

        this.k = best.k;
        this.distortion = best.distortion;
        this.centroids = best.centroids;
        this.y = best.y;
        this.size = best.size;
    }
    
    /**
     * Adapter for running SIB algorithm in thread pool.
     */
    static class SIBThread implements Callable<SIB> {
        double[][] data = null;
        SparseDataset sparse = null;
        final int k;
        final int maxIter;

        SIBThread(double[][] data, int k, int maxIter) {
            this.data = data;
            this.k = k;
            this.maxIter = maxIter;
        }

        SIBThread(SparseDataset sparse, int k, int maxIter) {
            this.sparse = sparse;
            this.k = k;
            this.maxIter = maxIter;
        }

        @Override
        public SIB call() {
            if (data != null) {
                return new SIB(data, k, maxIter);
            } else {
                return new SIB(sparse, k, maxIter);
            }
        }
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label.
     */
    @Override
    public int predict(double[] x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        for (int i = 0; i < k; i++) {
            double dist = Math.JensenShannonDivergence(x, centroids[i]);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = i;
            }
        }

        return bestCluster;
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label.
     */
    public int predict(SparseArray x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        for (int i = 0; i < k; i++) {
            double dist = Math.JensenShannonDivergence(x, centroids[i]);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = i;
            }
        }

        return bestCluster;
    }

    /**
     * Returns the distortion.
     */
    public double distortion() {
        return distortion;
    }

    /**
     * Returns the centroids.
     */
    public double[][] centroids() {
        return centroids;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("Sequential Information Bottleneck distortion: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points of dimension %d:%n", y.length, centroids[0].length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }
        
        return sb.toString();
    }
}
