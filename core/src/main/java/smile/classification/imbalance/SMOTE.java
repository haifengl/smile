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

import java.util.Arrays;
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
 * The amount of over-sampling is controlled by the {@code ratio} parameter.
 * A value of 1.0 doubles the minority class (100% over-sampling), 2.0
 * triples it, and so on.
 * <p>
 * <b>Index selection</b><br>
 * When the input dimensionality {@code d <= 20} a {@link KDTree} is used for
 * exact k-nearest-neighbor search. For {@code d > 20} a
 * {@link RandomProjectionForest} (approximate NN) is used instead, because
 * k-d trees suffer from the curse of dimensionality and become no faster than
 * a linear scan in high dimensions.
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
 * @author Haifeng Li
 */
public class SMOTE {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SMOTE.class);

    /**
     * The dimensionality threshold above which {@link RandomProjectionForest}
     * is used instead of {@link KDTree} for k-nearest-neighbor search.
     */
    private static final int HIGH_DIM_THRESHOLD = 20;

    /**
     * Number of trees used in {@link RandomProjectionForest} for high-dimensional data.
     * A value of 10 offers a good balance between recall and build time.
     */
    private static final int RPF_NUM_TREES = 10;

    /**
     * Maximum leaf size for {@link RandomProjectionForest}.
     * Larger leaves improve recall at the cost of search speed.
     */
    private static final int RPF_LEAF_SIZE = 30;

    /** The number of nearest neighbors to consider when generating synthetic samples. */
    private final int k;

    /**
     * The over-sampling ratio relative to the current minority class size.
     * A value of 1.0 means the minority class will be doubled (100% over-sampling).
     */
    private final double ratio;

    /**
     * Constructor with default parameters: {@code k = 5} nearest neighbors
     * and {@code ratio = 1.0} (100% over-sampling, doubling the minority class).
     */
    public SMOTE() {
        this(5, 1.0);
    }

    /**
     * Constructor.
     *
     * @param k     the number of nearest neighbors to consider (typically 5).
     * @param ratio the over-sampling ratio. A value of 1.0 doubles the minority
     *              class size, 2.0 triples it, etc. Must be positive.
     */
    public SMOTE(int k, double ratio) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1: " + k);
        }
        if (ratio <= 0.0) {
            throw new IllegalArgumentException("ratio must be positive: " + ratio);
        }
        this.k = k;
        this.ratio = ratio;
    }

    /**
     * Oversamples the minority class samples using SMOTE and returns the
     * combined dataset (original samples plus synthetic minority samples).
     * <p>
     * The method identifies the minority class as the label with the fewest
     * occurrences, generates synthetic samples for it, and appends them to
     * the original data.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return a two-element array {@code [augmentedData, augmentedLabels]} where
     *         {@code augmentedData} is the combined feature matrix and
     *         {@code augmentedLabels} contains the corresponding labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} have
     *         different lengths, or if the minority class has fewer samples than
     *         {@code k}.
     */
    public double[][][] fit(double[][] data, int[] labels) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException(
                    "data and labels must have the same length: " + data.length + " vs " + labels.length);
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Input data is empty.");
        }

        // Identify minority class (label with fewest samples).
        int minorityLabel = findMinorityLabel(labels);
        double[][] minoritySamples = Arrays.stream(
                        java.util.stream.IntStream.range(0, labels.length)
                                .filter(i -> labels[i] == minorityLabel)
                                .toArray())
                .mapToObj(i -> data[i])
                .toArray(double[][]::new);

        int n = minoritySamples.length;
        if (n < k) {
            throw new IllegalArgumentException(String.format(
                    "Minority class has %d samples, which is fewer than k=%d.", n, k));
        }

        int numSynthetic = (int) Math.round(n * ratio);
        logger.info("SMOTE: minority label={}, minority size={}, generating {} synthetic samples (ratio={})",
                minorityLabel, n, numSynthetic, ratio);

        double[][] synthetic = generateSynthetic(minoritySamples, numSynthetic);

        // Combine original data with synthetic samples.
        int totalSize = data.length + numSynthetic;
        double[][] augmentedData = new double[totalSize][];
        int[] augmentedLabels = new int[totalSize];

        System.arraycopy(data, 0, augmentedData, 0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int i = 0; i < numSynthetic; i++) {
            augmentedData[data.length + i] = synthetic[i];
            augmentedLabels[data.length + i] = minorityLabel;
        }

        return new double[][][] {augmentedData, {Arrays.stream(augmentedLabels)
                .mapToDouble(v -> v).toArray()}};
    }

    /**
     * Oversamples the minority class and returns augmented data and labels
     * as separate arrays.
     *
     * @param data   the input feature matrix.
     * @param labels the class labels.
     * @return a {@link Result} containing the augmented data and labels.
     */
    public Result resample(double[][] data, int[] labels) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException(
                    "data and labels must have the same length: " + data.length + " vs " + labels.length);
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Input data is empty.");
        }

        int minorityLabel = findMinorityLabel(labels);

        int[] minorityIndices = java.util.stream.IntStream.range(0, labels.length)
                .filter(i -> labels[i] == minorityLabel)
                .toArray();
        double[][] minoritySamples = Arrays.stream(minorityIndices)
                .mapToObj(i -> data[i])
                .toArray(double[][]::new);

        int n = minoritySamples.length;
        if (n < k) {
            throw new IllegalArgumentException(String.format(
                    "Minority class has %d samples, which is fewer than k=%d.", n, k));
        }

        int numSynthetic = (int) Math.round(n * ratio);
        logger.info("SMOTE: minority label={}, minority size={}, generating {} synthetic samples (ratio={})",
                minorityLabel, n, numSynthetic, ratio);

        double[][] synthetic = generateSynthetic(minoritySamples, numSynthetic);

        // Combine original data with synthetic samples.
        int totalSize = data.length + numSynthetic;
        double[][] augmentedData = new double[totalSize][];
        int[] augmentedLabels = new int[totalSize];

        System.arraycopy(data, 0, augmentedData, 0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int i = 0; i < numSynthetic; i++) {
            augmentedData[data.length + i] = synthetic[i];
            augmentedLabels[data.length + i] = minorityLabel;
        }

        return new Result(augmentedData, augmentedLabels);
    }

    /**
     * Generates {@code numSynthetic} synthetic samples from the given minority
     * class samples using the SMOTE interpolation strategy.
     * <p>
     * A {@link KDTree} is used when {@code d <= 20}; otherwise a
     * {@link RandomProjectionForest} (approximate NN) is used.
     *
     * @param samples      the minority class feature vectors.
     * @param numSynthetic the number of synthetic samples to generate.
     * @return an array of synthetic feature vectors.
     */
    private double[][] generateSynthetic(double[][] samples, int numSynthetic) {
        int n = samples.length;
        int d = samples[0].length;
        int kActual = Math.min(k, n - 1);  // guard: can't have more neighbors than n-1

        // Choose index structure based on dimensionality.
        KNNSearch<double[], double[]> index;
        if (d <= HIGH_DIM_THRESHOLD) {
            logger.debug("SMOTE: using KDTree for d={}", d);
            index = KDTree.of(samples);
        } else {
            logger.debug("SMOTE: using RandomProjectionForest for d={}", d);
            index = RandomProjectionForest.of(samples, RPF_NUM_TREES, RPF_LEAF_SIZE, false);
        }

        double[][] synthetic = new double[numSynthetic][d];

        for (int i = 0; i < numSynthetic; i++) {
            // Pick a random minority sample as the seed.
            int seedIdx = MathEx.randomInt(n);
            double[] seed = samples[seedIdx];

            // Find k nearest neighbors of the seed within the minority class.
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] neighbors = index.search(seed, kActual);

            // Pick a random neighbor.
            Neighbor<double[], double[]> neighbor = neighbors[MathEx.randomInt(neighbors.length)];
            double[] neighborSample = neighbor.value();

            // Interpolate: synthetic = seed + gap * (neighbor - seed), gap in [0,1)
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
     * Returns the label of the minority class (the class with the fewest samples).
     * In the case of a tie, the label with the smaller integer value is returned.
     *
     * @param labels the class label array.
     * @return the minority class label.
     */
    private static int findMinorityLabel(int[] labels) {
        // Count occurrences per label.
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int label : labels) {
            counts.merge(label, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .min(java.util.Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(java.util.Map.Entry.comparingByKey()))
                .orElseThrow()
                .getKey();
    }

    /**
     * Returns the number of nearest neighbors used for synthesis.
     *
     * @return k
     */
    public int k() {
        return k;
    }

    /**
     * Returns the over-sampling ratio.
     *
     * @return ratio
     */
    public double ratio() {
        return ratio;
    }

    /**
     * The result of SMOTE resampling, containing the augmented feature matrix
     * and the corresponding label array.
     *
     * @param data   the augmented feature matrix (original + synthetic samples).
     * @param labels the augmented labels (original + synthetic sample labels).
     */
    public record Result(double[][] data, int[] labels) {
        /**
         * Returns the total number of samples after resampling.
         *
         * @return the number of rows in {@code data}.
         */
        public int size() {
            return data.length;
        }
    }
}

