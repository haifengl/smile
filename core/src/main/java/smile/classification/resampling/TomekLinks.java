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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import smile.neighbor.KDTree;
import smile.neighbor.KNNSearch;
import smile.neighbor.RandomProjectionForest;

/**
 * Tomek Links under-sampling.
 * <p>
 * A <em>Tomek link</em> is a pair of samples {@code (xᵢ, xⱼ)} from different
 * classes such that no other sample {@code xₖ} exists that is closer to
 * {@code xᵢ} than {@code xⱼ} is, and closer to {@code xⱼ} than {@code xᵢ}
 * is. In other words, {@code xᵢ} and {@code xⱼ} are each other's nearest
 * neighbor and they belong to different classes.
 * <p>
 * Tomek links tend to be either:
 * <ul>
 *   <li><em>Noisy samples</em> — misclassified points deep in the wrong
 *       class region.</li>
 *   <li><em>Borderline samples</em> — samples near the class boundary
 *       that are hardest to classify.</li>
 * </ul>
 * <p>
 * This implementation removes only the <em>majority</em> class member of
 * each detected link, thereby cleaning the class boundary without reducing
 * the minority class size. The cleaned dataset is stored in this record.
 * <p>
 * <b>Complexity</b><br>
 * Nearest-neighbor search dominates: {@code O(n log n)} with a
 * {@link KDTree}, or approximate {@link RandomProjectionForest} for
 * high-dimensional data ({@code d > highDimThreshold}).
 * <p>
 * <b>Limitations</b>
 * <ul>
 *   <li>Only continuous (numeric) features are supported.</li>
 *   <li>In very high dimensions k-d trees degrade to linear scan; the
 *       approximate RPForest index is activated automatically.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li>I. Tomek. Two modifications of CNN.
 *     IEEE Transactions on Systems, Man, and Cybernetics, 6:769–772, 1976.
 * </li>
 * <li>G. E. A. P. A. Batista, R. C. Prati and M. C. Monard.
 *     A study of the behavior of several methods for balancing machine
 *     learning training data. SIGKDD Explorations, 6(1):20–29, 2004.
 * </li>
 * </ol>
 *
 * @param data   the cleaned feature matrix (majority Tomek-link members removed).
 * @param labels the corresponding class labels.
 *
 * @author Haifeng Li
 */
