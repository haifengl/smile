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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import java.util.Arrays;
import java.util.Properties;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.EuclideanDistance;
import smile.tensor.DenseMatrix;
import smile.tensor.Eigen;
import smile.tensor.Vector;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;

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
public class DeterministicAnnealing {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DeterministicAnnealing.class);

    /** Constructor. */
    private DeterministicAnnealing() {

    }

    /**
     * Deterministic annealing hyperparameters.
     * @param kmax the maximum number of clusters.
     * @param alpha the temperature T is decreasing as T = T * alpha.
     *              alpha has to be in (0, 1).
     * @param maxIter the maximum number of iterations at each temperature.
     * @param tol the tolerance of convergence test.
     * @param splitTol the tolerance to split a cluster.
     * @param controller the optional training controller.
     */
    public record Options(int kmax, double alpha, int maxIter, double tol, double splitTol,
                          IterativeAlgorithmController<AlgoStatus> controller) {
        /** Constructor. */
        public Options {
            if (kmax < 2) {
                throw new IllegalArgumentException("Invalid number of clusters: " + kmax);
            }

            if (alpha <= 0 || alpha >= 1.0) {
                throw new IllegalArgumentException("Invalid alpha: " + alpha);
            }

            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            if (tol < 0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }

            if (splitTol < 0) {
                throw new IllegalArgumentException("Invalid split tolerance: " + splitTol);
            }
        }

        /**
         * Constructor.
         * @param kmax the maximum number of clusters.
         * @param alpha the temperature T is decreasing as T = T * alpha.
         *              alpha has to be in (0, 1).
         * @param maxIter the maximum number of iterations at each temperature.
         */
        public Options(int kmax, double alpha, int maxIter) {
            this(kmax, alpha, maxIter, 1E-4, 1E-2, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.deterministic_annealing.k", Integer.toString(kmax));
            props.setProperty("smile.deterministic_annealing.alpha", Double.toString(alpha));
            props.setProperty("smile.deterministic_annealing.iterations", Integer.toString(maxIter));
            props.setProperty("smile.deterministic_annealing.tolerance", Double.toString(tol));
            props.setProperty("smile.deterministic_annealing.split_tolerance", Double.toString(splitTol));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int kmax = Integer.parseInt(props.getProperty("smile.deterministic_annealing.k", "2"));
            double alpha = Double.parseDouble(props.getProperty("smile.deterministic_annealing.alpha", "0.9"));
            int maxIter = Integer.parseInt(props.getProperty("smile.deterministic_annealing.iterations", "100"));
            double tol = Double.parseDouble(props.getProperty("smile.deterministic_annealing.tolerance", "1E-4"));
            double splitTol = Double.parseDouble(props.getProperty("smile.deterministic_annealing.split_tolerance", "1E-2"));
            return new Options(kmax, alpha, maxIter, tol, splitTol, null);
        }
    }

    /**
     * Clustering data into k clusters.
     * @param data the input data of which each row is an observation.
     * @param kmax the maximum number of clusters.
     * @param alpha the temperature T is decreasing as T = T * alpha.
     *              alpha has to be in (0, 1).
     * @param maxIter the maximum number of iterations at each temperature.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, int kmax, double alpha, int maxIter) {
        return fit(data, new Options(kmax, alpha, maxIter));
    }

    /**
     * Clustering data into k clusters.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Options options) {
        int kmax = options.kmax;
        double alpha = options.alpha;
        double splitTol = options.splitTol;
        var controller = options.controller();
        int n = data.length;
        int d = data[0].length;

        double[][] centroids = new double[2 * kmax][d];
        double[][] posteriori = new double[n][2 * kmax];
        double[] priori = new double[2 * kmax];
        priori[0] = priori[1] = 0.5;

        centroids[0] = MathEx.colMeans(data);
        for (int i = 0; i < d; i++) {
            centroids[1][i] = centroids[0][i] * 1.01;
        }

        DenseMatrix cov = DenseMatrix.of(MathEx.cov(data, centroids[0]));
        Vector ev = cov.vector(d);
        ev.fill(1.0);
        double lambda = Eigen.power(cov, ev, 0.0f, 1E-4, Math.max(20, 2 * cov.nrow()));
        double T = 2.0 * lambda + 0.01;
        
        int k = 2;
        boolean stop = false;
        boolean split = false;
        while (!stop) {
            double distortion = update(data, T, k, centroids, posteriori, priori, options.maxIter, options.tol);

            if (k >= 2 * kmax && split) {
                stop = true;
            }

            if (controller != null) {
                controller.submit(new AlgoStatus(k/2, distortion, T));
                if (controller.isInterrupted()) stop = true;
            }

            int currentK = k;
            for (int i = 0; i < currentK; i += 2) {
                double norm = 0.0;
                for (int j = 0; j < d; j++) {
                    double diff = centroids[i][j] - centroids[i + 1][j];
                    norm += diff * diff;
                }

                if (norm > splitTol) {
                    if (k < 2 * kmax) {
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

                    if (currentK >= 2 * kmax) {
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
                if (k > currentK && k == 2 * kmax - 2) {
                    alpha += 5 * Math.pow(10, Math.log10(1 - alpha) - 1);
                }

                // decrease the temperature.
                T *= alpha;
            }

            if (alpha >= 1) {
                break;
            }
        }

        // Finish the annealing process and run the system at T = 0,
        // i.e. hard clustering.
        k = k / 2;
        double[][] centers = new double[k][];
        for (int i = 0; i < k; i++) {
            centers[i] = centroids[2*i];
        }

        int[] size = new int[k];
        int[] group = new int[n];
        final int numClusters = k;
        IntStream.range(0, n).parallel().forEach(i -> {
            int cluster = -1;
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < numClusters; j++) {
                double dist = MathEx.squaredDistance(centers[j], data[i]);
                if (nearest > dist) {
                    nearest = dist;
                    cluster = j;
                }
            }

            group[i] = cluster;
            size[cluster]++;
        });

        IntStream.range(0, k).parallel().forEach(cluster -> {
            var center = centers[cluster];
            Arrays.fill(center, 0.0);
            for (int i = 0; i < n; i++) {
                if (group[i] == cluster) {
                    for (int j = 0; j < d; j++) {
                        center[j] += data[i][j];
                    }
                }
            }

            for (int j = 0; j < d; j++) {
                center[j] /= size[cluster];
            }
        });

        double[] proximity = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            proximity[i] = MathEx.squaredDistance(centers[group[i]], data[i]);
        });

        ToDoubleBiFunction<double[], double[]> distance = new EuclideanDistance();
        return new CentroidClustering<>("D.Annealing", centers, distance, group, proximity);
    }

    /**
     *  Update the system at a given temperature until convergence.
     */
    private static double update(double[][] data, double T, int k, double[][] centroids,
                                 double[][] posteriori, double[] priori, int maxIter, double tol) {
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

            logger.info("Iterations {}: k = {}, temperature = {}, entropy = {}, soft distortion = {}", iter, k/2, T, H, D);
        }

        return distortion;
    }
}
