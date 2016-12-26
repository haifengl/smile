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
import smile.util.MulticoreExecutor;

/**
 * DENsity CLUstering. The DENCLUE algorithm employs a cluster model based on
 * kernel density estimation. A cluster is defined by a local maximum of the
 * estimated density function. Data points going to the same local maximum
 * are put into the same cluster.
 * <p>
 * Clearly, DENCLUE doesn't work on data with uniform distribution. In high
 * dimensional space, the data always look like uniformly distributed because
 * of the curse of dimensionality. Therefore, DENCLUDE doesn't work well on
 * high-dimensional data in general.
 *
 * <h2>References</h2>
 * <ol>
 * <li> A. Hinneburg and D. A. Keim. A general approach to clustering in large databases with noise. Knowledge and Information Systems, 5(4):387-415, 2003.</li>
 * <li> Alexander Hinneburg and Hans-Henning Gabriel. DENCLUE 2.0: Fast Clustering based on Kernel Density Estimation. IDA, 2007.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class DENCLUE extends PartitionClustering<double[]> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DENCLUE.class);

    /**
     * The epsilon of finishing density attractor searching.
     */
    private double eps = 1E-7;
    /**
     * The smooth parameter in the Gaussian kernel. The user can
     * choose sigma such that number of density attractors is constant for a
     * long interval of sigma.
     */
    private double sigma;
    /**
     * The smooth parameter in the Gaussian kernel. It is -0.5 / (sigma * sigma).
     */
    private double gamma;
    /**
     * The density attractor of each cluster.
     */
    private double[][] attractors;
    /**
     * The radius of density attractor.
     */
    private double[] radius;
    /**
     * The samples decided by NeuralGas used in the iterations of hill climbing.
     */
    private double[][] samples;

    /**
     * Constructor. Clustering data.
     * @param data the dataset for clustering.
     * @param sigma the smooth parameter in the Gaussian kernel. The user can
     * choose sigma such that number of density attractors is constant for a
     * long interval of sigma.
     * @param m the number of selected samples used in the iteration.
     * This number should be much smaller than the number of data points
     * to speed up the algorithm. It should also be large enough to capture
     * the sufficient information of underlying distribution.
     */
    public DENCLUE(double[][] data, double sigma, int m) {
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }
        
        if (m <= 0) {
            throw new IllegalArgumentException("Invalid number of selected samples: " + m);
        }
        
        if (m < 10) {
            throw new IllegalArgumentException("The number of selected samples is too small: " + m);
        }
        
        this.sigma = sigma;
        this.gamma = -0.5 / (sigma * sigma);
        
        KMeans kmeans = new KMeans(data, m);
        samples = kmeans.centroids();

        int n = data.length;
        int d = data[0].length;

        attractors = new double[n][];
        for (int i = 0; i < n; i++) {
            attractors[i] = data[i].clone();
        }

        double[] attractor = new double[d];
        double[] prob = new double[n];
        radius = new double[n];

        int np = MulticoreExecutor.getThreadPoolSize();
        List<DENCLUEThread> tasks = null;
        if (n >= 1000 && np >= 2) {
            tasks = new ArrayList<>(np + 1);
            int step = n / np;
            if (step < 100) {
                step = 100;
            }

            int start = 0;
            int end = step;
            for (int i = 0; i < np-1; i++) {
                tasks.add(new DENCLUEThread(prob, start, end));
                start += step;
                end += step;
            }
            
            tasks.add(new DENCLUEThread(prob, start, n));
            
            try {
                MulticoreExecutor.run(tasks);
            } catch (Exception ex) {
                logger.error("Failed to run DENCLUE on multi-core", ex);
                for (DENCLUEThread task : tasks) {
                    task.call();
                }
            }
        } else {

            for (int i = 0; i < n; i++) {
                double diff = 1.0;
                while (diff > eps) {
                    double weight = 0.0;
                    for (int j = 0; j < m; j++) {
                        double w = Math.exp(gamma * Math.squaredDistance(attractors[i], samples[j]));
                        weight += w;
                        for (int l = 0; l < d; l++) {
                            attractor[l] += w * samples[j][l];
                        }
                    }

                    for (int l = 0; l < d; l++) {
                        attractor[l] /= weight;
                    }

                    weight /= m;
                    diff = weight - prob[i];
                    prob[i] = weight;

                    if (diff > 1E-5) {
                        radius[i] = 2 * Math.distance(attractors[i], attractor);
                    }

                    System.arraycopy(attractor, 0, attractors[i], 0, d);
                    Arrays.fill(attractor, 0.0);
                }
            }
        }

        y = new int[n];
        ArrayList<double[]> cluster = new ArrayList<>();
        ArrayList<Double> probability = new ArrayList<>();
        ArrayList<Double> step = new ArrayList<>();

        y[0] = 0;
        cluster.add(attractors[0]);
        probability.add(prob[0]);
        step.add(radius[0]);

        boolean newcluster = true;
        for (int i = 1; i < n; i++) {
            newcluster = true;
            for (int j = 0; j < cluster.size(); j++) {
                if (Math.distance(attractors[i], cluster.get(j)) < radius[i] + step.get(j)) {
                    y[i] = j;
                    newcluster = false;
                    if (prob[i] > probability.get(j)) {
                        cluster.set(j, attractors[i]);
                        probability.set(j, prob[i]);
                        step.set(j, radius[i]);
                    }
                    break;
                }
            }

            if (newcluster) {
                y[i] = cluster.size();
                cluster.add(attractors[i]);
                probability.add(prob[i]);
                step.add(radius[i]);
            }
        }

        size = new int[cluster.size()];
        for (int i = 0; i < n; i++) {
            size[y[i]]++;
        }

        k = cluster.size();
        attractors = new double[k][];
        for (int i = 0; i < k; i++) {
            attractors[i] = cluster.get(i);
        }
    }

    /**
     * Returns the smooth (standard deviation) parameter in the Gaussian kernel.
     * @return the smooth (standard deviation) parameter in the Gaussian kernel.
     */
    public double getSigma() {
    	return sigma;
    }
    
    /**
     * Adapter for running DENCLUE algorithm in thread pool.
     */
    class DENCLUEThread implements Callable<DENCLUEThread> {

        /**
         * The start index of data portion for this task.
         */
        final int start;
        /**
         * The end index of data portion for this task.
         */
        final int end;
        double[] attractor;
        double[] prob;

        DENCLUEThread(double[] prob, int start, int end) {
            this.prob = prob;
            this.start = start;
            this.end = end;
            attractor = new double[samples[0].length];
        }

        @Override
        public DENCLUEThread call() {
            int m = samples.length;
            int d = samples[0].length;
            for (int i = start; i < end; i++) {
                double diff = 1.0;
                while (diff > eps) {
                    double weight = 0.0;
                    for (int j = 0; j < m; j++) {
                        double w = Math.exp(gamma * Math.squaredDistance(attractors[i], samples[j]));
                        weight += w;
                        for (int l = 0; l < d; l++) {
                            attractor[l] += w * samples[j][l];
                        }
                    }

                    for (int l = 0; l < d; l++) {
                        attractor[l] /= weight;
                    }

                    weight /= m;
                    diff = weight - prob[i];
                    prob[i] = weight;

                    if (diff > 1E-5) {
                        radius[i] = 2 * Math.distance(attractors[i], attractor);
                    }

                    System.arraycopy(attractor, 0, attractors[i], 0, d);
                    Arrays.fill(attractor, 0.0);
                }
            }
            
            return this;
        }
    }

    /**
     * Returns the density attractors of cluster.
     */
    public double[][] getDensityAttractors() {
        return attractors;
    }

    @Override
    public int predict(double[] x) {
        int p = attractors[0].length;
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double prob = 0.0;
        double diff = 1.0;

        double step = 0.0;
        double[] z = x.clone();
        double[] attractor = new double[p];
        while (diff > eps) {
            double weight = 0.0;
            for (int i = 0; i < samples.length; i++) {
                double w = Math.exp(gamma * Math.squaredDistance(samples[i], z));
                weight += w;
                for (int j = 0; j < p; j++) {
                    attractor[j] += w * samples[i][j];
                }
            }

            for (int j = 0; j < p; j++) {
                attractor[j] /= weight;
            }

            weight /= k;
            diff = weight - prob;
            prob = weight;

            if (diff > 1E-5) {
                step = 2 * Math.distance(attractor, z);
            }

            for (int j = 0; j < p; j++) {
                z[j] = attractor[j];
                attractor[j] = 0;
            }
        }

        for (int i = 0; i < k; i++) {
            if (Math.distance(attractors[i], z) < radius[i] + step) {
                return i;
            }
        }

        return OUTLIER;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("DENCLUE clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }

        return sb.toString();
    }
}
