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
 * Borderline Shifting Oversampling (BSO).
 * <p>
 * BSO extends {@link SMOTE} by combining the borderline sample identification
 * from Borderline-SMOTE (Han et al., 2005) with a <em>shifting</em> synthesis
 * mechanism. Instead of interpolating between two minority samples, BSO shifts
 * each borderline minority sample toward the local majority centroid, placing
 * synthetic points directly in the critical region between the classes.
 * <p>
 * <b>Borderline classification</b> — for each minority sample {@code xᵢ},
 * its {@code m} nearest neighbors in the <em>entire</em> dataset are found and
 * the number of majority neighbors {@code mʼ} is counted:
 * <ul>
 *   <li>{@code mʼ = m} → <em>NOISE</em>: entirely surrounded by majority;
 *       never used as a seed.</li>
 *   <li>{@code m/2 ≤ mʼ < m} → <em>DANGER</em>: on the class boundary; the
 *       primary seed candidates for synthesis in both {@code DANGER} and
 *       {@code DANGER_AND_SAFE}.</li>
 *   <li>{@code mʼ < m/2} → <em>SAFE</em>: deep in the minority region;
 *       skipped in {@code DANGER}, used as secondary seeds at a reduced shift
 *       rate in {@code DANGER_AND_SAFE}.</li>
 * </ul>
 * <p>
 * <b>Shifting mechanism</b> — for a seed {@code xᵢ}, its {@code kMaj} nearest
 * <em>majority</em> neighbors are found and their centroid {@code cᵢ} is
 * computed. A synthetic sample is generated as:
 * <pre>
 *   x_syn = xᵢ + α · (cᵢ − xᵢ) + jitter
 * </pre>
 * where {@code α ∈ (0, 1)} is the shift factor and {@code jitter} is a small
 * per-dimension Gaussian noise scaled to a fraction of the shift magnitude to
 * prevent all samples from collapsing to the same point. SAFE seeds in
 * {@code DANGER_AND_SAFE} use {@code α/2} to shift more conservatively.
 * <p>
 * If no DANGER samples exist, all minority samples are used as seeds (fallback
 * identical to plain SMOTE but with shifting).
 * <p>
 * <b>Index selection</b><br>
 * When the dimensionality {@code d ≤ highDimThreshold} (default 20) a
 * {@link KDTree} is used; otherwise a {@link RandomProjectionForest} is used.
 * <p>
 * <b>Limitations</b>
 * <ul>
 *   <li>Feature spaces must be entirely continuous (no categorical features).</li>
 *   <li>A large {@code α} may push synthetic samples deep into the majority
 *       region, potentially introducing ambiguous training examples.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ol>
 * <li> Malhat M.G., Elsobky A.M., Keshk A.E. et al. An approach for handling imbalanced 
 * datasets using borderline shifting. Sci Rep (2026).</li>
 * <li>N. V. Chawla, K. W. Bowyer, L. O. Hall and W. P. Kegelmeyer.
 * <li>H. Han, W. Wang and B. Mao. Borderline-SMOTE: A New Over-Sampling Method
 *     in Imbalanced Data Sets Learning. ICIC, LNCS 3644, 878–887, 2005.</li>
 * <li>N. V. Chawla, K. W. Bowyer, L. O. Hall and W. P. Kegelmeyer.
 *     SMOTE: Synthetic Minority Over-sampling Technique.
 *     JAIR 16:321–357, 2002.</li>
 * </ol>
 *
 * @param data   the augmented feature matrix (original + synthetic samples).
 * @param labels the augmented labels (original + synthetic sample labels).
 *
 * @author Haifeng Li
 */
