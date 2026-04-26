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
package smile.classification.imbalance;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import smile.math.MathEx;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RandomProjectionForest;

/**
 * Synthetic Minority Over-sampling Technique (SMOTE).
 * <p>
 * SMOTE addresses class imbalance by synthetically generating new minority
 * class samples rather than simply duplicating existing ones. For each
 * minority sample, the algorithm selects one of its {@code k} nearest
 * neighbors at random and places a new synthetic sample at a uniformly
 * random position along the line segment connecting the two points in
 * feature space.
 * <p>
 * The amount of over-sampling is controlled by the {@code ratio} parameter
 * in {@link Options}. A value of 1.0 doubles the minority class
 * (100% over-sampling), 2.0 triples it, and so on.
 * <p>
 * <b>Index selection</b><br>
 * When the input dimensionality {@code d <= highDimThreshold} (default 20),
 * a {@link KDTree} is used for exact k-nearest-neighbor search. For higher
 * dimensionality a {@link RandomProjectionForest} (approximate NN) is used
 * instead, because k-d trees suffer from the curse of dimensionality and
 * become no faster than a linear scan in high dimensions.
 * <p>
 * <b>Limitations</b>
 * <ul>
 *   <li>Feature spaces must be entirely continuous (no categorical features).</li>
 *   <li>SMOTE can introduce noise when minority samples already overlap
 *       heavily with the majority class.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>N. V. Chawla, K. W. Bowyer, L. O. Hall, and W. P. Kegelmeyer.
 *     SMOTE: Synthetic Minority Over-sampling Technique.
 *     Journal of Artificial Intelligence Research, 16:321–357, 2002.</li>
 * </ol>
 *
 * @param data   the augmented feature matrix (original + synthetic samples).
 * @param labels the augmented labels (original + synthetic sample labels).
 *
 * @author Haifeng Li
 */
