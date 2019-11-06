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
import smile.math.distance.EuclideanDistance;
import smile.neighbor.LinearSearch;

/**
 * DENsity CLUstering. The DENCLUE algorithm employs a cluster model based on
 * kernel density estimation. A cluster is defined by a local maximum of the
 * estimated density function. Observations going to the same local maximum
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
     * The tolerance of hill-climbing procedure.
     */
    private double tol;
    /**
     * The smooth parameter in the Gaussian kernel.
     */
    private double sigma;
    /**
     * The density attractor of each observation.
     */
    public final double[][] attractors;
    /**
     * The radius of density attractor.
     */
    private double[] radius;
    /**
     * The samples decided by k-means used in the iterations of hill climbing.
     */
    private double[][] samples;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param attractors the density attractor of each observation.
     * @param radius the radius of density attractor.
     * @param sigma the smooth parameter in the Gaussian kernel. The user can
     *              choose sigma such that number of density attractors is
     *              constant for a long interval of sigma.
     * @param y the cluster labels.
     * @param tol the tolerance of hill-climbing procedure.
     */
    public DENCLUE(int k, double[][] attractors, double[] radius, double[][] samples, double sigma, int[] y, double tol) {
        super(k, y);
        this.attractors = attractors;
        this.radius = radius;
        this.samples = samples;
        this.sigma = sigma;
        this.tol = tol;
    }

    /**
     * Clustering data.
     * @param data the input data of which each row is an observation.
     * @param sigma the smooth parameter in the Gaussian kernel. The user can
     *              choose sigma such that number of density attractors is
     *              constant for a long interval of sigma.
     * @param m the number of selected samples used in the iteration.
     *          This number should be much smaller than the number of
     *          observations to speed up the algorithm. It should also be
     *          large enough to capture the sufficient information of
     *          underlying distribution.
     */
    public static DENCLUE fit(double[][] data, double sigma, int m) {
        int n = data.length;
        return fit(data, sigma, m, 1E-2, Math.max(10, n/200));
    }

    /**
     * Clustering data.
     * @param data the input data of which each row is an observation.
     * @param sigma the smooth parameter in the Gaussian kernel. The user can
     *              choose sigma such that number of density attractors is
     *              constant for a long interval of sigma.
     * @param m the number of selected samples used in the iteration.
     *          This number should be much smaller than the number of
     *          observations to speed up the algorithm. It should also be
     *          large enough to capture the sufficient information of
     *          underlying distribution.
     * @param tol the tolerance of hill-climbing procedure.
     * @param minPts the minimum number of neighbors for a core attractor.
     */
    public static DENCLUE fit(double[][] data, double sigma, int m, double tol, int minPts) {
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("Invalid standard deviation of Gaussian kernel: " + sigma);
        }
        
        if (m <= 0 || m > data.length) {
            throw new IllegalArgumentException("Invalid number of selected samples: " + m);
        }

        logger.info("Select {} samples by k-means", m);
        KMeans kmeans = KMeans.fit(data, m);
        double[][] samples = kmeans.centroids;

        int n = data.length;
        int d = data[0].length;
        double[][] attractors = new double[n][d];
        double[][] steps = new double[n][2];

        logger.info("Hill-climbing of density function for each observation");
        IntStream.range(0, n).parallel().mapToDouble(i -> climb(data[i], attractors[i], steps[i], samples, sigma, tol)).toArray();

        double[] radius = Arrays.stream(steps).mapToDouble(step -> step[0] + step[1]).toArray();
        double r = MathEx.mean(radius);

        logger.info("Clustering attractors with DBSCAN (radius = {})", r);
        DBSCAN<double[]> dbscan = DBSCAN.fit(attractors, minPts, r);

        return new DENCLUE(dbscan.k, attractors, radius, samples, sigma, dbscan.y, tol);
    }

    /**
     * Classifies a new observation.
     * @param x a new observation.
     * @return the cluster label. Note that it may be {@link #OUTLIER}.
     */
    public int predict(double[] x) {
        int d = attractors[0].length;
        if (x.length != d) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, d));
        }

        double[] attractor = new double[d];
        double[] step = new double[2];
        climb(x, attractor, step, samples, sigma, tol);

        double r = step[0] + step[1];
        for (int i = 0; i < attractors.length; i++) {
            if (MathEx.distance(attractors[i], attractor) < radius[i] + r) {
                return y[i];
            }
        }

        return OUTLIER;
    }

    /**
     * The hill-climbing is started for each observation, which assigns the
     * observation to a local maxima (attractor).
     * @param x the observation to start with.
     * @param attractor the local maxima on output.
     * @param step the last k step sizes.
     * @param samples the samples used in kernel density estimation.
     * @param sigma the bandwidth of Gaussian kernel.
     * @param tol the tolerance of convergence test.
     * @return the radius of attractor, which is the sum of last 2 steps.
     */
    private static double climb(double[] x, double[] attractor, double[] step, double[][] samples, double sigma, double tol) {
        int m = samples.length;
        int d = x.length;
        int k = step.length;

        double p = 1.0;
        double h = Math.pow(2 * Math.PI * sigma, d/2.0);
        double gamma = -0.5 / (sigma * sigma);

        // Don't overwrite the origin observation
        x = x.clone();
        double[] w = new double[m];

        double diff = Double.MAX_VALUE;
        for (int iter = 0; iter < k || diff > tol; iter++) {
            for (int i = 0; i < m; i++) {
                w[i] = Math.exp(gamma * MathEx.squaredDistance(x, samples[i]));
            }

            Arrays.fill(attractor, 0.0);
            for (int i = 0; i < m; i++) {
                double wi = w[i];
                double[] xi = samples[i];
                for (int j = 0; j < d; j++) {
                    attractor[j] += wi * xi[j];
                }
            }

            double W = MathEx.sum(w);
            for (int j = 0; j < d; j++) {
                attractor[j] /= W;
            }

            double prob = W / (m * h);
            diff = Math.abs(prob - p) / p;
            p = prob;

            step[iter % k] = MathEx.distance(attractor, x);
            System.arraycopy(attractor, 0, x, 0, d);
        }

        return p;
    }
}
