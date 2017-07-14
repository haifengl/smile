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
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.PowerIteration;
import smile.util.MulticoreExecutor;

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
public class DeterministicAnnealing extends KMeans implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DeterministicAnnealing.class);

    /**
     * Annealing parameter in (0, 1).
     */
    private double alpha;
    /**
     * Parallel computing of update procedure.
     */
    private transient List<UpdateThread> tasks = null;
    /**
     * Parallel computing of centroids.
     */
    private transient List<CentroidThread> ctasks = null;
    
    /**
     * Constructor. Clustering data into k clusters.
     * @param data the input data of which each row is a sample.
     * @param Kmax the maximum number of clusters.
     */
    public DeterministicAnnealing(double[][] data, int Kmax) {
        this(data, Kmax, 0.9);
    }

    /**
     * Constructor. Clustering data into k clusters.
     * @param data the input data of which each row is a sample.
     * @param Kmax the maximum number of clusters.
     * @param alpha the temperature T is decreasing as T = T * alpha. alpha has
     * to be in (0, 1).
     */
    public DeterministicAnnealing(double[][] data, int Kmax, double alpha) {
        if (alpha <= 0 || alpha >= 1.0) {
            throw new IllegalArgumentException("Invalid alpha: " + alpha);
        }

        this.alpha = alpha;
        
        int n = data.length;
        int d = data[0].length;

        centroids = new double[2 * Kmax][d];
        double[][] posteriori = new double[n][2 * Kmax];
        double[] priori = new double[2 * Kmax];

        int np = MulticoreExecutor.getThreadPoolSize();
        if (n >= 1000 && np >= 2) {
            tasks = new ArrayList<>(np + 1);
            int step = n / np;
            if (step < 100) {
                step = 100;
            }

            int start = 0;
            int end = step;
            for (int i = 0; i < np-1; i++) {
                tasks.add(new UpdateThread(data, centroids, posteriori, priori, start, end));
                start += step;
                end += step;
            }
            tasks.add(new UpdateThread(data, centroids, posteriori, priori, start, n));
            
            ctasks = new ArrayList<>(2 * Kmax);
            for (int i = 0; i < 2*Kmax; i++) {
                ctasks.add(new CentroidThread(data, centroids, posteriori, priori, i));
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                centroids[0][j] += data[i][j];
            }
        }

        for (int i = 0; i < d; i++) {
            centroids[0][i] /= n;
            centroids[1][i] = centroids[0][i] * 1.01;
        }

        priori[0] = priori[1] = 0.5;

        DenseMatrix cov = Matrix.newInstance(Math.cov(data, centroids[0]));
        double[] ev = new double[d];
        Arrays.fill(ev, 1.0);
        double lambda = PowerIteration.eigen(cov, ev, 1E-4);
        double T = 2.0 * lambda + 0.01;
        
        k = 2;

        boolean stop = false;
        boolean split = false;
        while (!stop) {
            update(data, T, k, centroids, posteriori, priori);

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

                if (norm > 1E-2) {
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
        y = new int[n];
        distortion = 0.0;
        for (int i = 0; i < n; i++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < k; j += 2) {
                double dist = Math.squaredDistance(data[i], centroids[j]);
                if (nearest > dist) {
                    y[i] = j / 2;
                    nearest = dist;
                }
            }
            distortion += nearest;
        }

        size = new int[k];
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
    }
    
    /**
     * Returns the annealing parameter.
     * @return the annealing parameter
     */
    public double getAlpha() {
    	return alpha;
    }
    /**
     *  Update the system at a given temperature until convergence.
     */
    private double update(double[][] data, double T, int k, double[][] centroids, double[][] posteriori, double[] priori) {
        int n = data.length;
        int d = data[0].length;

        double D = 0.0;
        double H = 0.0;

        int iter = 0;
        double currentDistortion = Double.MAX_VALUE;
        double newDistortion = Double.MAX_VALUE / 2;
        while (iter < 100 && currentDistortion > newDistortion) {
            currentDistortion = newDistortion;

            D = Double.NaN;
            H = 0.0;

            if (tasks != null) {
                try {
                    D = 0.0;
                    for (UpdateThread t : tasks) {
                        t.k = k;
                        t.T = T;
                    }
                    
                    for (UpdateThread t : MulticoreExecutor.run(tasks)) {
                        D += t.D;
                        H += t.H;
                    }
                } catch (Exception ex) {
                    logger.error("Failed to run Deterministic Annealing on multi-core", ex);

                    D = Double.NaN;
                }
            }

            if (Double.isNaN(D)) {
                D = 0.0;
                double[] dist = new double[k];

                for (int i = 0; i < n; i++) {
                    double p = 0.0;

                    for (int j = 0; j < k; j++) {
                        dist[j] = Math.squaredDistance(data[i], centroids[j]);
                        posteriori[i][j] = priori[j] * Math.exp(-dist[j] / T);
                        p += posteriori[i][j];
                    }

                    double r = 0.0;
                    for (int j = 0; j < k; j++) {
                        posteriori[i][j] /= p;
                        D += posteriori[i][j] * dist[j];
                        r += -posteriori[i][j] * Math.log(posteriori[i][j]);
                    }
                    H += r;
                }
            }
            
            for (int i = 0; i < k; i++) {
                priori[i] = 0;
                for (int j = 0; j < n; j++) {
                    priori[i] += posteriori[j][i];
                }
                priori[i] /= n;
            }

            boolean parallel = false;
            if (ctasks != null) {
                try {
                    for (CentroidThread t : ctasks) {
                        t.k = k;
                    }
                    
                    MulticoreExecutor.run(ctasks);
                    parallel = true;
                } catch (Exception ex) {
                    logger.error("Failed to run Deterministic Annealing on multi-core", ex);

                    parallel = false;
                }
            }

            if (!parallel) {
                for (int i = 0; i < k; i++) {
                    Arrays.fill(centroids[i], 0.0);
                    for (int j = 0; j < d; j++) {
                        for (int m = 0; m < n; m++) {
                            centroids[i][j] += data[m][j] * posteriori[m][i];
                        }
                        centroids[i][j] /= (n * priori[i]);
                    }
                }
            }

            newDistortion = D - T * H;
            iter++;
        }

        logger.info(String.format("Deterministic Annealing clustering entropy after %3d iterations at temperature %.4f and k = %d: %.5f (soft distortion = %.5f )%n", iter, T, k / 2, H, D));

        return currentDistortion;
    }

    /**
     * Adapter for running update procedure in thread pool.
     */
    class UpdateThread implements Callable<UpdateThread> {

        /**
         * The start index of data portion for this task.
         */
        final int start;
        /**
         * The end index of data portion for this task.
         */
        final int end;
        final double[][] data;
        final double[][] centroids;
        int k;
        double T;
        double D;
        double H;
        double[][] posteriori;
        double[] priori;
        double[] dist;

        UpdateThread(double[][] data, double[][] centroids, double[][] posteriori, double[] priori, int start, int end) {
            this.data = data;
            this.centroids = centroids;
            this.posteriori = posteriori;
            this.priori = priori;
            this.start = start;
            this.end = end;
            dist = new double[centroids.length];
        }

        @Override
        public UpdateThread call() {
            D = 0.0;
            H = 0.0;
            for (int i = start; i < end; i++) {
                double p = 0.0;

                for (int j = 0; j < k; j++) {
                    dist[j] = Math.squaredDistance(data[i], centroids[j]);
                    posteriori[i][j] = priori[j] * Math.exp(-dist[j] / T);
                    p += posteriori[i][j];
                }

                double r = 0.0;
                for (int j = 0; j < k; j++) {
                    posteriori[i][j] /= p;
                    D += posteriori[i][j] * dist[j];
                    r += -posteriori[i][j] * Math.log(posteriori[i][j]);
                }
                H += r;
            }

            
            return this;
        }
    }

    /**
     * Adapter for running update procedure in thread pool.
     */
    class CentroidThread implements Callable<CentroidThread> {

        /**
         * The index of centroid to compute.
         */
        final int i;
        final double[][] data;
        int k;
        double[][] centroids;
        double[][] posteriori;
        double[] priori;

        CentroidThread(double[][] data, double[][] centroids, double[][] posteriori, double[] priori, int i) {
            this.data = data;
            this.centroids = centroids;
            this.posteriori = posteriori;
            this.priori = priori;
            this.i = i;
        }

        @Override
        public CentroidThread call() {
            if (i < k) {
                int n = data.length;
                int d = data[0].length;
                Arrays.fill(centroids[i], 0.0);
                for (int j = 0; j < d; j++) {
                    for (int m = 0; m < n; m++) {
                        centroids[i][j] += data[m][j] * posteriori[m][i];
                    }
                    centroids[i][j] /= (n * priori[i]);
                }
            }
            
            return this;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("Deterministic Annealing clustering distortion: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        return sb.toString();
    }
}