public record SMOTE(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SMOTE.class);

    /**
     * SMOTE hyperparameters.
     *
     * @param k                the number of nearest neighbors to consider
     *                         when generating each synthetic sample (typically 5).
     * @param ratio            the over-sampling ratio relative to the minority
     *                         class size. {@code 1.0} doubles the minority class,
     *                         {@code 2.0} triples it, etc. Must be positive.
     * @param highDimThreshold the dimensionality threshold above which
     *                         {@link RandomProjectionForest} is used instead of
     *                         {@link KDTree} for k-NN search.
     * @param rpfNumTrees      number of trees in the {@link RandomProjectionForest}.
     *                         More trees improve recall at the cost of build time.
     * @param rpfLeafSize      maximum leaf size for the {@link RandomProjectionForest}.
     *                         Larger leaves improve recall at the cost of search speed.
     */
    public record Options(int k, double ratio, int highDimThreshold,
                          int rpfNumTrees, int rpfLeafSize) {
        /** Default constructor: k=5, ratio=1.0, highDimThreshold=20, rpfNumTrees=10, rpfLeafSize=30. */
        public Options() {
            this(5, 1.0, 20, 10, 30);
        }

        /**
         * Constructor: highDimThreshold=20, rpfNumTrees=10, rpfLeafSize=30.
         * @param k                the number of nearest neighbors to consider
         *                         when generating each synthetic sample (typically 5).
         * @param ratio            the over-sampling ratio relative to the minority
         *                         class size. {@code 1.0} doubles the minority class,
         *                         {@code 2.0} triples it, etc. Must be positive.
         */
        public Options(int k, double ratio) {
            this(k, ratio, 20, 10, 30);
        }

        /** Compact constructor with validation. */
        public Options {
            if (k < 1) {
                throw new IllegalArgumentException("k must be at least 1: " + k);
            }
            if (ratio <= 0.0) {
                throw new IllegalArgumentException("ratio must be positive: " + ratio);
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
     * Applies SMOTE to the given dataset with default {@link Options}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return a {@link SMOTE} instance holding the augmented data and labels.
     */
    public static SMOTE fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options());
    }

    /**
     * Applies SMOTE to the given dataset.
     * <p>
     * The minority class (label with the fewest occurrences) is identified
     * automatically. Synthetic samples are generated for it and appended to
     * the original dataset.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return a {@link SMOTE} instance holding the augmented data and labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} have
     *         different lengths, if the input is empty, or if the minority class
     *         has fewer samples than {@code options.k()}.
     */
    public static SMOTE fit(double[][] data, int[] labels, Options options) {
        validate(data, labels, options.k());

        int minorityLabel = findMinorityLabel(labels);
        double[][] minoritySamples = IntStream.range(0, labels.length)
                .filter(i -> labels[i] == minorityLabel)
                .mapToObj(i -> data[i])
                .toArray(double[][]::new);

        int n = minoritySamples.length;
        int numSynthetic = (int) Math.round(n * options.ratio());
        logger.info("SMOTE: minority label={}, minority size={}, generating {} synthetic samples (ratio={})",
                minorityLabel, n, numSynthetic, options.ratio());

        double[][] synthetic = generateSynthetic(minoritySamples, numSynthetic, options);

        int totalSize = data.length + numSynthetic;
        double[][] augmentedData   = new double[totalSize][];
        int[]      augmentedLabels = new int[totalSize];

        System.arraycopy(data,   0, augmentedData,   0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int i = 0; i < numSynthetic; i++) {
            augmentedData[data.length + i]   = synthetic[i];
            augmentedLabels[data.length + i] = minorityLabel;
        }

        return new SMOTE(augmentedData, augmentedLabels);
    }

    /**
     * Generates {@code numSynthetic} synthetic minority-class samples using
     * the SMOTE interpolation strategy.
     * <p>
     * A {@link KDTree} is used when {@code d <= options.highDimThreshold()};
     * otherwise a {@link RandomProjectionForest} (approximate NN) is used.
     *
     * @param samples      the minority class feature vectors.
     * @param numSynthetic the number of synthetic samples to generate.
     * @param options      the hyperparameters.
     * @return an array of synthetic feature vectors.
     */
    private static double[][] generateSynthetic(double[][] samples, int numSynthetic, Options options) {
        int n = samples.length;
        int d = samples[0].length;
        int kActual = Math.min(options.k(), n - 1); // can't have more neighbors than n-1

        // Choose index structure based on dimensionality.
        KNNSearch<double[], double[]> index;
        if (d <= options.highDimThreshold()) {
            logger.debug("SMOTE: using KDTree for d={}", d);
            index = KDTree.of(samples);
        } else {
            logger.debug("SMOTE: using RandomProjectionForest for d={}", d);
            index = RandomProjectionForest.of(samples, options.rpfNumTrees(), options.rpfLeafSize(), false);
        }

        double[][] synthetic = new double[numSynthetic][d];

        for (int i = 0; i < numSynthetic; i++) {
            // Pick a random minority sample as the seed.
            int seedIdx = MathEx.randomInt(n);
            double[] seed = samples[seedIdx];

            // Find k nearest neighbors of the seed within the minority class.
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] neighbors = index.search(seed, kActual);

            // Pick one neighbor at random.
            Neighbor<double[], double[]> neighbor = neighbors[MathEx.randomInt(neighbors.length)];
            double[] neighborSample = neighbor.value();

            // Interpolate: synthetic = seed + gap * (neighbor - seed), gap in [0, 1)
            double gap = MathEx.random();
            double[] syntheticSample = new double[d];
            for (int j = 0; j < d; j++) {
                syntheticSample[j] = seed[j] + gap * (neighborSample[j] - seed[j]);
            }
            synthetic[i] = syntheticSample;
        }

        return synthetic;
    }

    /**
     * Returns the label of the minority class (the class with the fewest
     * samples). In the case of a tie, the smaller label value wins.
     *
     * @param labels the class label array.
     * @return the minority class label.
     */
    private static int findMinorityLabel(int[] labels) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int label : labels) {
            counts.merge(label, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .min(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow()
                .getKey();
    }

    /**
     * Validates the inputs before running SMOTE.
     *
     * @param data   the feature matrix.
     * @param labels the class labels.
     * @param k      the number of nearest neighbors required.
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