public record TomekLinks(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TomekLinks.class);

    /**
     * TomekLinks hyperparameters.
     *
     * @param highDimThreshold the dimensionality threshold above which
     *                         {@link RandomProjectionForest} is used instead of
     *                         {@link KDTree} for nearest-neighbor search.
     * @param rpfNumTrees      number of trees in the {@link RandomProjectionForest}.
     * @param rpfLeafSize      maximum leaf size for the {@link RandomProjectionForest}.
     */
    public record Options(int highDimThreshold, int rpfNumTrees, int rpfLeafSize) {

        /** Default constructor: highDimThreshold=20, rpfNumTrees=10, rpfLeafSize=30. */
        public Options() {
            this(20, 10, 30);
        }

        /** Compact constructor with validation. */
        public Options {
            if (highDimThreshold < 1) {
                throw new IllegalArgumentException(
                        "highDimThreshold must be at least 1: " + highDimThreshold);
            }
            if (rpfNumTrees < 1) {
                throw new IllegalArgumentException(
                        "rpfNumTrees must be at least 1: " + rpfNumTrees);
            }
            if (rpfLeafSize < 1) {
                throw new IllegalArgumentException(
                        "rpfLeafSize must be at least 1: " + rpfLeafSize);
            }
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.tomek.high_dim_threshold</code>
         * <li><code>smile.tomek.rpf_num_trees</code>
         * <li><code>smile.tomek.rpf_leaf_size</code>
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.tomek.high_dim_threshold", Integer.toString(highDimThreshold));
            props.setProperty("smile.tomek.rpf_num_trees",      Integer.toString(rpfNumTrees));
            props.setProperty("smile.tomek.rpf_leaf_size",      Integer.toString(rpfLeafSize));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int highDimThreshold = Integer.parseInt(
                    props.getProperty("smile.tomek.high_dim_threshold", "20"));
            int rpfNumTrees      = Integer.parseInt(
                    props.getProperty("smile.tomek.rpf_num_trees",      "10"));
            int rpfLeafSize      = Integer.parseInt(
                    props.getProperty("smile.tomek.rpf_leaf_size",      "30"));
            return new Options(highDimThreshold, rpfNumTrees, rpfLeafSize);
        }
    }

    /**
     * Returns the number of samples after cleaning.
     *
     * @return the number of rows in {@code data}.
     */
    public int size() {
        return data.length;
    }

    /**
     * Applies Tomek Links cleaning with default {@link Options}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return a {@link TomekLinks} record holding the cleaned data and labels.
     */
    public static TomekLinks fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options());
    }

    /**
     * Applies Tomek Links cleaning to the given dataset.
     * <p>
     * The minority class is identified automatically as the label with the
     * fewest occurrences. For every sample, its nearest neighbor is found. If
     * the nearest neighbor belongs to a different class <em>and</em> the
     * relationship is mutual (i.e. they form a Tomek link), the
     * <em>majority-class member</em> of that pair is marked for removal.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return a {@link TomekLinks} record holding the cleaned data and labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} differ
     *         in length or the dataset is empty.
     */
    public static TomekLinks fit(double[][] data, int[] labels, Options options) {
        if (data.length != labels.length) {
            throw new IllegalArgumentException(
                    "data and labels must have the same length: "
                            + data.length + " vs " + labels.length);
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Input data is empty.");
        }

        int n = data.length;
        int minorityLabel = findMinorityLabel(labels);

        // ── Step 1: find the nearest neighbor of every sample ───────────────
        // We build a single index over all samples and search for k=1 neighbor
        // (the index automatically excludes the query point itself).
        KNNSearch<double[], double[]> index = buildIndex(data, options);

        // nearestIdx[i] = index of the nearest neighbor of sample i in `data`.
        // We use the Neighbor.index() field for direct array lookup.
        int[] nearestIdx = new int[n];
        for (int i = 0; i < n; i++) {
            var nb = index.nearest(data[i]);
            nearestIdx[i] = nb.index();
        }

        // ── Step 2: detect Tomek links and mark majority members ─────────────
        // A pair (i, j) is a Tomek link iff:
        //   nearestIdx[i] == j  AND  nearestIdx[j] == i  AND  labels[i] != labels[j]
        // We only remove the majority-class member.
        boolean[] remove = new boolean[n];
        int linkCount    = 0;

        for (int i = 0; i < n; i++) {
            int j = nearestIdx[i];
            if (labels[i] != labels[j]          // different classes
                    && nearestIdx[j] == i       // mutual nearest neighbors
                    && !remove[i] && !remove[j]) // not already flagged
            {
                // Mark the majority member for removal.
                if (labels[i] != minorityLabel) {
                    remove[i] = true;
                } else {
                    remove[j] = true;
                }
                linkCount++;
            }
        }

        logger.info("TomekLinks: {} Tomek links detected; {} majority samples removed.",
                linkCount, linkCount);

        // ── Step 3: collect kept samples ─────────────────────────────────────
        int kept = 0;
        for (boolean r : remove) if (!r) kept++;

        double[][] cleanData   = new double[kept][];
        int[]      cleanLabels = new int[kept];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (!remove[i]) {
                cleanData[idx]   = data[i];
                cleanLabels[idx] = labels[i];
                idx++;
            }
        }

        return new TomekLinks(cleanData, cleanLabels);
    }

    /**
     * Builds a nearest-neighbor index over the given samples, choosing between
     * {@link KDTree} and {@link RandomProjectionForest} based on dimensionality.
     */
    private static KNNSearch<double[], double[]> buildIndex(double[][] samples, Options options) {
        int d = samples[0].length;
        if (d <= options.highDimThreshold()) {
            logger.debug("TomekLinks: using KDTree for d={}", d);
            return KDTree.of(samples);
        } else {
            logger.debug("TomekLinks: using RandomProjectionForest for d={}", d);
            return RandomProjectionForest.of(
                    samples, options.rpfNumTrees(), options.rpfLeafSize(), false);
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
}

