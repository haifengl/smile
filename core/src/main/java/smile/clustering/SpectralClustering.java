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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

/**
 * Spectral Clustering. Given a set of data points, the similarity matrix may
 * be defined as a matrix S where S<sub>ij</sub> represents a measure of the
 * similarity between points. Spectral clustering techniques make use of the
 * spectrum of the similarity matrix of the data to perform dimensionality
 * reduction for clustering in fewer dimensions. Then the clustering will
 * be performed in the dimension-reduce space, in which clusters of non-convex
 * shape may become tight. There are some intriguing similarities between
 * spectral clustering methods and kernel PCA, which has been empirically
 * observed to perform clustering.
 *
 * <h2>References</h2>
 * <ol>
 * <li> A.Y. Ng, M.I. Jordan, and Y. Weiss. On Spectral Clustering: Analysis and an algorithm. NIPS, 2001. </li>
 * <li> Marina Maila and Jianbo Shi. Learning segmentation by random walks. NIPS, 2000. </li>
 * <li> Deepak Verma and Marina Meila. A Comparison of Spectral Clustering Algorithms. 2003. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class SpectralClustering implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SpectralClustering.class);

    /**
     * The number of clusters.
     */
    private int k;
    /**
     * The cluster labels of data.
     */
    private int[] y;
    /**
     * The number of samples in each cluster.
     */
    private int[] size;
    /**
     * The width of Gaussian kernel.
     */
    private double sigma;
    /**
     * The distortion in feature space.
     */
    private double distortion;

    /**
     * Constructor. Spectral graph clustering.
     * @param W the adjacency matrix of graph.
     * @param k the number of clusters.
     */
    public SpectralClustering(double[][] W, int k) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        this.k = k;        
        int n = W.length;
        
        for (int i = 0; i < n; i++) {
            if (W[i].length != n) {
                throw new IllegalArgumentException("The adjacency matrix is not square.");
            }
            
            if (W[i][i] != 0.0) {
                throw new IllegalArgumentException(String.format("Vertex %d has self loop: ", i));
            }
            
            for (int j = 0; j < i; j++) {
                if (W[i][j] != W[j][i]) {
                    throw new IllegalArgumentException("The adjacency matrix is not symmetric.");                    
                }
                
                if (W[i][j] < 0.0) {
                    throw new IllegalArgumentException("Negative entry of adjacency matrix: " + W[i][j]);                    
                }
            }
        }
        
        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                D[i] += W[i][j];
            }
            
            if (D[i] == 0.0) {
                throw new IllegalArgumentException("Isolated vertex: " + i);                    
            }
            
            D[i] = 1.0 / Math.sqrt(D[i]);
        }

        DenseMatrix L = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double l = D[i] * W[i][j] * D[j];
                L.set(i, j, l);
                L.set(j, i, l);
            }
        }

        L.setSymmetric(true);
        EVD eigen = L.eigen(k);
        double[][] Y = eigen.getEigenVectors().array();
        for (int i = 0; i < n; i++) {
            Math.unitize2(Y[i]);
        }

        KMeans kmeans = new KMeans(Y, k);
        distortion = kmeans.distortion;
        y = kmeans.getClusterLabel();
        size = kmeans.getClusterSize();
    }

    /**
     * Constructor. Spectral clustering the data.
     * @param data the dataset for clustering.
     * @param k the number of clusters.
     * @param sigma the smooth/width parameter of Gaussian kernel, which
     * is a somewhat sensitive parameter. To search for the best setting,
     * one may pick the value that gives the tightest clusters (smallest
     * distortion, see {@link #distortion()}) in feature space.
     */
    public SpectralClustering(double[][] data, int k, double sigma) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }

        this.k = k;
        this.sigma = sigma;

        int n = data.length;
        double gamma = -0.5 / (sigma * sigma);

        DenseMatrix W = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double w = Math.exp(gamma * Math.squaredDistance(data[i], data[j]));
                W.set(i, j, w);
                W.set(j, i, w);
            }
        }
        
        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                D[i] += W.get(i, j);
            }

            if (D[i] < 1E-5) {
                logger.error(String.format("Small D[%d] = %f. The data may contain outliers.", i, D[i]));
            }
            
            D[i] = 1.0 / Math.sqrt(D[i]);
        }

        DenseMatrix L = W;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double l = D[i] * W.get(i, j) * D[j];
                L.set(i, j, l);
                L.set(j, i, l);
            }
        }

        L.setSymmetric(true);
        EVD eigen = L.eigen(k);
        double[][] Y = eigen.getEigenVectors().array();
        for (int i = 0; i < n; i++) {
            Math.unitize2(Y[i]);
        }

        KMeans kmeans = new KMeans(Y, k);
        distortion = kmeans.distortion;
        y = kmeans.getClusterLabel();
        size = kmeans.getClusterSize();
    }

    /**
     * Constructor. Spectral clustering with Nystrom approximation.
     * @param data the dataset for clustering.
     * @param l the number of random samples for Nystrom approximation.
     * @param k the number of clusters.
     * @param sigma the smooth/width parameter of Gaussian kernel, which
     * is a somewhat sensitive parameter. To search for the best setting,
     * one may pick the value that gives the tightest clusters (smallest
     * distortion, see {@link #distortion()}) in feature space.
     */
    public SpectralClustering(double[][] data, int k, int l, double sigma) {
        if (l < k || l >= data.length) {
            throw new IllegalArgumentException("Invalid number of random samples: " + l);
        }
        
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }
        
        this.k = k;
        this.sigma = sigma;

        int n = data.length;
        double gamma = -0.5 / (sigma * sigma);

        int[] index = Math.permutate(n);
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }
        data = x;
        
        DenseMatrix C = Matrix.zeros(n, l);
        double[] D = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double w = Math.exp(gamma * Math.squaredDistance(data[i], data[j]));
                    sum += w;
                    if (j < l) {
                        C.set(i, j, w);
                    }
                }
            }
            
            if (sum < 1E-5) {
                logger.error(String.format("Small D[%d] = %f. The data may contain outliers.", i, sum));
            }
            
            D[i] = 1.0 / Math.sqrt(sum);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < l; j++) {
                C.set(i, j, D[i] * C.get(i, j) * D[j]);
            }
        }

        DenseMatrix W = Matrix.zeros(l, l);
        for (int i = 0; i < l; i++) {
            for (int j = 0; j < l; j++) {
                W.set(i, j, C.get(i, j));
            }
        }

        W.setSymmetric(true);
        EVD eigen = W.eigen(k);
        double[] e = eigen.getEigenValues();
        double scale = Math.sqrt((double)l / n);
        for (int i = 0; i < k; i++) {
            if (e[i] <= 0.0) {
                throw new IllegalStateException("Non-positive eigen value: " + e[i]);
            }
            
            e[i] = scale / e[i];
        }
        
        DenseMatrix U = eigen.getEigenVectors();
        for (int i = 0; i < l; i++) {
            for (int j = 0; j < k; j++) {
                U.mul(i, j, e[j]);
            }
        }
        
        double[][] Y = C.abmm(U).array();
        for (int i = 0; i < n; i++) {
            Math.unitize2(Y[i]);
        }

        KMeans kmeans = new KMeans(Y, k);
        distortion = kmeans.distortion;
        size = kmeans.getClusterSize();

        int[] label = kmeans.getClusterLabel();
        y = new int[n];
        for (int i = 0; i < n; i++) {
            y[index[i]] = label[i];
        }
    }
    
    /**
     * Returns the number of clusters.
     */
    public int getNumClusters() {
        return k;
    }

    /**
     * Returns the cluster labels of data.
     */
    public int[] getClusterLabel() {
        return y;
    }

    /**
     * Returns the size of clusters.
     */
    public int[] getClusterSize() {
        return size;
    }

    /**
     * Returns the width of Gaussian kernel.
     */
    public double getGaussianKernelWidth() {
        return sigma;
    }

    /**
     * Returns the distortion in feature space.
     */
    public double distortion() {
        return distortion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Spectral Clustering distortion in feature space: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        return sb.toString();
    }
}
