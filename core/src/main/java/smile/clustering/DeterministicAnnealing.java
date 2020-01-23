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

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.PowerIteration;

/**
 * Deterministic annealing clustering. Deterministic annealing extends
 * soft-clustering to an annealing process.
 * For each temperature value, the algorithm iterates between the calculation
 * of all posteriori probabilities and the update of the centroids vectors,
 * until convergence is reached. The annealing starts with a high temperature.
 * Here, all centroids vectors converge to the center of the pattern
 * distribution (independent of their initial positions). Below a critical
 * temperature the vectors start to split. Further decreasing the temperature
 * leads to more splittings until all centroids vectors are separate. The
 * annealing can therefore avoid (if it is sufficiently slow) the convergence
 * to local minima.
 * 
 * <h2>References</h2>
 * <ol>
 * <li> Kenneth Rose. Deterministic Annealing for Clustering, Compression, Classification, Regression, and Speech Recognition. </li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class DeterministicAnnealing extends CentroidClustering<double[], double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DeterministicAnnealing.class);

    /**
     * Constructor.
     * @param distortion the total distortion.
     * @param centroids the centroids of each cluster.
     * @param y the cluster labels.
     */
    public DeterministicAnnealing(double distortion, double[][] centroids, int[] y) {
        super(distortion, centroids, y);
    }

    @Override
    public double distance(double[] x, double[] y) {
        return MathEx.squaredDistance(x, y);
    }

    /**
     * Clustering data into k clusters.
     * @param data the input data of which each row is an observation.
     * @param Kmax the maximum number of clusters.
     */
    public static DeterministicAnnealing fit(double[][] data, int Kmax) {
        return fit(data, Kmax, 0.9, 100, 1E-4, 1E-2);
    }

    /**
     * Clustering data into k clusters.
     * @param data the input data of which each row is an observation.
     * @param Kmax the maximum number of clusters.
     * @param alpha the temperature T is decreasing as T = T * alpha.
     *              alpha has to be in (0, 1).
     * @param maxIter the maximum number of iterations at each temperature.
     * @param tol the tolerance of convergence test.
     * @param splitTol the tolerance to split a cluster.
     */
    public static DeterministicAnnealing fit(double[][] data, int Kmax, double alpha, int maxIter, double tol, double splitTol) {
        if (alpha <= 0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid alpha: " + alpha);
        }

        int n = data.length;
        int d = data[0].length;

        double[][] centroids = new double[2 * Kmax][d];
        double[][] posteriori = new double[n][2 * Kmax];
        double[] priori = new double[2 * Kmax];

        centroids[0] = MathEx.colMeans(data);
        for (int i = 0; i < d; i++) {
            centroids[1][i] = centroids[0][i] * 1.01;
        }

        priori[0] = priori[1] = 0.5;

        DenseMatrix cov = Matrix.of(MathEx.cov(data, centroids[0]));
        double[] ev = new double[d];
        Arrays.fill(ev, 1.0);
        double lambda = PowerIteration.eigen(cov, ev, 1E-4);
        double T = 2.0 * lambda + 0.01;
        
        int k = 2;

        boolean stop = false;
        boolean split = false;
        while (!stop) {
            update(data, T, k, centroids, posteriori, priori, maxIter, tol);

            if (k >= 2 * Kmax && split) {
                stop = true;
            }

            int currentK = k;
            for (int i = 0; i < currentK; i += 2) {
                double norm = 0.0;
                for (int j = 0; j < d; j++) {
                    double diff = centroids[i][j] - centroids[i + 1][j];
                    norm += diff * diff;
                }

                if (norm > splitTol) {
                    if (k < 2 * Kmax) {
                        // split the cluster to two.
                        for (int j = 0; j < d; j++) {
                            centroids[k][j] = centroids[i + 1][j];
                            centroids[k + 1][j] = centroids[i + 1][j] * 1.01;
                        }

                        priori[k] = priori[i + 1] / 2;
                        priori[k + 1] = priori[i + 1] / 2;

                        priori[i] = priori[i] / 2;
                        priori[i + 1] = priori[i] / 2;

                        k += 2;
                    }

                    if (currentK >= 2 * Kmax) {
                        split = true;
                    }
                }

                for (int j = 0; j < d; j++) {
                    centroids[i + 1][j] = centroids[i][j] * 1.01;
                }
            }

            if (split) {
                // we have reach Kmax+2 clusters. Reverse the temperature back
                // and rerun the system at high temperature for Kmax effective
                // clusters.
                T /= alpha;
            } else if (k - currentK > 2) { // too large step
                T /= alpha; // revise back and try smaller step.
                alpha += 5 * Math.pow(10, Math.log10(1 - alpha) - 1);
            } else {
                // be careful since we are close to the final Kmax.
                if (k > currentK && k == 2 * Kmax - 2) {
                    alpha += 5 * Math.pow(10, Math.log10(1 - alpha) - 1);
                }

                // decrease the temperature.
                T *= alpha;
            }

            if (alpha >= 1) {
                break;
            }
        }

        // Finish the annealing process and run the system at T = 0, i.e.
        // hard clustering.
        k = k / 2;
        double[][] centers = new double[k][];
        for (int i = 0; i < k; i++) {
            centers[i] = centroids[2*i];
        }

        int[] y = new int[n];
        double distortion = assign(y, data, centers, MathEx::squaredDistance);

        int[] size = new int[k];
        centroids = new double[k][d];
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

        return new DeterministicAnnealing(distortion, centroids, y);
    }

    /**
     *  Update the system at a given temperature until convergence.
     */
    private static double update(double[][] data, double T, int k, double[][] centroids, double[][] posteriori, double[] priori, int maxIter, double tol) {
        int n = data.length;
        int d = data[0].length;

        double distortion = Double.MAX_VALUE;
        double diff = Double.MAX_VALUE;
        for (int iter = 1; iter <= maxIter && diff > tol; iter++) {

            double D = IntStream.range(0, n).parallel().mapToDouble(i -> {
                double Z = 0.0;
                double[] p = posteriori[i];
                double[] dist = new double[k];

                for (int j = 0; j < k; j++) {
                    dist[j] = MathEx.squaredDistance(data[i], centroids[j]);
                    p[j] = priori[j] * Math.exp(-dist[j] / T);
                    Z += p[j];
                }

                double sum = 0.0;
                for (int j = 0; j < k; j++) {
                    p[j] /= Z;
                    sum += p[j] * dist[j];
                }
                return sum;
            }).sum();

            double H = IntStream.range(0, n).parallel().mapToDouble(i -> {
                double[] p = posteriori[i];
                double sum = 0.0;
                for (int j = 0; j < k; j++) {
                    sum += -p[j] * Math.log(p[j]);
                }
                return sum;
            }).sum();

            Arrays.fill(priori, 0.0);
            for (int i = 0; i < n; i++) {
                double[] p = posteriori[i];
                for (int j = 0; j < k; j++) {
                    priori[j] += p[j];
                }
            }

            for (int i = 0; i < k; i++) {
                priori[i] /= n;
            }

            IntStream.range(0, k).parallel().forEach(i -> {
                Arrays.fill(centroids[i], 0.0);
                for (int j = 0; j < d; j++) {
                    for (int m = 0; m < n; m++) {
                        centroids[i][j] += data[m][j] * posteriori[m][i];
                    }
                    centroids[i][j] /= (n * priori[i]);
                }
            });

            double DTH = D - T * H;
            diff = distortion - DTH;
            distortion = DTH;

            logger.info(String.format("Entropy after %3d iterations at temperature %.4f and k = %d: %.4f (soft distortion = %.4f)", iter, T, k / 2, H, D));
        }

        return distortion;
    }
}