public record BSO(double[][] data, int[] labels) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BSO.class);

    /**
     * BSO strategy controlling which minority samples are used as seeds.
     */
    public enum Strategy {
        /**
         * Only DANGER samples (borderline minority instances) are used as
         * seeds. Synthesis is concentrated at the class boundary.
         */
        DANGER,
        /**
         * DANGER samples are primary seeds with shift factor {@code α};
         * SAFE (deep interior) samples are secondary seeds with shift
         * factor {@code α/2}. More samples are generated but at a reduced
         * shift magnitude for safe samples.
         */
        DANGER_AND_SAFE
    }

    /**
     * Borderline category of a minority sample.
     */
    private enum Category { NOISE, DANGER, SAFE }

    /**
     * BSO hyperparameters.
     *
     * @param m                neighborhood size used to classify each minority
     *                         sample as NOISE / DANGER / SAFE. Typical value: 5.
     * @param kMaj             number of nearest <em>majority</em> neighbors used
     *                         to compute the shift centroid. Typical value: 3.
     * @param alpha            shift factor {@code α ∈ (0, 1)} — proportion of the
     *                         vector from seed to majority centroid to traverse when
     *                         placing a synthetic sample. Smaller values keep
     *                         synthetics closer to the minority side.
     * @param ratio            over-sampling ratio relative to the minority class
     *                         size. {@code 1.0} doubles the minority class. Must be
     *                         positive.
     * @param strategy         {@link Strategy#DANGER} or {@link Strategy#DANGER_AND_SAFE}.
     * @param highDimThreshold the dimensionality threshold above which
     *                         {@link RandomProjectionForest} is used instead of
     *                         {@link KDTree}.
     * @param rpfNumTrees      number of trees in the {@link RandomProjectionForest}.
     * @param rpfLeafSize      maximum leaf size for the {@link RandomProjectionForest}.
     */
    public record Options(int m, int kMaj, double alpha, double ratio,
                          Strategy strategy,
                          int highDimThreshold, int rpfNumTrees, int rpfLeafSize) {

        /** Default constructor: m=5, kMaj=3, alpha=0.2, ratio=1.0, strategy=DANGER,
         *  highDimThreshold=20, rpfNumTrees=10, rpfLeafSize=30. */
        public Options() {
            this(5, 3, 0.2, 1.0, Strategy.DANGER, 20, 10, 30);
        }

        /**
         * Constructor with core parameters and default index / dimension settings.
         * @param m       neighborhood size for borderline classification.
         * @param kMaj    number of majority neighbors for shift centroid.
         * @param alpha   shift factor.
         * @param ratio   over-sampling ratio.
         * @param variant BSO strategy.
         */
        public Options(int m, int kMaj, double alpha, double ratio, Strategy variant) {
            this(m, kMaj, alpha, ratio, variant, 20, 10, 30);
        }

        /** Compact constructor with validation. */
        public Options {
            if (m < 1) {
                throw new IllegalArgumentException("m must be at least 1: " + m);
            }
            if (kMaj < 1) {
                throw new IllegalArgumentException("kMaj must be at least 1: " + kMaj);
            }
            if (alpha <= 0.0 || alpha >= 1.0) {
                throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
            }
            if (ratio <= 0.0) {
                throw new IllegalArgumentException("ratio must be positive: " + ratio);
            }
            if (strategy == null) {
                throw new IllegalArgumentException("strategy must not be null");
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
         * <li><code>smile.bso.m</code>
         * <li><code>smile.bso.k_maj</code>
         * <li><code>smile.bso.alpha</code>
         * <li><code>smile.bso.ratio</code>
         * <li><code>smile.bso.strategy</code>
         * <li><code>smile.bso.high_dim_threshold</code>
         * <li><code>smile.bso.rpf_num_trees</code>
         * <li><code>smile.bso.rpf_leaf_size</code>
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.bso.m",                  Integer.toString(m));
            props.setProperty("smile.bso.k_maj",              Integer.toString(kMaj));
            props.setProperty("smile.bso.alpha",              Double.toString(alpha));
            props.setProperty("smile.bso.ratio",              Double.toString(ratio));
            props.setProperty("smile.bso.strategy",           strategy.name());
            props.setProperty("smile.bso.high_dim_threshold", Integer.toString(highDimThreshold));
            props.setProperty("smile.bso.rpf_num_trees",      Integer.toString(rpfNumTrees));
            props.setProperty("smile.bso.rpf_leaf_size",      Integer.toString(rpfLeafSize));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            int     m                = Integer.parseInt(props.getProperty("smile.bso.m",                  "5"));
            int     kMaj             = Integer.parseInt(props.getProperty("smile.bso.k_maj",              "3"));
            double  alpha            = Double.parseDouble(props.getProperty("smile.bso.alpha",            "0.2"));
            double  ratio            = Double.parseDouble(props.getProperty("smile.bso.ratio",            "1.0"));
            Strategy strategy        = Strategy.valueOf(props.getProperty("smile.bso.strategy",           "DANGER"));
            int     highDimThreshold = Integer.parseInt(props.getProperty("smile.bso.high_dim_threshold", "20"));
            int     rpfNumTrees      = Integer.parseInt(props.getProperty("smile.bso.rpf_num_trees",      "10"));
            int     rpfLeafSize      = Integer.parseInt(props.getProperty("smile.bso.rpf_leaf_size",      "30"));
            return new Options(m, kMaj, alpha, ratio, strategy, highDimThreshold, rpfNumTrees, rpfLeafSize);
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
     * Applies BSO to the given dataset with default {@link Options}.
     *
     * @param data   the input feature matrix; each row is an observation.
     * @param labels the class labels corresponding to each row of {@code data}.
     * @return a {@link BSO} instance holding the augmented data and labels.
     */
    public static BSO fit(double[][] data, int[] labels) {
        return fit(data, labels, new Options());
    }

    /**
     * Applies BSO to the given dataset.
     * <p>
     * The minority class (label with the fewest occurrences) is identified
     * automatically. Each minority sample is classified as NOISE, DANGER, or
     * SAFE based on its neighborhood composition. Synthetic samples are then
     * generated by shifting DANGER (and optionally SAFE) samples toward the
     * local majority centroid.
     *
     * @param data    the input feature matrix; each row is an observation.
     * @param labels  the class labels corresponding to each row of {@code data}.
     * @param options the hyperparameters.
     * @return a {@link BSO} instance holding the augmented data and labels.
     * @throws IllegalArgumentException if {@code data} and {@code labels} have
     *         different lengths, if the input is empty, or if the minority class
     *         has fewer samples than {@code options.m() + 1}.
     */
    public static BSO fit(double[][] data, int[] labels, Options options) {
        validate(data, labels, options.m());

        int minorityLabel = findMinorityLabel(labels);
        int[] minorityIdx = IntStream.range(0, labels.length)
                .filter(i -> labels[i] == minorityLabel).toArray();
        int nMinority = minorityIdx.length;
        double[][] minoritySamples = IntStream.of(minorityIdx)
                .mapToObj(i -> data[i]).toArray(double[][]::new);

        int numSynthetic = (int) Math.round(nMinority * options.ratio());
        if (numSynthetic == 0) {
            logger.info("BSO: numSynthetic=0, nothing to generate.");
            return new BSO(data, labels);
        }

        logger.info("BSO: minority label={}, minority size={}, generating {} synthetic samples",
                minorityLabel, nMinority, numSynthetic);

        // ── Step 1: build full-dataset index for borderline classification ──
        KNNSearch<double[], double[]> fullIndex = buildIndex(data, options);
        int mActual = Math.min(options.m(), labels.length - 1);

        // ── Step 2: classify each minority sample ───────────────────────────
        Category[] categories = new Category[nMinority];
        for (int i = 0; i < nMinority; i++) {
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] neighbors = fullIndex.search(minoritySamples[i], mActual);
            int majorityCount = 0;
            for (Neighbor<double[], double[]> nb : neighbors) {
                if (labels[nb.index()] != minorityLabel) majorityCount++;
            }
            if (majorityCount == mActual) {
                categories[i] = Category.NOISE;
            } else if (majorityCount >= mActual / 2.0) {
                categories[i] = Category.DANGER;
            } else {
                categories[i] = Category.SAFE;
            }
        }

        // Collect seeds: DANGER always; SAFE only for DANGER_AND_SAFE
        List<Integer> dangerIdx = new ArrayList<>();
        List<Integer> safeIdx   = new ArrayList<>();
        for (int i = 0; i < nMinority; i++) {
            if (categories[i] == Category.DANGER) dangerIdx.add(i);
            else if (categories[i] == Category.SAFE) safeIdx.add(i);
        }

        logger.info("BSO: DANGER={}, SAFE={}, NOISE={}",
                dangerIdx.size(), safeIdx.size(), nMinority - dangerIdx.size() - safeIdx.size());

        // Fall back to all minority samples if no DANGER samples found.
        if (dangerIdx.isEmpty()) {
            logger.warn("BSO: no DANGER samples found — using all minority samples as seeds.");
            for (int i = 0; i < nMinority; i++) dangerIdx.add(i);
        }

        // Build the combined seed list with per-seed effective alpha.
        // Each entry: [minority-array-index, effective-alpha]
        List<int[]>    seedIndices = new ArrayList<>();
        List<Double>   seedAlphas  = new ArrayList<>();
        for (int idx : dangerIdx) {
            seedIndices.add(new int[]{idx});
            seedAlphas.add(options.alpha());
        }
        if (options.strategy() == Strategy.DANGER_AND_SAFE) {
            for (int idx : safeIdx) {
                seedIndices.add(new int[]{idx});
                seedAlphas.add(options.alpha() / 2.0);
            }
        }

        int nSeeds = seedIndices.size();

        // ── Step 3: build majority-only index for shift centroid computation ─
        int[] majorityIdx = IntStream.range(0, labels.length)
                .filter(i -> labels[i] != minorityLabel).toArray();
        double[][] majoritySamples = IntStream.of(majorityIdx)
                .mapToObj(i -> data[i]).toArray(double[][]::new);
        int kMajActual = Math.min(options.kMaj(), majoritySamples.length);
        KNNSearch<double[], double[]> majorityIndex = buildIndex(majoritySamples, options);

        // ── Step 4: generate synthetic samples ──────────────────────────────
        int d = data[0].length;
        double[][] augmentedData   = new double[data.length + numSynthetic][];
        int[]      augmentedLabels = new int[data.length + numSynthetic];
        System.arraycopy(data,   0, augmentedData,   0, data.length);
        System.arraycopy(labels, 0, augmentedLabels, 0, labels.length);

        for (int s = 0; s < numSynthetic; s++) {
            // Pick a random seed.
            int pick        = MathEx.randomInt(nSeeds);
            int minIdx      = seedIndices.get(pick)[0];
            double effAlpha = seedAlphas.get(pick);
            double[] xi     = minoritySamples[minIdx];

            // Compute shift vector toward majority centroid.
            @SuppressWarnings("unchecked")
            Neighbor<double[], double[]>[] majNeighbors = majorityIndex.search(xi, kMajActual);

            double[] centroid = new double[d];
            for (Neighbor<double[], double[]> nb : majNeighbors) {
                for (int j = 0; j < d; j++) centroid[j] += nb.value()[j];
            }
            for (int j = 0; j < d; j++) centroid[j] /= majNeighbors.length;

            // shift_vec = centroid - xi
            double shiftNorm = 0.0;
            double[] shiftVec = new double[d];
            for (int j = 0; j < d; j++) {
                shiftVec[j] = centroid[j] - xi[j];
                shiftNorm  += shiftVec[j] * shiftVec[j];
            }
            shiftNorm = Math.sqrt(shiftNorm);

            // Jitter scale: 5% of shift magnitude per dimension (avoids degenerate stacking).
            double jitterScale = (shiftNorm > 0) ? 0.05 * shiftNorm : 1e-6;

            // x_syn = xi + alpha * shift_vec + jitter
            double[] syn = new double[d];
            for (int j = 0; j < d; j++) {
                double jitter = jitterScale * (MathEx.random() * 2.0 - 1.0); // uniform in [-scale, scale]
                syn[j] = xi[j] + effAlpha * shiftVec[j] + jitter;
            }

            augmentedData[data.length + s]   = syn;
            augmentedLabels[data.length + s] = minorityLabel;
        }

        return new BSO(augmentedData, augmentedLabels);
    }

    /**
     * Builds a k-NN index over the given samples, choosing between
     * {@link KDTree} and {@link RandomProjectionForest} based on dimensionality.
     */
    private static KNNSearch<double[], double[]> buildIndex(double[][] samples, Options options) {
        int d = samples[0].length;
        if (d <= options.highDimThreshold()) {
            logger.debug("BSO: using KDTree for d={}", d);
            return KDTree.of(samples);
        } else {
            logger.debug("BSO: using RandomProjectionForest for d={}", d);
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
     * Validates the inputs before running BSO.
     */
    private static void validate(double[][] data, int[] labels, int m) {
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
        if (minCount <= m) {
            throw new IllegalArgumentException(String.format(
                    "Minority class has %d samples; need more than m=%d samples.", minCount, m));
        }
    }
}

