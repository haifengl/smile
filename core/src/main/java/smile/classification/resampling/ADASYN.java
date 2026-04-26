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
package smile.classification.resampling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RandomProjectionForest;

/**
 * Adaptive Synthetic Sampling (ADASYN).
 * <p>
 * ADASYN is an extension of {@link SMOTE} that adaptively generates synthetic
 * minority class samples. Rather than producing the same number of synthetic
 * samples for every minority instance, ADASYN concentrates synthesis where the
 * local distribution is hardest to learn — i.e. minority instances that are
 * surrounded by many majority neighbors receive more synthetic samples than
 * those in denser minority regions.
 * <p>
 * The algorithm proceeds as follows:
 * <ol>
 *   <li>For each minority instance {@code x_i}, find its {@code k} nearest
 *       neighbors in the <em>entire</em> dataset (majority and minority).</li>
 *   <li>Compute the density ratio {@code r_i = Δ_i / k}, where {@code Δ_i} is
 *       the number of those neighbors that belong to the majority class.</li>
 *   <li>Normalize {@code r_i} so that the weights sum to 1:
 *       {@code r̂_i = r_i / Σ r_i}.</li>
 *   <li>For each minority instance {@code x_i}, generate
 *       {@code g_i = round(r̂_i * G)} synthetic samples, where
 *       {@code G = (|majority| − |minority|) * ratio} is the total number of
 *       samples to generate.</li>
 *   <li>Each synthetic sample is placed on the line segment between {@code x_i}
 *       and one of its minority-only nearest neighbors, at a uniformly random
 *       position (identical interpolation to SMOTE).</li>
 * </ol>
 * <p>
 * Instances whose density ratio {@code r_i = 0} (entirely surrounded by
 * minority neighbors) contribute no synthetic samples, so synthesis is
 * automatically focused on the class boundary.
 * <p>
 * <b>Index selection</b><br>
 * When the input dimensionality {@code d <= highDimThreshold} (default 20),
 * a {@link KDTree} is used for exact k-NN search. For higher dimensionality
 * a {@link RandomProjectionForest} (approximate NN) is used instead, because
 * k-d trees suffer from the curse of dimensionality.
 * <p>
 * <b>Limitations</b>
 * <ul>
 *   <li>Feature spaces must be entirely continuous (no categorical features).</li>
 *   <li>When all minority instances have {@code r_i = 0} (perfectly separated
 *       classes), no synthetic samples are generated and a warning is logged.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>H. He, Y. Bai, E. A. Garcia, and S. Li.
 *     ADASYN: Adaptive Synthetic Sampling Approach for Imbalanced Learning.
 *     IJCNN, 2008.</li>
 * </ol>
 *
 * @param data   the augmented feature matrix (original + synthetic samples).
 * @param labels the augmented labels (original + synthetic sample labels).
 *
 * @author Haifeng Li
 */
