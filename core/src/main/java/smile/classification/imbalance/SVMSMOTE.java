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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.classification.SVM;
import smile.math.MathEx;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.MercerKernel;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.Neighbor;
import smile.neighbor.RandomProjectionForest;

/**
 * SVM-SMOTE — Support Vector Machine guided Synthetic Minority Over-sampling.
 * <p>
 * SVM-SMOTE is a variant of {@link SMOTE} that uses an SVM classifier to
 * identify the most informative minority class samples for synthesis. Rather
 * than interpolating between arbitrary minority pairs, synthesis is restricted
 * to <em>support vectors</em> — the minority samples closest to the decision
 * boundary — which are the hardest to classify and the most likely to benefit
 * from additional training data near the margin.
 * <p>
 * The algorithm proceeds as follows:
 * <ol>
 *   <li>Encode the minority class as {@code +1} and all other classes as
 *       {@code -1}, then train a binary SVM on the full dataset.</li>
 *   <li>Identify minority support vectors: minority samples whose signed
 *       decision function value satisfies
 *       {@code |score(x)| <= 1 + m_factor * (1 − 1/C)}, i.e. samples inside
 *       or close to the margin band. If no minority support vectors are found,
 *       all minority samples are used as seeds.</li>
 *   <li>For each selected seed, find its {@code k} nearest neighbors within
 *       the minority class. Then interpolate to produce a synthetic sample,
 *       choosing the direction depending on whether the randomly selected
 *       neighbor is a support vector:
 *       <ul>
 *         <li>If the neighbor is also a support vector, the synthetic sample
 *             is placed <em>randomly between</em> the seed and the neighbor
 *             (standard SMOTE interpolation).</li>
 *         <li>If the neighbor is not a support vector, the synthetic sample
 *             is placed <em>randomly between</em> the seed and a point
 *             extrapolated <em>away from</em> the interior, pushing synthesis
 *             toward the boundary. Specifically the gap is in
 *             {@code [0, 0.5)} so the sample stays within the safe zone.</li>
 *       </ul>
 *   </li>
 * </ol>
 * <p>
 * <b>Index selection</b><br>
 * When the input dimensionality {@code d <= highDimThreshold} (default 20), a
 * {@link KDTree} is used for exact k-NN search; otherwise a
 * {@link RandomProjectionForest} is used.
 * <p>
 * <b>Limitations</b>
 * <ul>
 *   <li>Feature spaces must be entirely continuous (no categorical features).</li>
 *   <li>Training an SVM adds non-trivial overhead compared to plain SMOTE.</li>
 *   <li>SVM performance depends on the choice of kernel and its parameters.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>H. M. Nguyen, E. W. Cooper and K. Kamei.
 *     Borderline over-sampling for imbalanced data classification.
 *     International Journal of Knowledge Engineering and Soft Data Paradigms,
 *     3(1), 2011.</li>
 * <li>G. E. A. P. A. Batista, R. C. Prati and M. C. Monard.
 *     A study of the behavior of several methods for balancing machine
 *     learning training data. SIGKDD Explorations, 6(1):20–29, 2004.</li>
 * </ol>
 *
 * @param data   the augmented feature matrix (original + synthetic samples).
 * @param labels the augmented labels (original + synthetic sample labels).
 *
 * @author Haifeng Li
 */
