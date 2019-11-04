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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;
import smile.math.MathEx;

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
public class DENCLUE extends PartitionClustering {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DENCLUE.class);

    /**
     * The epsilon of finishing density attractor searching.
     */
    private static final double eps = 1E-7;

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
    public final double[][] attractors;
    /**
     * The radius of density attractor.
     */
    private double[] radius;
    /**
     * The samples decided by NeuralGas used in the iterations of hill climbing.
     */
    private double[][] samples;

    /**
     * Constructor.
     * @param attractors the density attractor of each cluster.
     * @param y the cluster labels.
     */
    public DENCLUE(double[][] attractors, int[] y) {
        super(attractors.length, y);
        this.attractors = attractors;
    }

    /**
     * Clustering data.
     * @param data the input data of which each row is an observation.
     * @param sigma the smooth parameter in the Gaussian kernel. The user can
     * choose sigma such that number of density attractors is constant for a
     * long interval of sigma.
     * @param m the number of selected samples used in the iteration.
     * This number should be much smaller than the number of data points
     * to speed up the algorithm. It should also be large enough to capture
     * the sufficient information of underlying distribution.
     */
    public static DENCLUE fit(double[][] data, double sigma, int m) {
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }
        
        if (m <= 0) {
            throw new IllegalArgumentException("Invalid number of selected samples: " + m);
        }
        
        if (m < 10) {
            throw new IllegalArgumentException("The number of selected samples is too small: " + m);
        }
        
        double gamma = -0.5 / (sigma * sigma);
        
        KMeans kmeans = KMeans.fit(data, m);
        double[][] samples = kmeans.centroids;

        int n = data.length;
        int d = data[0].length;

        double[][] attractors = new double[n][];
        for (int i = 0; i < n; i++) {
            attractors[i] = data[i].clone();
        }

        double[] attractor = new double[d];
        double[] prob = new double[n];
        double[] radius = new double[n];

        IntStream.range(0, n).parallel().forEach(i -> {
            double diff = 1.0;
            while (diff > eps) {
                double weight = 0.0;
                for (int j = 0; j < m; j++) {
                    double w = Math.exp(gamma * MathEx.squaredDistance(attractors[i], samples[j]));
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
                    radius[i] = 2 * MathEx.distance(attractors[i], attractor);
                }

                System.arraycopy(attractor, 0, attractors[i], 0, d);
                Arrays.fill(attractor, 0.0);
            }
        });

        int[] y = new int[n];
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
                if (MathEx.distance(attractors[i], cluster.get(j)) < radius[i] + step.get(j)) {
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

        int k = cluster.size();
        return new DENCLUE(cluster.toArray(new double[k][]), y);
    }

    /**
     * Returns the smooth (standard deviation) parameter in the Gaussian kernel.
     * @return the smooth (standard deviation) parameter in the Gaussian kernel.
     */
    public double getSigma() {
        return sigma;
    }

    /**
     * Returns the density attractors of cluster.
     */
    public double[][] getDensityAttractors() {
        return attractors;
    }

    /**
     * Classifies a new observation.
     * @param x a new observation.
     * @return the cluster label. Note that it may be {@link #OUTLIER}.
     */
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
                double w = Math.exp(gamma * MathEx.squaredDistance(samples[i], z));
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
                step = 2 * MathEx.distance(attractor, z);
            }

            for (int j = 0; j < p; j++) {
                z[j] = attractor[j];
                attractor[j] = 0;
            }
        }

        for (int i = 0; i < k; i++) {
            if (MathEx.distance(attractors[i], z) < radius[i] + step) {
                return i;
            }
        }

        return OUTLIER;
    }
}
