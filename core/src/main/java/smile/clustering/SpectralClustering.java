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

package smile.clustering;

import java.io.Serializable;
import java.util.stream.IntStream;

import smile.math.MathEx;
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
public class SpectralClustering extends PartitionClustering implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SpectralClustering.class);

    /**
     * The distortion in feature space.
     */
    public final double distortion;

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param k the number of clusters.
     * @param y the cluster labels.
     */
    public SpectralClustering(double distortion, int k, int[] y) {
        super(k, y);
        this.distortion = distortion;
    }

    /**
     * Spectral graph clustering.
     * @param W the adjacency matrix of graph, which will be modified.
     * @param k the number of clusters.
     */
    public static SpectralClustering fit(DenseMatrix W, int k) {
        return fit(W, k, 100, 1E-4);
    }

    /**
     * Spectral graph clustering.
     * @param W the adjacency matrix of graph, which will be modified.
     * @param k the number of clusters.
     * @param maxIter the maximum number of iterations for k-means.
     * @param tol the tolerance of k-means convergence test.
     */
    public static SpectralClustering fit(DenseMatrix W, int k, int maxIter, double tol) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        int n = W.nrows();
        double[] D = W.colSums();
        for (int i = 0; i < n; i++) {
            if (D[i] == 0.0) {
                throw new IllegalArgumentException("Isolated vertex: " + i);                    
            }
            
            D[i] = 1.0 / Math.sqrt(D[i]);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double w = D[i] * W.get(i, j) * D[j];
                W.set(i, j, w);
                W.set(j, i, w);
            }
        }

        W.setSymmetric(true);
        EVD eigen = W.eigen(k);
        double[][] Y = eigen.getEigenVectors().toArray();
        for (int i = 0; i < n; i++) {
            MathEx.unitize2(Y[i]);
        }

        KMeans kmeans = KMeans.fit(Y, k, maxIter, tol);
        return new SpectralClustering(kmeans.distortion, k, kmeans.y);
    }

    /**
     * Spectral clustering the data.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param sigma the smooth/width parameter of Gaussian kernel, which is
     *              a somewhat sensitive parameter. To search for the best
     *              setting, one may pick the value that gives the tightest
     *              clusters (smallest distortion) in feature space.
     */
    public static SpectralClustering fit(double[][] data, int k, double sigma) {
        return fit(data, k, sigma, 100, 1E-4);
    }

    /**
     * Spectral clustering the data.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param sigma the smooth/width parameter of Gaussian kernel, which is
     *              a somewhat sensitive parameter. To search for the best
     *              setting, one may pick the value that gives the tightest
     *              clusters (smallest distortion) in feature space.
     * @param maxIter the maximum number of iterations for k-means.
     * @param tol the tolerance of k-means convergence test.
     */
    public static SpectralClustering fit(double[][] data, int k, double sigma, int maxIter, double tol) {
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }

        int n = data.length;
        double gamma = -0.5 / (sigma * sigma);

        DenseMatrix W = Matrix.zeros(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double w = Math.exp(gamma * MathEx.squaredDistance(data[i], data[j]));
                W.set(i, j, w);
                W.set(j, i, w);
            }
        }

        return fit(W, k, maxIter, tol);
    }

    /**
     * Spectral clustering with Nystrom approximation.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param l the number of random samples for Nystrom approximation.
     * @param sigma the smooth/width parameter of Gaussian kernel, which is
     *              a somewhat sensitive parameter. To search for the best
     *              setting, one may pick the value that gives the tightest
     *              clusters (smallest distortion) in feature space.
     */
    public static SpectralClustering fit(double[][] data, int k, int l, double sigma) {
        return fit(data, k, l, sigma, 100, 1E-4);
    }

    /**
     * Spectral clustering with Nystrom approximation.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param l the number of random samples for Nystrom approximation.
     * @param sigma the smooth/width parameter of Gaussian kernel, which is
     *              a somewhat sensitive parameter. To search for the best
     *              setting, one may pick the value that gives the tightest
     *              clusters (smallest distortion) in feature space.
     * @param maxIter the maximum number of iterations for k-means.
     * @param tol the tolerance of k-means convergence test.
     */
    public static SpectralClustering fit(double[][] data, int k, int l, double sigma, int maxIter, double tol) {
        if (l < k || l >= data.length) {
            throw new IllegalArgumentException("Invalid number of random samples: " + l);
        }
        
        if (k < 2) {
            throw new IllegalArgumentException("Invalid number of clusters: " + k);
        }

        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }
        
        int n = data.length;
        double gamma = -0.5 / (sigma * sigma);

        int[] index = MathEx.permutate(n);
        double[][] x = new double[n][];
        for (int i = 0; i < n; i++) {
            x[i] = data[index[i]];
        }

        DenseMatrix C = Matrix.zeros(n, l);
        double[] D = new double[n];

        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double w = Math.exp(gamma * MathEx.squaredDistance(x[i], x[j]));
                    D[i] += w;
                    if (j < l) {
                        C.set(i, j, w);
                    }
                }
            }
        });

        for (int i = 0; i < n; i++) {
            if (D[i] < 1E-4) {
                logger.error(String.format("Small D[%d] = %f. The data may contain outliers.", i, D[i]));
            }
            
            D[i] = 1.0 / Math.sqrt(D[i]);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < l; j++) {
                C.set(i, j, D[i] * C.get(i, j) * D[j]);
            }
        }

        DenseMatrix W = C.submat(0, 0, l, l);

        W.setSymmetric(true);
        EVD eigen = W.eigen(k);
        double[] e = eigen.getEigenValues();
        double scale = Math.sqrt((double)l / n);
        for (int i = 0; i < k; i++) {
            if (e[i] <= 1E-8) {
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
        
        double[][] Y = C.abmm(U).toArray();
        for (int i = 0; i < n; i++) {
            MathEx.unitize2(Y[i]);
        }

        KMeans kmeans = KMeans.fit(Y, k, maxIter, tol);
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            y[index[i]] = kmeans.y[i];
        }

        return new SpectralClustering(kmeans.distortion, k, y);
    }
}