public record ADASYN(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ADASYN.class);

    /**
     * ADASYN hyperparameters.
     *
     * @param k                the number of nearest neighbors used to compute
     *                         the local density ratio (typically 5).
     * @param ratio            controls the desired level of synthetic data
     *                         generation. The total number of synthetic samples
     *                         is {@code G = (|majority| - |minority|) * ratio}.
     *                         A value of 1.0 attempts full balancing.
     *                         Must be in {@code (0, 1]}.
     * @param highDimThreshold the dimensionality threshold above which
     *                         {@link RandomProjectionForest} is used instead of
     *                         {@link KDTree} for k-NN search.
     * @param rpfNumTrees      number of trees in the {@link RandomProjectionForest}.
     * @param rpfLeafSize      maximum leaf size for the {@link RandomProjectionForest}.
     */
    public record Options(int k, double ratio, int highDimThreshold,
                          int rpfNumTrees, int rpfLeafSize) {

        /** Default constructor: k=5, ratio=1.0, highDimThreshold=20, rpfNumTrees=10, rpfLeafSize=30. */
        public Options() {
            this(5, 1.0, 20, 10, 30);
        }

        /**
         * Constructor with default index parameters.
         * @param k     number of nearest neighbors.
         * @param ratio target imbalance ratio.
         */
        public Options(int k, double ratio) {
            this(k, ratio, 20, 10, 30);
        }

        /** Compact constructor with validation. */
        public Options {
            if (k < 1) {
                throw new IllegalArgumentException("k must be at least 1: " + k);
            }
            if (ratio <= 0.0 || ratio > 1.0) {
                throw new IllegalArgumentException("ratio must be in (0, 1]: " + ratio);
            }
            if (highDimThreshold < 1) {
                throw new IllegalArgumentException("highDimThreshold must be at least 1: " + highDimThreshold);
            }
            if (rpfNumTrees < 1) {
                throw new IllegalArgumentException("rpfNumTrees must be at least 1: " + rpfNumTrees);
            }
            if (rpfLeafSize < 1) {
                throw new IllegalArgumentException("rpfLeafSize must be at least 1: " + rpfLeafSize);
            }
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.adasyn.k</code>
         * <li><code>smile.adasyn.ratio</code>
         * <li><code>smile.adasyn.high_dim_threshold</code>
         * <li><code>smile.adasyn.rpf_num_trees</code>
         * <li><code>smile.adasyn.rpf_leaf_size</code>
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.adasyn.k",                  Integer.toString(k));
            props.setProperty("smile.adasyn.ratio",              Double.toString(ratio));
            props.setProperty("smile.adasyn.high_dim_threshold", Integer.toString(highDimThreshold));
            props.setProperty("smile.adasyn.rpf_num_trees",      Integer.toString(rpfNumTrees));
            props.setProperty("smile.adasyn.rpf_leaf_size",      Integer.toString(rpfLeafSize));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int    k                = Integer.parseInt(props.getProperty("smile.adasyn.k",                  "5"));
            double ratio            = Double.parseDouble(props.getProperty("smile.adasyn.ratio",            "1.0"));
            int    highDimThreshold = Integer.parseInt(props.getProperty("smile.adasyn.high_dim_threshold", "20"));
            int    rpfNumTrees      = Integer.parseInt(props.getProperty("smile.adasyn.rpf_num_trees",      "10"));
            int    rpfLeafSize      = Integer.parseInt(props.getProperty("smile.adasyn.rpf_leaf_size",      "30"));
            return new Options(k, ratio, highDimThreshold, rpfNumTrees, rpfLeafSize);
        }
    }

    /**
     * Returns the total number of samples after resampling.
     *
     * @return the number of rows in {@code data}.
     */
    public int size() {
        return data.length;
    }

    /**
     * Applies ADASYN to the given dataset with default {@link Options}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return an {@link ADASYN} instance holding the augmented data and labels.
     */
    public static ADASYN fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options());
    }

    /**
     * Applies ADASYN to the given dataset.
     * <p>
     * The minority class (label with the fewest occurrences) is identified
     * automatically. Adaptive synthetic samples are generated and appended to
     * the original dataset.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return an {@link ADASYN} instance holding the augmented data and labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} have
     *         different lengths, if the input is empty, or if the minority class
     *         has fewer samples than {@code options.k()}.
     */
    public static ADASYN fit(double[][] data, int[] labels, Options options) {
        validate(data, labels, options.k());

        // Identify majority and minority classes.
        int minorityLabel = findMinorityLabel(labels);
        int majorityLabel = findMajorityLabel(labels);

        int[] minorityIdx = IntStream.range(0, labels.length)
                .filter(i -> labels[i] == minorityLabel).toArray();
        int nMinority = minorityIdx.length;
        int nMajority = labels.length - nMinority;

        // Total number of synthetic samples G = (nMajority - nMinority) * ratio.
        int G = (int) Math.round((nMajority - nMinority) * options.ratio());
        if (G <= 0) {
            logger.info("ADASYN: classes already balanced or ratio too small; no samples generated.");
            return new ADASYN(data, labels);
        }

        logger.info("ADASYN: minority label={}, minority size={}, majority size={}, G={}",
                minorityLabel, nMinority, nMajority, G);

        double[][] minoritySamples = IntStream.of(minorityIdx)
                .mapToObj(i -> data[i]).toArray(double[][]::new);

        // Build an index over the WHOLE dataset for density ratio estimation.
        KNNSearch<double[], double[]> fullIndex = buildIndex(data, options);

        // Step 1: for each minority sample, count majority neighbors in k-NN of full data.
        int k = Math.min(options.k(), labels.length - 1);
        double[] r = new double[nMinority];
        for (int i = 0; i < nMinority; i++) {
            double[] xi = minoritySamples[i];
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] neighbors = fullIndex.search(xi, k);
            int majorityNeighbors = 0;
            for (Neighbor<double[], double[]> nb : neighbors) {
                // Map neighbor back to its label via index in the original data array.
                // LinearSearch / KDTree store the value == the original array element;
                // we locate the index by identity (same reference).
                if (labels[nb.index()] != minorityLabel) {
                    majorityNeighbors++;
                }
            }
            r[i] = (double) majorityNeighbors / k;
        }

        // Step 2: normalize r̂ so it sums to 1.
        double rSum = 0.0;
        for (double ri : r) rSum += ri;

        if (rSum == 0.0) {
            logger.warn("ADASYN: all minority samples are surrounded only by minority neighbors; "
                    + "no synthetic samples generated. Consider reducing k or using SMOTE.");
            return new ADASYN(data, labels);
        }

        // Step 3: compute per-sample generation count g_i = round(r̂_i * G).
        int[] g = new int[nMinority];
        int totalGenerated = 0;
        for (int i = 0; i < nMinority; i++) {
            g[i] = (int) Math.round((r[i] / rSum) * G);
            totalGenerated += g[i];
        }

        // Due to rounding, totalGenerated may differ from G by a few samples; that is acceptable.
        logger.info("ADASYN: generating {} synthetic samples (requested G={})", totalGenerated, G);

        // Step 4: for each minority sample, generate g_i synthetic samples using
        //         SMOTE-style interpolation within the minority-only neighborhood.
        KNNSearch<double[], double[]> minorityIndex = buildIndex(minoritySamples, options);
        int kMin = Math.min(options.k(), nMinority - 1);

        List<double[]> syntheticList = new ArrayList<>(totalGenerated);
        for (int i = 0; i < nMinority; i++) {
            if (g[i] == 0) continue;

            double[] xi = minoritySamples[i];
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] minNeighbors = minorityIndex.search(xi, kMin);

            for (int s = 0; s < g[i]; s++) {
                // Pick a random minority neighbor.
                double[] xn = minNeighbors[MathEx.randomInt(minNeighbors.length)].value();
                double gap = MathEx.random();
                int d = xi.length;
                double[] syn = new double[d];
                for (int j = 0; j < d; j++) {
                    syn[j] = xi[j] + gap * (xn[j] - xi[j]);
                }
                syntheticList.add(syn);
            }
        }

        // Combine original data with synthetic samples.
        int totalSize = data.length + syntheticList.size();
        double[][] augmentedData   = new double[totalSize][];
        int[]      augmentedLabels = new int[totalSize];

        System.arraycopy(data,   0, augmentedData,   0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int i = 0; i < syntheticList.size(); i++) {
            augmentedData[data.length + i]   = syntheticList.get(i);
            augmentedLabels[data.length + i] = minorityLabel;
        }

        return new ADASYN(augmentedData, augmentedLabels);
    }

    /**
     * Builds a k-NN index over the given samples, choosing between
     * {@link KDTree} and {@link RandomProjectionForest} based on dimensionality.
     */
    private static KNNSearch<double[], double[]> buildIndex(double[][] samples, Options options) {
        int d = samples[0].length;
        if (d <= options.highDimThreshold()) {
            logger.debug("ADASYN: using KDTree for d={}", d);
            return KDTree.of(samples);
        } else {
            logger.debug("ADASYN: using RandomProjectionForest for d={}", d);
            return RandomProjectionForest.of(samples, options.rpfNumTrees(), options.rpfLeafSize(), false);
        }
    }

    /**
     * Returns the label with the fewest samples (minority class).
     * Ties are broken by choosing the smaller label value.
     */
    private static int findMinorityLabel(int[] labels) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) counts.merge(label, 1, Integer::sum);
        return counts.entrySet().stream()
                .min(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow().getKey();
    }

    /**
     * Returns the label with the most samples (majority class).
     * Ties are broken by choosing the larger label value.
     */
    private static int findMajorityLabel(int[] labels) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) counts.merge(label, 1, Integer::sum);
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow().getKey();
    }

    /**
     * Validates the inputs before running ADASYN.
     */
    private static void validate(double[][] data, int[] labels, int k) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException(
                    "data and labels must have the same length: " + data.length + " vs " + labels.length);
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Input data is empty.");
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) counts.merge(label, 1, Integer::sum);
        int minCount = counts.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        if (minCount < k) {
            throw new IllegalArgumentException(String.format(
                    "Minority class has %d samples, which is fewer than k=%d.", minCount, k));
        }
    }
}

