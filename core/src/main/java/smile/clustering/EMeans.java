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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.math.distance.Distance;
import smile.math.distance.EuclideanDistance;
import smile.sort.QuickSort;
import smile.util.AlgoStatus;
import smile.util.IterativeAlgorithmController;

/**
 * E-Means clustering. E-Means is a mini-batch exponential moving average (EMA)
 * optimization procedure for the k-means objective that converges to lower
 * distortion than Lloyd's algorithm in roughly one-tenth the passes over the
 * dataset.
 * <p>
 * For each cluster {@code j}, E-Means maintains an exponentially decayed count
 * {@code N_j} and sum {@code S_j}, reading off the centroid as
 * {@code c_j = S_j / N_j}:
 * <pre>{@code
 * N_j <- (1 - alpha) * N_j + alpha * n_j
 * S_j <- (1 - alpha) * S_j + alpha * s_j
 * }</pre>
 * where {@code n_j} and {@code s_j} are the batch count and sum assigned to
 * cluster {@code j}, and {@code alpha} is annealed on a cosine schedule from
 * an initial peak {@code alpha0} down to {@code 0.1 * alpha0}.
 * <p>
 * E-Means combines four key mechanisms:
 * <ul>
 * <li><b>Automatic scaling laws:</b> The mini-batch size {@code B} and initial
 *     step size {@code alpha0} are automatically determined from the dataset
 *     size {@code n}, cluster count {@code k}, and epoch budget {@code T}
 *     without hyperparameter tuning.</li>
 * <li><b>Cosine step-size schedule:</b> Progressively stabilizes centroid
 *     movement across mini-batch updates.</li>
 * <li><b>Dead-cluster revival:</b> Every 5 mini-batches, clusters whose EMA
 *     mass falls below {@code tau} times the mean mass are revived by splitting
 *     active donor clusters sampled in proportion to their mass.</li>
 * <li><b>Last-epoch-mean finalization:</b> In the closing pass over the data,
 *     each centroid is set to the exact unweighted mean of the points assigned
 *     to it during that pass.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>Swann Bessa, Pierre-Emmanuel Mazar&eacute;, Gergely Szilvasy, Hugo Touvron,
 *     David Picard, Matthijs Douze, and Herv&eacute; J&eacute;gou.
 *     e-means: outperforming k-means (in one-tenth the epochs).
 *     arXiv:2611.09501, 2026.</li>
 * </ol>
 *
 * @see KMeans
 * @see GMeans
 * @see XMeans
 *
 * @author Haifeng Li
 */