public record SVMSMOTE(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SVMSMOTE.class);

    /**
     * SVM-SMOTE hyperparameters.
     *
     * @param k                the number of nearest minority neighbors used
     *                         for interpolation (typically 5).
     * @param ratio            the over-sampling ratio relative to the minority
     *                         class size. {@code 1.0} doubles the minority class,
     *                         {@code 2.0} triples it, etc. Must be positive.
     * @param mFactor          margin factor — scales the SVM margin band used to
     *                         select support vectors. Larger values include more
     *                         minority samples as seeds. Must be non-negative.
     * @param svmC             the soft-margin penalty {@code C} for the internal
     *                         SVM. Smaller values → wider margin → more support
     *                         vectors selected.
     * @param highDimThreshold the dimensionality threshold above which
     *                         {@link RandomProjectionForest} is used instead of
     *                         {@link KDTree} for k-NN search.
     * @param rpfNumTrees      number of trees in the {@link RandomProjectionForest}.
     * @param rpfLeafSize      maximum leaf size for the {@link RandomProjectionForest}.
     */
    public record Options(int k, double ratio, double mFactor, double svmC,
                          int highDimThreshold, int rpfNumTrees, int rpfLeafSize) {

        /** Default constructor: k=5, ratio=1.0, mFactor=10.0, svmC=1.0, highDimThreshold=20,
         *  rpfNumTrees=10, rpfLeafSize=30. */
        public Options() {
            this(5, 1.0, 10.0, 1.0, 20, 10, 30);
        }

        /**
         * Constructor with default SVM and index parameters.
         * @param k     number of nearest neighbors.
         * @param ratio over-sampling ratio.
         */
        public Options(int k, double ratio) {
            this(k, ratio, 10.0, 1.0, 20, 10, 30);
        }

        /** Compact constructor with validation. */
        public Options {
            if (k < 1) {
                throw new IllegalArgumentException("k must be at least 1: " + k);
            }
            if (ratio <= 0.0) {
                throw new IllegalArgumentException("ratio must be positive: " + ratio);
            }
            if (mFactor < 0.0) {
                throw new IllegalArgumentException("mFactor must be non-negative: " + mFactor);
            }
            if (svmC <= 0.0) {
                throw new IllegalArgumentException("svmC must be positive: " + svmC);
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
         * <li><code>smile.svmsmote.k</code>
         * <li><code>smile.svmsmote.ratio</code>
         * <li><code>smile.svmsmote.m_factor</code>
         * <li><code>smile.svmsmote.svm_c</code>
         * <li><code>smile.svmsmote.high_dim_threshold</code>
         * <li><code>smile.svmsmote.rpf_num_trees</code>
         * <li><code>smile.svmsmote.rpf_leaf_size</code>
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.svmsmote.k",                  Integer.toString(k));
            props.setProperty("smile.svmsmote.ratio",              Double.toString(ratio));
            props.setProperty("smile.svmsmote.m_factor",           Double.toString(mFactor));
            props.setProperty("smile.svmsmote.svm_c",              Double.toString(svmC));
            props.setProperty("smile.svmsmote.high_dim_threshold", Integer.toString(highDimThreshold));
            props.setProperty("smile.svmsmote.rpf_num_trees",      Integer.toString(rpfNumTrees));
            props.setProperty("smile.svmsmote.rpf_leaf_size",      Integer.toString(rpfLeafSize));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int    k                = Integer.parseInt(props.getProperty("smile.svmsmote.k",                  "5"));
            double ratio            = Double.parseDouble(props.getProperty("smile.svmsmote.ratio",            "1.0"));
            double mFactor          = Double.parseDouble(props.getProperty("smile.svmsmote.m_factor",         "10.0"));
            double svmC             = Double.parseDouble(props.getProperty("smile.svmsmote.svm_c",            "1.0"));
            int    highDimThreshold = Integer.parseInt(props.getProperty("smile.svmsmote.high_dim_threshold", "20"));
            int    rpfNumTrees      = Integer.parseInt(props.getProperty("smile.svmsmote.rpf_num_trees",      "10"));
            int    rpfLeafSize      = Integer.parseInt(props.getProperty("smile.svmsmote.rpf_leaf_size",      "30"));
            return new Options(k, ratio, mFactor, svmC, highDimThreshold, rpfNumTrees, rpfLeafSize);
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
     * Applies SVM-SMOTE to the given dataset with default {@link Options}
     * and a Gaussian (RBF) kernel with {@code sigma = 1}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return an {@link SVMSMOTE} instance holding the augmented data and labels.
     */
    public static SVMSMOTE fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options(), new GaussianKernel(1.0));
    }

    /**
     * Applies SVM-SMOTE to the given dataset with the given {@link Options}
     * and a Gaussian (RBF) kernel with {@code sigma = 1}.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return an {@link SVMSMOTE} instance holding the augmented data and labels.
     */
    public static SVMSMOTE fit(double[][] data, int[] labels, Options options) {
        return fit(data, labels, options, new GaussianKernel(1.0));
    }

    /**
     * Applies SVM-SMOTE to the given dataset.
     * <p>
     * The minority class (label with the fewest occurrences) is identified
     * automatically. An SVM is trained with the minority class as {@code +1}
     * and all other classes as {@code -1}. Minority support vectors (samples
     * near the decision boundary) are used as seeds for SMOTE interpolation.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @param kernel  the SVM kernel function.
     * @return an {@link SVMSMOTE} instance holding the augmented data and labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} have
     *         different lengths, if the input is empty, or if the minority class
     *         has fewer samples than {@code options.k()}.
     */
    @SuppressWarnings("unchecked")
    public static SVMSMOTE fit(double[][] data, int[] labels, Options options,
                               MercerKernel<double[]> kernel) {
        validate(data, labels, options.k());

        int minorityLabel = findMinorityLabel(labels);
        int[] minorityIdx = IntStream.range(0, labels.length)
                .filter(i -> labels[i] == minorityLabel).toArray();
        int nMinority = minorityIdx.length;

        double[][] minoritySamples = IntStream.of(minorityIdx)
                .mapToObj(i -> data[i]).toArray(double[][]::new);

        int numSynthetic = (int) Math.round(nMinority * options.ratio());
        logger.info("SVM-SMOTE: minority label={}, minority size={}, generating {} synthetic samples",
                minorityLabel, nMinority, numSynthetic);

        // ── Step 1: train binary SVM (minority = +1, rest = -1) ────────────
        int[] svmLabels = Arrays.stream(labels)
                .map(l -> l == minorityLabel ? +1 : -1)
                .toArray();

        var svmOptions = new SVM.Options(options.svmC());
        SVM<double[]> svm = SVM.fit((double[][]) data, svmLabels, kernel, svmOptions);

        // ── Step 2: identify minority support vectors ────────────────────────
        // A minority sample is a support vector when |score| <= margin threshold.
        // margin threshold = 1 + mFactor * (1 - 1/C)  (equivalent to Python impl).
        double marginThreshold = 1.0 + options.mFactor() * (1.0 - 1.0 / options.svmC());

        List<double[]> svSeeds = new ArrayList<>();
        boolean[] isSupportVector = new boolean[nMinority];
        for (int i = 0; i < nMinority; i++) {
            double score = svm.score(minoritySamples[i]);
            if (Math.abs(score) <= marginThreshold) {
                svSeeds.add(minoritySamples[i]);
                isSupportVector[i] = true;
            }
        }

        if (svSeeds.isEmpty()) {
            // Fall back: use all minority samples as seeds.
            logger.info("SVM-SMOTE: no minority support vectors found — using all minority samples as seeds.");
            svSeeds.addAll(Arrays.asList(minoritySamples));
            Arrays.fill(isSupportVector, true);
        }

        logger.info("SVM-SMOTE: {} support vector seeds identified", svSeeds.size());

        // ── Step 3: build minority-only k-NN index ───────────────────────────
        KNNSearch<double[], double[]> minorityIndex = buildIndex(minoritySamples, options);
        int kActual = Math.min(options.k(), nMinority - 1);

        // Build a set of support vector references for fast lookup.
        // We use identity comparison (same array reference).
        java.util.Set<double[]> svSet = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        svSet.addAll(svSeeds);

        // ── Step 4: generate synthetic samples ───────────────────────────────
        double[][] seeds = svSeeds.toArray(new double[0][]);
        int nSeeds = seeds.length;
        double[][] augmentedData   = new double[data.length + numSynthetic][];
        int[]      augmentedLabels = new int[data.length + numSynthetic];

        System.arraycopy(data,   0, augmentedData,   0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int i = 0; i < numSynthetic; i++) {
            double[] seed = seeds[MathEx.randomInt(nSeeds)];

            Neighbor<double[], double[]>[] neighbors = minorityIndex.search(seed, kActual);
            Neighbor<double[], double[]>  nb         = neighbors[MathEx.randomInt(neighbors.length)];
            double[] xn = nb.value();

            double gap;
            if (svSet.contains(xn)) {
                // Both seed and neighbor are support vectors → interpolate between them.
                gap = MathEx.random();
            } else {
                // Neighbor is an interior point → push toward the boundary by
                // extrapolating slightly (gap in [0.5, 1.0) moves toward the seed side).
                gap = 0.5 * MathEx.random();
            }

            int d = seed.length;
            double[] syn = new double[d];
            for (int j = 0; j < d; j++) {
                syn[j] = seed[j] + gap * (xn[j] - seed[j]);
            }

            augmentedData[data.length + i]   = syn;
            augmentedLabels[data.length + i] = minorityLabel;
        }

        return new SVMSMOTE(augmentedData, augmentedLabels);
    }

    /**
     * Builds a k-NN index over the given samples, choosing between
     * {@link KDTree} and {@link RandomProjectionForest} based on dimensionality.
     */
    private static KNNSearch<double[], double[]> buildIndex(double[][] samples, Options options) {
        int d = samples[0].length;
        if (d <= options.highDimThreshold()) {
            logger.debug("SVM-SMOTE: using KDTree for d={}", d);
            return KDTree.of(samples);
        } else {
            logger.debug("SVM-SMOTE: using RandomProjectionForest for d={}", d);
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
     * Validates the inputs before running SVM-SMOTE.
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