public class EMeans {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EMeans.class);

    /** Default number of training passes over the dataset. */
    public static final double DEFAULT_EPOCHS = 5.0;
    /** Ratio of minimum step size to initial step size in the cosine schedule. */
    private static final double ALPHA_MIN_FACTOR = 0.1;
    /** Number of mini-batch updates between dead-cluster revival checks. */
    private static final int CHECK_UNUSED_EVERY = 5;
    /** Initial EMA mass N_j = 2^-16. */
    private static final double INIT_USAGE = Math.scalb(1.0, -16);
    /** Relative coordinate perturbation delta for donor cluster splits. */
    private static final double SPLIT_EPSILON = 1E-4;
    /** Default revival threshold tau when d > 2. */
    private static final double DEFAULT_UNUSED_THRESHOLD = 0.1;
    /** Maximum dimension at or below which automatic revival is disabled. */
    private static final int NO_REVIVAL_MAX_D = 2;
    /** Maximum dimension at or below which BatchLaw.AUTO selects QUALITY. */
    private static final int AUTO_QUALITY_MAX_D = 16;

    /** Batch law coefficients: B = C_B * k^B_K * n^B_N * T^B_T. */
    private static final double THROUGHPUT_C_B = 0.035134;
    private static final double QUALITY_C_B = 0.00439175;
    private static final double B_K = 0.134749;
    private static final double B_N = 0.876473;
    private static final double B_T = 0.813344;

    /** Step law coefficients: alpha0 = C_ALPHA * k^A_K * B^A_B * n^A_N * T^A_T. */
    private static final double C_ALPHA = 0.8244;
    private static final double A_K = -0.4617;
    private static final double A_B = 0.9003;
    private static final double A_N = -0.4498;
    private static final double A_T = -0.1057;
    private static final double MIN_ALPHA0 = 1E-4;
    private static final double MAX_ALPHA0 = 1.0;

    /** Private constructor to prevent instantiation. */
    private EMeans() {
    }

    /**
     * Scaling law mode for selecting the mini-batch size {@code B}.
     */
    public enum BatchLaw {
        /**
         * Automatically selects {@link #QUALITY} when {@code d <= 16} and
         * {@link #THROUGHPUT} when {@code d > 16}.
         */
        AUTO,
        /**
         * Throughput-optimized batch law:
         * {@code B = 0.035134 * k^0.134749 * n^0.876473 * T^0.813344},
         * rounded to the nearest power of two.
         */
        THROUGHPUT,
        /**
         * Quality-optimized batch law (batches ~8x smaller than throughput):
         * {@code B = 0.00439175 * k^0.134749 * n^0.876473 * T^0.813344},
         * rounded to the nearest power of two.
         */
        QUALITY
    }

    /**
     * E-Means clustering hyperparameters.
     *
     * @param k the number of clusters (at least 2).
     * @param epochs the number of training passes over the dataset ({@code > 0},
     *               may be fractional such as {@code 2.5}).
     * @param batchLaw the scaling law used to choose the batch size when
     *                 {@code batchSize == 0}.
     * @param batchSize manual batch size override, or {@code 0} to derive from
     *                  {@code batchLaw}.
     * @param alpha0 manual initial EMA step size in {@code (0, 1]}, or {@code 0.0}
     *               to derive from the step scaling law.
     * @param unusedThreshold dead-cluster revival threshold {@code tau >= 0}
     *                        ({@code 0.0} disables revival), or {@link Double#NaN}
     *                        to choose automatically ({@code 0.0} when {@code d <= 2},
     *                        {@code 0.1} when {@code d > 2}).
     * @param spherical if {@code true}, keeps centroids on the unit sphere and
     *                  assigns points by maximum inner product / cosine similarity.
     * @param controller the optional training controller.
     */
    public record Options(int k, double epochs, BatchLaw batchLaw, int batchSize,
                          double alpha0, double unusedThreshold, boolean spherical,
                          IterativeAlgorithmController<AlgoStatus> controller) {
        /** Compact constructor with validation. */
        public Options {
            if (k < 2) {
                throw new IllegalArgumentException("Invalid number of clusters: " + k);
            }
            if (!(epochs > 0.0) || Double.isInfinite(epochs)) {
                throw new IllegalArgumentException("Invalid number of epochs: " + epochs);
            }
            Objects.requireNonNull(batchLaw, "batchLaw cannot be null");
            if (batchSize < 0) {
                throw new IllegalArgumentException("Invalid batch size: " + batchSize);
            }
            if (Double.isNaN(alpha0) || alpha0 < 0.0 || alpha0 > 1.0) {
                throw new IllegalArgumentException("Invalid alpha0: " + alpha0);
            }
            if (!Double.isNaN(unusedThreshold) && (unusedThreshold < 0.0 || Double.isInfinite(unusedThreshold))) {
                throw new IllegalArgumentException("Invalid unusedThreshold: " + unusedThreshold);
            }
        }

        /**
         * Constructor with default 5 epochs and automatic scaling laws.
         * @param k the number of clusters.
         */
        public Options(int k) {
            this(k, DEFAULT_EPOCHS);
        }

        /**
         * Constructor with automatic scaling laws.
         * @param k the number of clusters.
         * @param epochs the number of training passes over the dataset.
         */
        public Options(int k, double epochs) {
            this(k, epochs, BatchLaw.AUTO, 0, 0.0, Double.NaN, false, null);
        }

        /**
         * Constructor with explicit batch law.
         * @param k the number of clusters.
         * @param epochs the number of training passes over the dataset.
         * @param batchLaw the batch scaling law.
         */
        public Options(int k, double epochs, BatchLaw batchLaw) {
            this(k, epochs, batchLaw, 0, 0.0, Double.NaN, false, null);
        }

        /**
         * Returns the persistent set of hyperparameters.
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.emeans.k", Integer.toString(k));
            props.setProperty("smile.emeans.epochs", Double.toString(epochs));
            props.setProperty("smile.emeans.batch_law", batchLaw.name());
            props.setProperty("smile.emeans.batch_size", Integer.toString(batchSize));
            props.setProperty("smile.emeans.alpha0", Double.toString(alpha0));
            if (!Double.isNaN(unusedThreshold)) {
                props.setProperty("smile.emeans.unused_threshold", Double.toString(unusedThreshold));
            }
            props.setProperty("smile.emeans.spherical", Boolean.toString(spherical));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int k = Integer.parseInt(props.getProperty("smile.emeans.k",
                    props.getProperty("smile.clustering.k", "2")));
            double epochs = Double.parseDouble(props.getProperty("smile.emeans.epochs",
                    props.getProperty("smile.clustering.iterations", Double.toString(DEFAULT_EPOCHS))));
            BatchLaw batchLaw = BatchLaw.valueOf(
                    props.getProperty("smile.emeans.batch_law", "AUTO").trim().toUpperCase(Locale.ROOT));
            int batchSize = Integer.parseInt(props.getProperty("smile.emeans.batch_size", "0"));
            double alpha0 = Double.parseDouble(props.getProperty("smile.emeans.alpha0", "0.0"));
            String unusedStr = props.getProperty("smile.emeans.unused_threshold");
            double unusedThreshold = (unusedStr == null || unusedStr.isBlank())
                    ? Double.NaN
                    : Double.parseDouble(unusedStr);
            boolean spherical = Boolean.parseBoolean(props.getProperty("smile.emeans.spherical", "false"));
            return new Options(k, epochs, batchLaw, batchSize, alpha0, unusedThreshold, spherical, null);
        }
    }

    /**
     * Fits e-means clustering with the default 5 epochs.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, int k) {
        return fit(data, new Options(k));
    }

    /**
     * Fits e-means clustering.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param epochs the number of training passes over the dataset.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, int k, double epochs) {
        return fit(data, new Options(k, epochs));
    }

    /**
     * Fits e-means clustering using shared {@link Clustering.Options}.
     * The {@code maxIter} parameter is used as the number of epochs.
     * @param data the input data of which each row is an observation.
     * @param options the clustering hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Clustering.Options options) {
        return fit(data, new Options(options.k(), options.maxIter(), BatchLaw.AUTO,
                0, 0.0, Double.NaN, false, options.controller()));
    }

    /**
     * Fits e-means clustering.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<double[], double[]> fit(double[][] data, Options options) {
        Objects.requireNonNull(options, "options cannot be null");
        KMeans.validateOptions(options.k(), data.length);
        KMeans.validateData(data, false);

        int n = data.length;
        int d = data[0].length;
        int k = options.k();
        double epochs = options.epochs();
        boolean spherical = options.spherical();
        var controller = options.controller();

        int batchSize = resolveBatchSize(n, k, d, epochs, options.batchLaw(), options.batchSize());
        double alpha0 = resolveAlpha0(n, k, epochs, batchSize, options.alpha0());
        double unusedThreshold = resolveUnusedThreshold(options.unusedThreshold(), d);
        int[] passRows = epochRows(n, epochs);
        int totalSteps = totalSteps(passRows, batchSize);

        logger.info("E-Means: n={}, d={}, k={}, epochs={}, batchSize={}, alpha0={}, unusedThreshold={}, spherical={}",
                n, d, k, epochs, batchSize, alpha0, unusedThreshold, spherical);

        // Random initialization: sample k distinct rows uniformly without replacement
        double[][] centroids = new double[k][d];
        int[] initPerm = (k == n) ? IntStream.range(0, n).toArray() : MathEx.permutate(n);
        for (int j = 0; j < k; j++) {
            System.arraycopy(data[initPerm[j]], 0, centroids[j], 0, d);
        }
        if (spherical) {
            normalizeRows(centroids);
        }

        double[] usage = new double[k];
        Arrays.fill(usage, INIT_USAGE);

        int[] group = new int[n];
        Arrays.fill(group, -1);
        long[] passCounts = new long[k];
        double[][] passSums = new double[k][d];

        int[] batchLabels = new int[batchSize];
        double[] batchDist = new double[batchSize];
        int[] batchCounts = new int[k];
        double[][] batchSums = new double[k][d];

        int step = 0;
        boolean interrupted = false;
        int numPasses = passRows.length;

        for (int epoch = 0; epoch < numPasses; epoch++) {
            int rows = passRows[epoch];
            boolean isFinalPass = (epoch == numPasses - 1);
            int[] order = MathEx.permutate(n);
            double epochObjective = 0.0;

            if (isFinalPass) {
                Arrays.fill(passCounts, 0L);
                for (int j = 0; j < k; j++) {
                    Arrays.fill(passSums[j], 0.0);
                }
            }

            for (int begin = 0; begin < rows; begin += batchSize) {
                int end = Math.min(begin + batchSize, rows);
                int b = end - begin;

                double alpha = scheduledAlpha(alpha0, step, totalSteps);
                double decay = 1.0 - alpha;

                double[][] readout = spherical ? normalizedCopy(centroids) : centroids;

                // Assign points in the mini-batch in parallel
                final int batchOffset = begin;
                IntStream.range(0, b).parallel().forEach(i -> {
                    double[] xi = data[order[batchOffset + i]];
                    int bestCluster = 0;
                    if (spherical) {
                        double bestSim = Double.NEGATIVE_INFINITY;
                        for (int j = 0; j < k; j++) {
                            double sim = MathEx.dot(xi, readout[j]);
                            if (sim > bestSim) {
                                bestSim = sim;
                                bestCluster = j;
                            }
                        }
                        batchLabels[i] = bestCluster;
                        batchDist[i] = Math.max(0.0, 2.0 - 2.0 * bestSim);
                    } else {
                        double nearest = Double.POSITIVE_INFINITY;
                        for (int j = 0; j < k; j++) {
                            double dist = MathEx.squaredDistance(xi, readout[j]);
                            if (dist < nearest) {
                                nearest = dist;
                                bestCluster = j;
                            }
                        }
                        batchLabels[i] = bestCluster;
                        batchDist[i] = nearest;
                    }
                });

                // Accumulate batch counts, sums, and objective
                Arrays.fill(batchCounts, 0);
                for (int j = 0; j < k; j++) {
                    Arrays.fill(batchSums[j], 0.0);
                }

                for (int i = 0; i < b; i++) {
                    int idx = order[begin + i];
                    int cluster = batchLabels[i];
                    epochObjective += batchDist[i];
                    batchCounts[cluster]++;
                    double[] xi = data[idx];
                    double[] sumCluster = batchSums[cluster];
                    for (int l = 0; l < d; l++) {
                        sumCluster[l] += xi[l];
                    }
                    if (isFinalPass) {
                        group[idx] = cluster;
                    }
                }

                if (isFinalPass) {
                    for (int j = 0; j < k; j++) {
                        if (batchCounts[j] > 0) {
                            passCounts[j] += batchCounts[j];
                            double[] ps = passSums[j];
                            double[] bs = batchSums[j];
                            for (int l = 0; l < d; l++) {
                                ps[l] += bs[l];
                            }
                        }
                    }
                }

                // Apply EMA update to usage (N_j) and centroids (c_j)
                boolean replaceUsage = (alpha == 1.0);
                if (!replaceUsage) {
                    for (int j = 0; j < k; j++) {
                        usage[j] *= decay;
                    }
                }

                for (int j = 0; j < k; j++) {
                    int count = batchCounts[j];
                    if (count > 0) {
                        double retained = replaceUsage ? 0.0 : usage[j];
                        double mass = retained + alpha * count;
                        double weight = (alpha * count) / mass;
                        double invCount = 1.0 / count;
                        double[] cj = centroids[j];
                        double[] sj = batchSums[j];
                        if (weight == 1.0) {
                            for (int l = 0; l < d; l++) {
                                cj[l] = sj[l] * invCount;
                            }
                        } else {
                            for (int l = 0; l < d; l++) {
                                double mean = sj[l] * invCount;
                                cj[l] += weight * (mean - cj[l]);
                            }
                        }
                        usage[j] = mass;
                    }
                }

                // Dead-cluster revival every CHECK_UNUSED_EVERY steps
                if (unusedThreshold > 0.0 && (step + 1) % CHECK_UNUSED_EVERY == 0) {
                    reviveDeadClusters(centroids, usage, unusedThreshold);
                }

                step++;
            }

            double distortion = epochObjective / rows;
            logger.info("Pass {}/{}: moving distortion = {}", epoch + 1, numPasses, distortion);
            if (controller != null) {
                controller.submit(new AlgoStatus(epoch + 1, distortion));
                if (controller.isInterrupted()) {
                    interrupted = true;
                    break;
                }
            }
        }

        // Last-epoch-mean finalization (Algorithm 1, line 12)
        for (int j = 0; j < k; j++) {
            long count = passCounts[j];
            if (count > 0) {
                double invCount = 1.0 / count;
                double[] cj = centroids[j];
                double[] ps = passSums[j];
                for (int l = 0; l < d; l++) {
                    cj[l] = ps[l] * invCount;
                }
            }
        }
        if (spherical) {
            normalizeRows(centroids);
        }

        Distance<double[]> distance = spherical ? new SphericalDistanceD() : new EuclideanDistance();

        // If fractional epochs < 1 or interrupted early left unassigned points, assign them now
        if (interrupted || passRows[numPasses - 1] < n) {
            assignUnassigned(data, centroids, group, spherical);
        }

        double[] proximity = new double[n];
        CentroidClustering.computeProximity(data, centroids, group, distance, proximity);
        return new CentroidClustering<>("E-Means", centroids, distance, group, proximity);
    }

    /**
     * Fits e-means clustering on single-precision input data with the default 5 epochs.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, int k) {
        return fit(data, new Options(k));
    }

    /**
     * Fits e-means clustering on single-precision input data.
     * @param data the input data of which each row is an observation.
     * @param k the number of clusters.
     * @param epochs the number of training passes over the dataset.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, int k, double epochs) {
        return fit(data, new Options(k, epochs));
    }

    /**
     * Fits e-means clustering on single-precision input data using shared {@link Clustering.Options}.
     * The {@code maxIter} parameter is used as the number of epochs.
     * @param data the input data of which each row is an observation.
     * @param options the clustering hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, Clustering.Options options) {
        return fit(data, new Options(options.k(), options.maxIter(), BatchLaw.AUTO,
                0, 0.0, Double.NaN, false, options.controller()));
    }

    /**
     * Fits e-means clustering on single-precision input data.
     * @param data the input data of which each row is an observation.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static CentroidClustering<float[], float[]> fit(float[][] data, Options options) {
        Objects.requireNonNull(options, "options cannot be null");
        KMeans.validateOptions(options.k(), data.length);
        KMeans.validateData(data, false);

        int n = data.length;
        int d = data[0].length;
        int k = options.k();
        double epochs = options.epochs();
        boolean spherical = options.spherical();
        var controller = options.controller();

        int batchSize = resolveBatchSize(n, k, d, epochs, options.batchLaw(), options.batchSize());
        double alpha0 = resolveAlpha0(n, k, epochs, batchSize, options.alpha0());
        double unusedThreshold = resolveUnusedThreshold(options.unusedThreshold(), d);
        int[] passRows = epochRows(n, epochs);
        int totalSteps = totalSteps(passRows, batchSize);

        logger.info("E-Means (float): n={}, d={}, k={}, epochs={}, batchSize={}, alpha0={}, unusedThreshold={}, spherical={}",
                n, d, k, epochs, batchSize, alpha0, unusedThreshold, spherical);

        // Random initialization: sample k distinct rows uniformly without replacement
        float[][] centroids = new float[k][d];
        int[] initPerm = (k == n) ? IntStream.range(0, n).toArray() : MathEx.permutate(n);
        for (int j = 0; j < k; j++) {
            System.arraycopy(data[initPerm[j]], 0, centroids[j], 0, d);
        }
        if (spherical) {
            normalizeRows(centroids);
        }

        float[] usage = new float[k];
        Arrays.fill(usage, (float) INIT_USAGE);

        int[] group = new int[n];
        Arrays.fill(group, -1);
        long[] passCounts = new long[k];
        double[][] passSums = new double[k][d];

        int[] batchLabels = new int[batchSize];
        double[] batchDist = new double[batchSize];
        int[] batchCounts = new int[k];
        double[][] batchSums = new double[k][d];

        int step = 0;
        boolean interrupted = false;
        int numPasses = passRows.length;

        for (int epoch = 0; epoch < numPasses; epoch++) {
            int rows = passRows[epoch];
            boolean isFinalPass = (epoch == numPasses - 1);
            int[] order = MathEx.permutate(n);
            double epochObjective = 0.0;

            if (isFinalPass) {
                Arrays.fill(passCounts, 0L);
                for (int j = 0; j < k; j++) {
                    Arrays.fill(passSums[j], 0.0);
                }
            }

            for (int begin = 0; begin < rows; begin += batchSize) {
                int end = Math.min(begin + batchSize, rows);
                int b = end - begin;

                float alpha = (float) scheduledAlpha(alpha0, step, totalSteps);
                float decay = 1.0f - alpha;

                float[][] readout = spherical ? normalizedCopy(centroids) : centroids;

                // Assign points in the mini-batch in parallel
                final int batchOffset = begin;
                IntStream.range(0, b).parallel().forEach(i -> {
                    float[] xi = data[order[batchOffset + i]];
                    int bestCluster = 0;
                    if (spherical) {
                        double bestSim = Double.NEGATIVE_INFINITY;
                        for (int j = 0; j < k; j++) {
                            double sim = dotF(xi, readout[j]);
                            if (sim > bestSim) {
                                bestSim = sim;
                                bestCluster = j;
                            }
                        }
                        batchLabels[i] = bestCluster;
                        batchDist[i] = Math.max(0.0, 2.0 - 2.0 * bestSim);
                    } else {
                        double nearest = Double.POSITIVE_INFINITY;
                        for (int j = 0; j < k; j++) {
                            double dist = MathEx.squaredDistance(xi, readout[j]);
                            if (dist < nearest) {
                                nearest = dist;
                                bestCluster = j;
                            }
                        }
                        batchLabels[i] = bestCluster;
                        batchDist[i] = nearest;
                    }
                });

                // Accumulate batch counts, sums, and objective
                Arrays.fill(batchCounts, 0);
                for (int j = 0; j < k; j++) {
                    Arrays.fill(batchSums[j], 0.0);
                }

                for (int i = 0; i < b; i++) {
                    int idx = order[begin + i];
                    int cluster = batchLabels[i];
                    epochObjective += batchDist[i];
                    batchCounts[cluster]++;
                    float[] xi = data[idx];
                    double[] sumCluster = batchSums[cluster];
                    for (int l = 0; l < d; l++) {
                        sumCluster[l] += xi[l];
                    }
                    if (isFinalPass) {
                        group[idx] = cluster;
                    }
                }

                if (isFinalPass) {
                    for (int j = 0; j < k; j++) {
                        if (batchCounts[j] > 0) {
                            passCounts[j] += batchCounts[j];
                            double[] ps = passSums[j];
                            double[] bs = batchSums[j];
                            for (int l = 0; l < d; l++) {
                                ps[l] += bs[l];
                            }
                        }
                    }
                }

                // Apply EMA update to usage (N_j) and centroids (c_j)
                boolean replaceUsage = (alpha == 1.0f);
                if (!replaceUsage) {
                    for (int j = 0; j < k; j++) {
                        usage[j] *= decay;
                    }
                }

                for (int j = 0; j < k; j++) {
                    int count = batchCounts[j];
                    if (count > 0) {
                        float retained = replaceUsage ? 0.0f : usage[j];
                        float mass = retained + alpha * count;
                        float weight = (alpha * count) / mass;
                        double invCount = 1.0 / count;
                        float[] cj = centroids[j];
                        double[] sj = batchSums[j];
                        if (weight == 1.0f) {
                            for (int l = 0; l < d; l++) {
                                cj[l] = (float) (sj[l] * invCount);
                            }
                        } else {
                            for (int l = 0; l < d; l++) {
                                float mean = (float) (sj[l] * invCount);
                                cj[l] += weight * (mean - cj[l]);
                            }
                        }
                        usage[j] = mass;
                    }
                }

                // Dead-cluster revival every CHECK_UNUSED_EVERY steps
                if (unusedThreshold > 0.0 && (step + 1) % CHECK_UNUSED_EVERY == 0) {
                    reviveDeadClusters(centroids, usage, (float) unusedThreshold);
                }

                step++;
            }

            double distortion = epochObjective / rows;
            logger.info("Pass {}/{}: moving distortion = {}", epoch + 1, numPasses, distortion);
            if (controller != null) {
                controller.submit(new AlgoStatus(epoch + 1, distortion));
                if (controller.isInterrupted()) {
                    interrupted = true;
                    break;
                }
            }
        }

        // Last-epoch-mean finalization (Algorithm 1, line 12)
        for (int j = 0; j < k; j++) {
            long count = passCounts[j];
            if (count > 0) {
                double invCount = 1.0 / count;
                float[] cj = centroids[j];
                double[] ps = passSums[j];
                for (int l = 0; l < d; l++) {
                    cj[l] = (float) (ps[l] * invCount);
                }
            }
        }
        if (spherical) {
            normalizeRows(centroids);
        }

        Distance<float[]> distance = spherical ? new SphericalDistanceF() : MathEx::distance;

        if (interrupted || passRows[numPasses - 1] < n) {
            assignUnassigned(data, centroids, group, spherical);
        }

        double[] proximity = new double[n];
        CentroidClustering.computeProximity(data, centroids, group, distance, proximity);
        return new CentroidClustering<>("E-Means", centroids, distance, group, proximity);
    }

    /**
     * Rounds a positive value to the nearest power of two.
     */
    static int roundPowerOfTwo(double value) {
        if (value <= 1.0) {
            return 1;
        }
        int exp = (int) Math.floor(Math.log(value) / Math.log(2.0));
        int low = 1 << Math.min(exp, 30);
        long high = Math.min(1L << 30, (long) low << 1);
        return (value / low < high / value) ? low : (int) high;
    }

    /**
     * Resolves {@link BatchLaw#AUTO} from the feature dimension {@code d}.
     */
    static BatchLaw resolveBatchLaw(BatchLaw batchLaw, int d) {
        if (batchLaw != BatchLaw.AUTO) {
            return batchLaw;
        }
        return d <= AUTO_QUALITY_MAX_D ? BatchLaw.QUALITY : BatchLaw.THROUGHPUT;
    }

    /**
     * Resolves the dead-cluster revival threshold {@code tau} from {@code d} when unspecified.
     */
    static double resolveUnusedThreshold(double unusedThreshold, int d) {
        if (Double.isNaN(unusedThreshold)) {
            return d <= NO_REVIVAL_MAX_D ? 0.0 : DEFAULT_UNUSED_THRESHOLD;
        }
        return unusedThreshold;
    }

    /**
     * Resolves the statistical mini-batch size {@code B}.
     */
    static int resolveBatchSize(int n, int k, int d, double epochs, BatchLaw batchLaw, int requestedBatchSize) {
        if (requestedBatchSize > 0) {
            return Math.min(requestedBatchSize, n);
        }
        BatchLaw resolved = resolveBatchLaw(batchLaw, d);
        double coeff = (resolved == BatchLaw.QUALITY) ? QUALITY_C_B : THROUGHPUT_C_B;
        double rawBatch = coeff * Math.pow(k, B_K) * Math.pow(n, B_N) * Math.pow(epochs, B_T);
        int batchSize = roundPowerOfTwo(rawBatch);
        int cap = (resolved == BatchLaw.THROUGHPUT) ? roundPowerOfTwo(n / 2.0) : (n / 2);
        batchSize = Math.min(batchSize, cap);
        return Math.max(1, Math.min(batchSize, n));
    }

    /**
     * Resolves the initial EMA step size {@code alpha0} from the fitted scaling law.
     */
    static double resolveAlpha0(int n, int k, double epochs, int batchSize, double requestedAlpha0) {
        if (requestedAlpha0 > 0.0) {
            return requestedAlpha0;
        }
        double alpha0 = C_ALPHA
                * Math.pow(k, A_K)
                * Math.pow(batchSize, A_B)
                * Math.pow(n, A_N)
                * Math.pow(epochs, A_T);
        return Math.clamp(alpha0, MIN_ALPHA0, MAX_ALPHA0);
    }

    /**
     * Computes the row count for each pass: whole passes, an optional rounded
     * fractional pass, and a full closing pass.
     */
    static int[] epochRows(int n, double epochs) {
        int whole = (int) Math.floor(epochs);
        int remainder = (int) Math.rint((epochs - whole) * n);
        if (whole == 0) {
            return new int[]{Math.max(1, remainder)};
        }
        if (remainder > 0) {
            int[] rows = new int[whole + 1];
            Arrays.fill(rows, 0, whole - 1, n);
            rows[whole - 1] = remainder;
            rows[whole] = n;
            return rows;
        }
        int[] rows = new int[whole];
        Arrays.fill(rows, n);
        return rows;
    }

    /**
     * Computes total mini-batch updates across all passes.
     */
    static int totalSteps(int[] passRows, int batchSize) {
        int total = 0;
        for (int rows : passRows) {
            total += (rows + batchSize - 1) / batchSize;
        }
        return total;
    }

    /**
     * Cosine-anneals the EMA step size towards {@code 0.1 * alpha0}.
     */
    static double scheduledAlpha(double alpha0, int step, int totalSteps) {
        double fraction = (double) Math.min(step, totalSteps) / Math.max(1, totalSteps);
        double cosine = 0.5 * (1.0 + Math.cos(Math.PI * fraction));
        return alpha0 * (ALPHA_MIN_FACTOR + (1.0 - ALPHA_MIN_FACTOR) * cosine);
    }

    /**
     * Revives dead clusters whose EMA mass is below {@code unusedThreshold * mean(usage)}
     * by splitting active donor clusters sampled without replacement in proportion to their mass.
     */
    static int reviveDeadClusters(double[][] centroids, double[] usage, double unusedThreshold) {
        int k = centroids.length;
        int d = centroids[0].length;
        double sumUsage = 0.0;
        for (double u : usage) {
            sumUsage += u;
        }
        double threshold = sumUsage * unusedThreshold / k;

        int[] unused = new int[k];
        int[] donors = new int[k];
        int nUnused = 0;
        int nDonors = 0;
        for (int j = 0; j < k; j++) {
            if (usage[j] < threshold) {
                unused[nUnused++] = j;
            } else if (usage[j] > 0.0) {
                donors[nDonors++] = j;
            }
        }

        int nSplit = Math.min(nUnused, nDonors);
        if (nSplit == 0) {
            return 0;
        }

        // Weighted sampling without replacement via Gumbel-max keys: log(usage) - log(-log(U))
        double[] keys = new double[nDonors];
        for (int i = 0; i < nDonors; i++) {
            int j = donors[i];
            double u = Math.max(MathEx.random(), Double.MIN_NORMAL);
            keys[i] = Math.log(usage[j]) - Math.log(-Math.log(u));
        }
        int[] order = QuickSort.sort(keys);

        for (int s = 0; s < nSplit; s++) {
            int source = donors[order[nDonors - 1 - s]];
            int target = unused[s];

            double[] donorCentroid = centroids[source];
            double[] childCentroid = centroids[target];
            boolean identical = true;
            for (int l = 0; l < d; l++) {
                double offset = ((l & 1) == 0) ? SPLIT_EPSILON : -SPLIT_EPSILON;
                double base = donorCentroid[l];
                double dVal = base * (1.0 - offset);
                double cVal = base * (1.0 + offset);
                donorCentroid[l] = dVal;
                childCentroid[l] = cVal;
                if (dVal != cVal) {
                    identical = false;
                }
            }
            if (identical) {
                double next = Math.nextUp(childCentroid[0]);
                childCentroid[0] = Double.isFinite(next) ? next : Math.nextDown(childCentroid[0]);
            }

            double halfUsage = usage[source] * 0.5;
            usage[source] = halfUsage;
            usage[target] = halfUsage;
        }
        return nSplit;
    }

    /**
     * Revives dead clusters (single-precision).
     */
    static int reviveDeadClusters(float[][] centroids, float[] usage, float unusedThreshold) {
        int k = centroids.length;
        int d = centroids[0].length;
        double sumUsage = 0.0;
        for (float u : usage) {
            sumUsage += u;
        }
        float threshold = (float) (sumUsage * unusedThreshold / k);

        int[] unused = new int[k];
        int[] donors = new int[k];
        int nUnused = 0;
        int nDonors = 0;
        for (int j = 0; j < k; j++) {
            if (usage[j] < threshold) {
                unused[nUnused++] = j;
            } else if (usage[j] > 0.0f) {
                donors[nDonors++] = j;
            }
        }

        int nSplit = Math.min(nUnused, nDonors);
        if (nSplit == 0) {
            return 0;
        }

        double[] keys = new double[nDonors];
        for (int i = 0; i < nDonors; i++) {
            int j = donors[i];
            double u = Math.max(MathEx.random(), Double.MIN_NORMAL);
            keys[i] = Math.log(usage[j]) - Math.log(-Math.log(u));
        }
        int[] order = QuickSort.sort(keys);

        for (int s = 0; s < nSplit; s++) {
            int source = donors[order[nDonors - 1 - s]];
            int target = unused[s];

            float[] donorCentroid = centroids[source];
            float[] childCentroid = centroids[target];
            boolean identical = true;
            for (int l = 0; l < d; l++) {
                float offset = ((l & 1) == 0) ? (float) SPLIT_EPSILON : (float) -SPLIT_EPSILON;
                float base = donorCentroid[l];
                float dVal = base * (1.0f - offset);
                float cVal = base * (1.0f + offset);
                donorCentroid[l] = dVal;
                childCentroid[l] = cVal;
                if (dVal != cVal) {
                    identical = false;
                }
            }
            if (identical) {
                float next = Math.nextUp(childCentroid[0]);
                childCentroid[0] = Float.isFinite(next) ? next : Math.nextDown(childCentroid[0]);
            }

            float halfUsage = usage[source] * 0.5f;
            usage[source] = halfUsage;
            usage[target] = halfUsage;
        }
        return nSplit;
    }

    /**
     * Normalizes each row of {@code matrix} in place to unit L2 norm (zero vectors remain zero).
     */
    private static void normalizeRows(double[][] matrix) {
        for (double[] row : matrix) {
            double norm = MathEx.norm(row);
            if (norm > 0.0) {
                for (int l = 0; l < row.length; l++) {
                    row[l] /= norm;
                }
            }
        }
    }

    /**
     * Normalizes each row of {@code matrix} in place to unit L2 norm (single-precision).
     */
    private static void normalizeRows(float[][] matrix) {
        for (float[] row : matrix) {
            double sum = 0.0;
            for (float v : row) {
                sum += (double) v * v;
            }
            double norm = Math.sqrt(sum);
            if (norm > 0.0) {
                for (int l = 0; l < row.length; l++) {
                    row[l] = (float) (row[l] / norm);
                }
            }
        }
    }

    /**
     * Returns a unit-normalized copy of {@code centroids}.
     */
    private static double[][] normalizedCopy(double[][] centroids) {
        int k = centroids.length;
        int d = centroids[0].length;
        double[][] copy = new double[k][d];
        for (int j = 0; j < k; j++) {
            System.arraycopy(centroids[j], 0, copy[j], 0, d);
        }
        normalizeRows(copy);
        return copy;
    }

    /**
     * Returns a unit-normalized copy of {@code centroids} (single-precision).
     */
    private static float[][] normalizedCopy(float[][] centroids) {
        int k = centroids.length;
        int d = centroids[0].length;
        float[][] copy = new float[k][d];
        for (int j = 0; j < k; j++) {
            System.arraycopy(centroids[j], 0, copy[j], 0, d);
        }
        normalizeRows(copy);
        return copy;
    }

    /**
     * Assigns any data points whose label is still {@code -1} (e.g. when {@code epochs < 1}
     * or when interrupted early).
     */
    private static void assignUnassigned(double[][] data, double[][] centroids, int[] group, boolean spherical) {
        int n = data.length;
        int k = centroids.length;
        IntStream.range(0, n).parallel().forEach(i -> {
            if (group[i] < 0) {
                double[] xi = data[i];
                int best = 0;
                if (spherical) {
                    double bestSim = Double.NEGATIVE_INFINITY;
                    for (int j = 0; j < k; j++) {
                        double sim = MathEx.dot(xi, centroids[j]);
                        if (sim > bestSim) {
                            bestSim = sim;
                            best = j;
                        }
                    }
                } else {
                    double nearest = Double.POSITIVE_INFINITY;
                    for (int j = 0; j < k; j++) {
                        double dist = MathEx.squaredDistance(xi, centroids[j]);
                        if (dist < nearest) {
                            nearest = dist;
                            best = j;
                        }
                    }
                }
                group[i] = best;
            }
        });
    }

    /**
     * Assigns any single-precision data points whose label is still {@code -1}.
     */
    private static void assignUnassigned(float[][] data, float[][] centroids, int[] group, boolean spherical) {
        int n = data.length;
        int k = centroids.length;
        IntStream.range(0, n).parallel().forEach(i -> {
            if (group[i] < 0) {
                float[] xi = data[i];
                int best = 0;
                if (spherical) {
                    double bestSim = Double.NEGATIVE_INFINITY;
                    for (int j = 0; j < k; j++) {
                        double sim = dotF(xi, centroids[j]);
                        if (sim > bestSim) {
                            bestSim = sim;
                            best = j;
                        }
                    }
                } else {
                    double nearest = Double.POSITIVE_INFINITY;
                    for (int j = 0; j < k; j++) {
                        double dist = MathEx.squaredDistance(xi, centroids[j]);
                        if (dist < nearest) {
                            nearest = dist;
                            best = j;
                        }
                    }
                }
                group[i] = best;
            }
        });
    }

    /**
     * Computes the inner product of two {@code float[]} vectors in double precision.
     */
    private static double dotF(float[] x, float[] y) {
        double sum = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += (double) x[i] * y[i];
        }
        return sum;
    }

    /**
     * Spherical distance on unit vectors: {@code sqrt(max(0, 2 - 2 * <x, y>))},
     * whose square equals {@code 2 - 2 * <x, y>}.
     */
    private static final class SphericalDistanceD implements Distance<double[]> {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        @Override
        public double d(double[] x, double[] y) {
            return Math.sqrt(Math.max(0.0, 2.0 - 2.0 * MathEx.dot(x, y)));
        }
    }

    /**
     * Single-precision spherical distance on unit vectors.
     */
    private static final class SphericalDistanceF implements Distance<float[]> {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        @Override
        public double d(float[] x, float[] y) {
            return Math.sqrt(Math.max(0.0, 2.0 - 2.0 * dotF(x, y)));
        }
    }
}
