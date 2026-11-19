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
package smile.stat.hypothesis;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.special.Gamma;
import smile.sort.IQAgent;

/**
 * Population Stability Index (PSI) and categorical frequency shift test.
 * PSI is a symmetric divergence metric (closely related to Jeffrey's divergence,
 * {@code D_KL(Q || P) + D_KL(P || Q)}) widely used in production machine learning
 * and credit risk monitoring to quantify how much a variable's distribution has
 * shifted between a baseline (training/reference) sample and an actual
 * (production/monitoring) window:
 * <pre>
 * PSI = &sum; (q_i - p_i) * ln(q_i / p_i)
 * </pre>
 * where {@code p_i} is the baseline proportion in bin {@code i} and {@code q_i}
 * is the actual proportion in bin {@code i}.
 *
 * <table>
 *     <caption>Standard Interpretation of PSI</caption>
 *     <tr><th>PSI Range</th><th>Severity</th><th>Interpretation</th></tr>
 *     <tr><td>&lt; 0.10</td><td>{@link Severity#STABLE}</td><td>No significant distribution change</td></tr>
 *     <tr><td>[0.10, 0.25)</td><td>{@link Severity#MODERATE}</td><td>Moderate shift; investigate</td></tr>
 *     <tr><td>&ge; 0.25</td><td>{@link Severity#SIGNIFICANT}</td><td>Significant drift; action required</td></tr>
 * </table>
 *
 * <p>This class supports both stateless batch comparisons (on raw numerical arrays,
 * categorical arrays, pre-binned counts, or {@link DataFrame}s) and constant-memory
 * streaming accumulation via {@link Baseline} and {@link Counter}.
 *
 * @param feature the feature or variable name.
 * @param psi the total Population Stability Index across all bins.
 * @param binPsi the per-bin PSI contributions.
 * @param expectedProb the baseline (expected) empirical proportions per bin.
 * @param actualProb the actual (monitored) empirical proportions per bin.
 * @param breaks the interior breakpoints for numerical features ({@code null} for categorical).
 * @param expectedCount the total baseline observation count (0 if constructed from probabilities only).
 * @param actualCount the total actual observation count.
 * @param tvd the Total Variation Distance {@code 0.5 * sum(|q_i - p_i|)} in [0, 1].
 * @param chisq the associated Pearson's chi-square test across the bins.
 * @author Haifeng Li
 */
public record PSI(
        String feature,
        double psi,
        double[] binPsi,
        double[] expectedProb,
        double[] actualProb,
        double[] breaks,
        long expectedCount,
        long actualCount,
        double tvd,
        ChiSqTest chisq
) implements Comparable<PSI>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Default number of quantile bins (deciles) for continuous features. */
    public static final int DEFAULT_BINS = 10;

    /** PSI threshold at or above which drift is considered moderate. */
    public static final double MODERATE_THRESHOLD = 0.10;

    /** PSI threshold at or above which drift is considered significant. */
    public static final double SIGNIFICANT_THRESHOLD = 0.25;

    /** Laplace smoothing floor for zero-count bins when sample count is known. */
    private static final double MIN_BIN_COUNT = 0.5;

    /** Probability smoothing floor when baseline is specified as probabilities only. */
    private static final double MIN_PROB_FLOOR = 1.0e-4;

    /** Default feature label for unnamed single-array tests. */
    private static final String DEFAULT_FEATURE_NAME = "x";

    /**
     * Drift severity classification based on standard PSI thresholds.
     */
    public enum Severity {
        /** No significant distribution shift ({@code PSI < 0.10}). */
        STABLE,
        /** Moderate distribution shift ({@code 0.10 <= PSI < 0.25}). */
        MODERATE,
        /** Significant distribution drift ({@code PSI >= 0.25}). */
        SIGNIFICANT
    }

    /**
     * Returns the drift severity classification for this PSI result.
     * @return the drift severity.
     */
    public Severity severity() {
        return severity(psi);
    }

    /**
     * Returns the drift severity classification for a given PSI value.
     * @param psi the PSI value.
     * @return the drift severity.
     */
    public static Severity severity(double psi) {
        if (psi < MODERATE_THRESHOLD) {
            return Severity.STABLE;
        } else if (psi < SIGNIFICANT_THRESHOLD) {
            return Severity.MODERATE;
        } else {
            return Severity.SIGNIFICANT;
        }
    }

    @Override
    public int compareTo(PSI other) {
        return Double.compare(this.psi, other.psi);
    }

    @Override
    public String toString() {
        return String.format("PSI(%s, psi = %.4f, tvd = %.4f, severity = %s)",
                feature, psi, tvd, severity());
    }

    /**
     * Formats an array of feature PSI results as a summary table.
     *
     * @param results the array of PSI results.
     * @return a formatted table string.
     */
    public static String toString(PSI[] results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature                             PSI         TVD       ChiSq     p-value    Severity\n");
        for (PSI r : results) {
            double chisqVal = r.chisq != null ? r.chisq.chisq() : Double.NaN;
            double pval = r.chisq != null ? r.chisq.pvalue() : Double.NaN;
            sb.append(String.format("%-25s %13.4f %11.4f %11.4f %11.4g    %-11s%n",
                    r.feature, r.psi, r.tvd, chisqVal, pval, r.severity()));
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Stateless Batch Methods — Pre-binned & Categorical
    // -----------------------------------------------------------------------

    /**
     * Computes PSI from expected bin probabilities and observed bin counts.
     *
     * @param expectedProb the baseline bin probabilities (must sum to ~1.0).
     * @param actualBins the observed counts per bin in the monitoring window.
     * @return the PSI result.
     */
    public static PSI test(double[] expectedProb, int[] actualBins) {
        return test(DEFAULT_FEATURE_NAME, expectedProb, toLongArray(actualBins));
    }

    /**
     * Computes PSI from expected bin probabilities and observed bin counts.
     *
     * @param feature the feature name.
     * @param expectedProb the baseline bin probabilities (must sum to ~1.0).
     * @param actualBins the observed counts per bin in the monitoring window.
     * @return the PSI result.
     */
    public static PSI test(String feature, double[] expectedProb, long[] actualBins) {
        Objects.requireNonNull(feature, "feature cannot be null");
        if (expectedProb == null || actualBins == null || expectedProb.length == 0) {
            throw new IllegalArgumentException("Empty bin arrays");
        }
        if (expectedProb.length != actualBins.length) {
            throw new IllegalArgumentException("Bin arrays have different lengths");
        }
        return computeFromProbAndBins(feature, expectedProb, null, 0L, actualBins, null);
    }

    /**
     * Computes PSI from two sets of binned counts (baseline vs. actual).
     *
     * @param expectedBins the baseline bin counts.
     * @param actualBins the actual bin counts.
     * @return the PSI result.
     */
    public static PSI test(int[] expectedBins, int[] actualBins) {
        return test(DEFAULT_FEATURE_NAME, toLongArray(expectedBins), toLongArray(actualBins));
    }

    /**
     * Computes PSI from two sets of binned counts (baseline vs. actual).
     *
     * @param expectedBins the baseline bin counts.
     * @param actualBins the actual bin counts.
     * @return the PSI result.
     */
    public static PSI test(long[] expectedBins, long[] actualBins) {
        return test(DEFAULT_FEATURE_NAME, expectedBins, actualBins);
    }

    /**
     * Computes PSI from two sets of binned counts (baseline vs. actual).
     *
     * @param feature the feature name.
     * @param expectedBins the baseline bin counts.
     * @param actualBins the actual bin counts.
     * @return the PSI result.
     */
    public static PSI test(String feature, long[] expectedBins, long[] actualBins) {
        Objects.requireNonNull(feature, "feature cannot be null");
        if (expectedBins == null || actualBins == null || expectedBins.length == 0) {
            throw new IllegalArgumentException("Empty bin arrays");
        }
        if (expectedBins.length != actualBins.length) {
            throw new IllegalArgumentException("Bin arrays have different lengths");
        }
        long expectedTotal = sumNonNegative(expectedBins);
        if (expectedTotal == 0L) {
            throw new IllegalArgumentException("Expected bin counts sum to zero");
        }
        double[] expectedProb = new double[expectedBins.length];
        for (int i = 0; i < expectedBins.length; i++) {
            expectedProb[i] = (double) expectedBins[i] / expectedTotal;
        }
        return computeFromProbAndBins(feature, expectedProb, expectedBins, expectedTotal, actualBins, null);
    }

    /**
     * Computes categorical frequency shift (PSI, TVD, and Chi-Square) from two
     * arrays of non-negative integer category codes. The number of categories
     * is inferred as {@code max(expected, actual) + 1}.
     *
     * @param expected the baseline categorical observations.
     * @param actual the actual categorical observations.
     * @return the PSI result.
     */
    public static PSI testCategorical(int[] expected, int[] actual) {
        if (expected == null || expected.length == 0 || actual == null || actual.length == 0) {
            throw new IllegalArgumentException("Empty categorical sample array");
        }
        int maxCat = 0;
        for (int c : expected) {
            if (c < 0) throw new IllegalArgumentException("Negative category code: " + c);
            if (c > maxCat) maxCat = c;
        }
        for (int c : actual) {
            if (c < 0) throw new IllegalArgumentException("Negative category code: " + c);
            if (c > maxCat) maxCat = c;
        }
        return testCategorical(DEFAULT_FEATURE_NAME, expected, actual, maxCat + 1);
    }

    /**
     * Computes categorical frequency shift (PSI, TVD, and Chi-Square) from two
     * arrays of non-negative integer category codes.
     *
     * @param expected the baseline categorical observations.
     * @param actual the actual categorical observations.
     * @param numCategories the number of nominal categories.
     * @return the PSI result.
     */
    public static PSI testCategorical(int[] expected, int[] actual, int numCategories) {
        return testCategorical(DEFAULT_FEATURE_NAME, expected, actual, numCategories);
    }

    /**
     * Computes categorical frequency shift (PSI, TVD, and Chi-Square) from two
     * arrays of non-negative integer category codes.
     *
     * @param feature the feature name.
     * @param expected the baseline categorical observations.
     * @param actual the actual categorical observations.
     * @param numCategories the number of nominal categories.
     * @return the PSI result.
     */
    public static PSI testCategorical(String feature, int[] expected, int[] actual, int numCategories) {
        Baseline baseline = Baseline.categorical(feature, expected, numCategories);
        return baseline.testCategorical(actual);
    }

    // -----------------------------------------------------------------------
    // Stateless Batch Methods — Continuous Numerical Arrays
    // -----------------------------------------------------------------------

    /**
     * Computes PSI between a baseline and an actual continuous sample using
     * {@link #DEFAULT_BINS} (10) quantile bins fitted on {@code expected}.
     *
     * @param expected the baseline sample values.
     * @param actual the actual sample values.
     * @return the PSI result.
     */
    public static PSI test(double[] expected, double[] actual) {
        return test(DEFAULT_FEATURE_NAME, expected, actual, DEFAULT_BINS);
    }

    /**
     * Computes PSI between a baseline and an actual continuous sample using
     * {@code nbins} quantile bins fitted on {@code expected}.
     *
     * @param expected the baseline sample values.
     * @param actual the actual sample values.
     * @param nbins the number of quantile bins (&ge; 2).
     * @return the PSI result.
     */
    public static PSI test(double[] expected, double[] actual, int nbins) {
        return test(DEFAULT_FEATURE_NAME, expected, actual, nbins);
    }

    /**
     * Computes PSI between a baseline and an actual continuous sample using
     * {@code nbins} quantile bins fitted on {@code expected}.
     *
     * @param feature the feature name.
     * @param expected the baseline sample values.
     * @param actual the actual sample values.
     * @param nbins the number of quantile bins (&ge; 2).
     * @return the PSI result.
     */
    public static PSI test(String feature, double[] expected, double[] actual, int nbins) {
        Baseline baseline = Baseline.numeric(feature, expected, nbins);
        return baseline.test(actual);
    }

    /**
     * Computes PSI between a baseline and an actual continuous sample using
     * caller-specified interior breakpoints {@code breaks}. The {@code k - 1}
     * breakpoints define {@code k} intervals:
     * {@code (-∞, breaks[0]), [breaks[0], breaks[1]), ..., [breaks[k-2], +∞)}.
     *
     * @param expected the baseline sample values.
     * @param actual the actual sample values.
     * @param breaks strictly increasing interior breakpoints.
     * @return the PSI result.
     */
    public static PSI test(double[] expected, double[] actual, double[] breaks) {
        return test(DEFAULT_FEATURE_NAME, expected, actual, breaks);
    }

    /**
     * Computes PSI between a baseline and an actual continuous sample using
     * caller-specified interior breakpoints {@code breaks}.
     *
     * @param feature the feature name.
     * @param expected the baseline sample values.
     * @param actual the actual sample values.
     * @param breaks strictly increasing interior breakpoints.
     * @return the PSI result.
     */
    public static PSI test(String feature, double[] expected, double[] actual, double[] breaks) {
        Baseline baseline = Baseline.numeric(feature, expected, breaks);
        return baseline.test(actual);
    }

    // -----------------------------------------------------------------------
    // Stateless Batch Methods — DataFrame
    // -----------------------------------------------------------------------

    /**
     * Computes PSI for all numeric and nominal columns shared between a baseline
     * {@link DataFrame} and an actual {@link DataFrame} using {@link #DEFAULT_BINS}
     * quantile bins for numeric columns.
     *
     * @param expected the baseline DataFrame.
     * @param actual the actual DataFrame.
     * @return an array of per-feature PSI results.
     */
    public static PSI[] test(DataFrame expected, DataFrame actual) {
        return test(expected, actual, DEFAULT_BINS);
    }

    /**
     * Computes PSI for all numeric and nominal columns shared between a baseline
     * {@link DataFrame} and an actual {@link DataFrame}.
     *
     * @param expected the baseline DataFrame.
     * @param actual the actual DataFrame.
     * @param nbins the number of quantile bins for numeric columns (&ge; 2).
     * @return an array of per-feature PSI results.
     */
    public static PSI[] test(DataFrame expected, DataFrame actual, int nbins) {
        Baseline[] baselines = Baseline.fit(expected, nbins);
        return Arrays.stream(baselines)
                .map(b -> b.test(actual))
                .toArray(PSI[]::new);
    }

    // -----------------------------------------------------------------------
    // Streaming Baseline & Windowed Counter
    // -----------------------------------------------------------------------

    /**
     * Immutable reference distribution summary for a single numeric or categorical
     * feature. Once fitted on training/baseline data, a {@code Baseline} can spawn
     * lightweight {@link Counter} accumulators to monitor streaming inference windows
     * in constant memory ({@code O(bins)}) and zero per-record heap allocations.
     */
    public static final class Baseline implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** The feature name. */
        private final String feature;
        /** The expected bin probabilities. */
        private final double[] expectedProb;
        /** The expected bin observation counts. */
        private final long[] expectedBins;
        /** The interior numeric breakpoints, or {@code null} for categorical features. */
        private final double[] breaks;
        /** The total baseline observation count. */
        private final long expectedCount;

        private Baseline(
                String feature,
                double[] expectedProb,
                long[] expectedBins,
                double[] breaks,
                long expectedCount
        ) {
            this.feature = Objects.requireNonNull(feature, "feature cannot be null");
            this.expectedProb = expectedProb;
            this.expectedBins = expectedBins;
            this.breaks = breaks;
            this.expectedCount = expectedCount;
        }

        /**
         * Fits a numeric PSI baseline using {@link #DEFAULT_BINS} quantile bins.
         * @param baseline the baseline numeric observations.
         * @return the fitted baseline.
         */
        public static Baseline numeric(double[] baseline) {
            return numeric(DEFAULT_FEATURE_NAME, baseline, DEFAULT_BINS);
        }

        /**
         * Fits a numeric PSI baseline using {@code nbins} quantile bins.
         * Tied quantile breakpoints (e.g., from zero-inflated or discrete-valued
         * features) are automatically deduplicated so interior breakpoints are
         * strictly increasing.
         *
         * @param feature the feature name.
         * @param baseline the baseline numeric observations.
         * @param nbins the target number of quantile bins (&ge; 2).
         * @return the fitted baseline.
         */
        public static Baseline numeric(String feature, double[] baseline, int nbins) {
            Objects.requireNonNull(feature, "feature cannot be null");
            if (nbins < 2) {
                throw new IllegalArgumentException("Invalid number of bins: " + nbins);
            }
            double[] clean = filterFinite(baseline);
            if (clean.length == 0) {
                throw new IllegalArgumentException("Empty baseline array");
            }
            Arrays.sort(clean);
            double[] breaks = extractUniqueQuantileBreaks(clean, nbins);
            return buildNumericBaselineFromSorted(feature, clean, breaks);
        }

        /**
         * Fits a numeric PSI baseline using caller-supplied interior breakpoints.
         *
         * @param feature the feature name.
         * @param baseline the baseline numeric observations.
         * @param breaks strictly increasing interior breakpoints.
         * @return the fitted baseline.
         */
        public static Baseline numeric(String feature, double[] baseline, double[] breaks) {
            Objects.requireNonNull(feature, "feature cannot be null");
            validateBreaks(breaks);
            double[] clean = filterFinite(baseline);
            if (clean.length == 0) {
                throw new IllegalArgumentException("Empty baseline array");
            }
            double[] breaksCopy = breaks.clone();
            long[] bins = new long[breaksCopy.length + 1];
            for (double v : clean) {
                bins[locateNumericBin(breaksCopy, v)]++;
            }
            double[] prob = new double[bins.length];
            for (int i = 0; i < bins.length; i++) {
                prob[i] = (double) bins[i] / clean.length;
            }
            return new Baseline(feature, prob, bins, breaksCopy, clean.length);
        }

        /**
         * Fits a numeric PSI baseline directly from a streaming {@link IQAgent}
         * quantile sketch using {@code nbins} quantile bins.
         *
         * @param feature the feature name.
         * @param sketch the populated baseline quantile sketch.
         * @param nbins the target number of quantile bins (&ge; 2).
         * @return the fitted baseline.
         */
        public static Baseline numeric(String feature, IQAgent sketch, int nbins) {
            Objects.requireNonNull(feature, "feature cannot be null");
            if (sketch == null || sketch.isEmpty()) {
                throw new IllegalArgumentException("Empty IQAgent sketch");
            }
            if (nbins < 2) {
                throw new IllegalArgumentException("Invalid number of bins: " + nbins);
            }
            double[] rawBreaks = new double[nbins - 1];
            for (int j = 0; j < nbins - 1; j++) {
                rawBreaks[j] = sketch.quantile((double) (j + 1) / nbins);
            }
            double[] breaks = deduplicateSorted(rawBreaks);
            if (breaks.length == 0) {
                breaks = new double[] { sketch.min() };
            }
            int k = breaks.length + 1;
            long total = sketch.count();
            double[] prob = new double[k];
            long[] bins = new long[k];
            double prevCdf = 0.0;
            for (int j = 0; j < k; j++) {
                double currCdf = (j < breaks.length) ? sketch.cdf(breaks[j]) : 1.0;
                double p = Math.max(0.0, currCdf - prevCdf);
                prob[j] = p;
                bins[j] = Math.round(p * total);
                prevCdf = currCdf;
            }
            normalizeProbabilities(prob);
            return new Baseline(feature, prob, bins, breaks, total);
        }

        /**
         * Fits a categorical PSI baseline from non-negative integer category codes.
         *
         * @param baseline the baseline category codes.
         * @return the fitted baseline.
         */
        public static Baseline categorical(int[] baseline) {
            if (baseline == null || baseline.length == 0) {
                throw new IllegalArgumentException("Empty categorical baseline array");
            }
            int maxCat = 0;
            for (int c : baseline) {
                if (c < 0) throw new IllegalArgumentException("Negative category code: " + c);
                if (c > maxCat) maxCat = c;
            }
            return categorical(DEFAULT_FEATURE_NAME, baseline, maxCat + 1);
        }

        /**
         * Fits a categorical PSI baseline from non-negative integer category codes.
         *
         * @param feature the feature name.
         * @param baseline the baseline category codes.
         * @param numCategories the number of nominal categories (&ge; 2).
         * @return the fitted baseline.
         */
        public static Baseline categorical(String feature, int[] baseline, int numCategories) {
            Objects.requireNonNull(feature, "feature cannot be null");
            if (baseline == null || baseline.length == 0) {
                throw new IllegalArgumentException("Empty categorical baseline array");
            }
            if (numCategories < 2) {
                throw new IllegalArgumentException("Invalid number of categories: " + numCategories);
            }
            long[] bins = new long[numCategories];
            for (int c : baseline) {
                if (c < 0 || c >= numCategories) {
                    throw new IllegalArgumentException("Category code out of range [0, " + numCategories + "): " + c);
                }
                bins[c]++;
            }
            double[] prob = new double[numCategories];
            for (int i = 0; i < numCategories; i++) {
                prob[i] = (double) bins[i] / baseline.length;
            }
            return new Baseline(feature, prob, bins, null, baseline.length);
        }

        /**
         * Creates a baseline directly from pre-computed expected bin probabilities
         * and optional interior breakpoints ({@code null} for categorical).
         *
         * @param feature the feature name.
         * @param expectedProb the expected bin probabilities (length {@code k}).
         * @param breaks the interior breakpoints of length {@code k - 1}, or {@code null} for categorical.
         * @return the baseline.
         */
        public static Baseline of(String feature, double[] expectedProb, double[] breaks) {
            Objects.requireNonNull(feature, "feature cannot be null");
            if (expectedProb == null || expectedProb.length < 2) {
                throw new IllegalArgumentException("expectedProb must have at least 2 bins");
            }
            double[] probCopy = expectedProb.clone();
            normalizeProbabilities(probCopy);
            double[] breaksCopy = null;
            if (breaks != null) {
                if (breaks.length != probCopy.length - 1) {
                    throw new IllegalArgumentException(
                            "breaks length (" + breaks.length + ") must equal expectedProb.length - 1 (" + (probCopy.length - 1) + ")");
                }
                validateBreaks(breaks);
                breaksCopy = breaks.clone();
            }
            return new Baseline(feature, probCopy, null, breaksCopy, 0L);
        }

        /**
         * Fits PSI baselines for all numeric and nominal columns in a {@link DataFrame}
         * using {@link #DEFAULT_BINS} quantile bins for numeric columns.
         *
         * @param data the baseline DataFrame.
         * @return an array of fitted baselines.
         */
        public static Baseline[] fit(DataFrame data) {
            return fit(data, DEFAULT_BINS);
        }

        /**
         * Fits PSI baselines for all numeric and nominal columns in a {@link DataFrame}.
         *
         * @param data the baseline DataFrame.
         * @param nbins the number of quantile bins for numeric columns (&ge; 2).
         * @return an array of fitted baselines.
         */
        public static Baseline[] fit(DataFrame data, int nbins) {
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Empty baseline DataFrame");
            }
            if (nbins < 2) {
                throw new IllegalArgumentException("Invalid number of bins: " + nbins);
            }
            StructType schema = data.schema();
            return IntStream.range(0, schema.length())
                    .mapToObj(i -> {
                        StructField field = schema.field(i);
                        if (field.measure() instanceof NominalScale scale) {
                            int k = Math.max(2, scale.size());
                            int[] codes = data.column(i).toIntArray();
                            return categorical(field.name(), codes, k);
                        } else if (field.isNumeric()) {
                            double[] values = data.column(i).toDoubleArray();
                            return numeric(field.name(), values, nbins);
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(Baseline[]::new);
        }

        /**
         * Returns the feature name.
         * @return the feature name.
         */
        public String feature() {
            return feature;
        }

        /**
         * Returns true if this baseline represents a categorical feature.
         * @return {@code true} if categorical.
         */
        public boolean isCategorical() {
            return breaks == null;
        }

        /**
         * Returns the number of baseline bins.
         * @return the number of bins.
         */
        public int numBins() {
            return expectedProb.length;
        }

        /**
         * Returns a copy of the expected bin probabilities.
         * @return the expected bin probabilities.
         */
        public double[] expectedProb() {
            return expectedProb.clone();
        }

        /**
         * Returns a copy of the interior breakpoints, or {@code null} if categorical.
         * @return the interior breakpoints, or {@code null}.
         */
        public double[] breaks() {
            return breaks == null ? null : breaks.clone();
        }

        /**
         * Returns the total baseline observation count (0 if created from probabilities only).
         * @return the baseline observation count.
         */
        public long expectedCount() {
            return expectedCount;
        }

        /**
         * Returns the bin index for a numeric observation {@code x}, or the category
         * index if this baseline is categorical.
         *
         * @param x the observation value.
         * @return the bin index in {@code [0, numBins() - 1]}, or {@code -1} if out-of-range categorical.
         */
        public int binIndex(double x) {
            if (breaks == null) {
                int c = (int) x;
                return (c >= 0 && c < expectedProb.length && c == x) ? c : -1;
            }
            return locateNumericBin(breaks, x);
        }

        /**
         * Creates a new streaming {@link Counter} bound to this baseline.
         * @return a new counter.
         */
        public Counter newCounter() {
            return new Counter(this);
        }

        /**
         * Evaluates PSI for a batch of numeric observations against this baseline.
         * @param actual the actual sample values.
         * @return the PSI result.
         */
        public PSI test(double[] actual) {
            if (actual == null || actual.length == 0) {
                throw new IllegalArgumentException("Empty actual sample array");
            }
            Counter counter = newCounter();
            counter.add(actual);
            return counter.test();
        }

        /**
         * Evaluates PSI for a batch of categorical observations against this baseline.
         * @param actual the actual category codes.
         * @return the PSI result.
         */
        public PSI testCategorical(int[] actual) {
            if (actual == null || actual.length == 0) {
                throw new IllegalArgumentException("Empty actual categorical array");
            }
            Counter counter = newCounter();
            counter.addCategorical(actual);
            return counter.test();
        }

        /**
         * Evaluates PSI for the matching column in an actual {@link DataFrame}.
         * @param actual the actual DataFrame.
         * @return the PSI result.
         */
        public PSI test(DataFrame actual) {
            if (actual == null || actual.isEmpty()) {
                throw new IllegalArgumentException("Empty actual DataFrame");
            }
            if (isCategorical()) {
                return testCategorical(actual.column(feature).toIntArray());
            } else {
                return test(actual.column(feature).toDoubleArray());
            }
        }
    }

    /**
     * Lightweight, mergeable streaming bin counter bound to a {@link Baseline}.
     * Requires only {@code O(bins)} memory and performs zero heap allocations on
     * {@link #add(double)} or {@link #addCategorical(int)}.
     */
    public static final class Counter implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** The associated baseline distribution. */
        private final Baseline baseline;
        /** The accumulated bin counts. */
        private final long[] bins;
        /** The total number of valid observations assimilated. */
        private long count;
        /** The number of out-of-vocabulary categorical observations seen. */
        private long unseenCount;

        private Counter(Baseline baseline) {
            this.baseline = Objects.requireNonNull(baseline, "baseline cannot be null");
            this.bins = new long[baseline.numBins()];
        }

        /**
         * Returns the associated baseline.
         * @return the baseline.
         */
        public Baseline baseline() {
            return baseline;
        }

        /**
         * Assimilates a single observation into the counter. {@code NaN} and infinite
         * values are ignored.
         *
         * @param x the observation value.
         */
        public void add(double x) {
            if (!Double.isFinite(x)) {
                return;
            }
            if (baseline.breaks == null) {
                addCategorical((int) x);
            } else {
                int idx = locateNumericBin(baseline.breaks, x);
                bins[idx]++;
                count++;
            }
        }

        /**
         * Assimilates a batch of numeric observations into the counter.
         * @param data the observation values.
         */
        public void add(double[] data) {
            for (double x : data) {
                add(x);
            }
        }

        /**
         * Assimilates a single categorical observation code into the counter.
         * Out-of-range codes (unseen categories not present in the baseline)
         * are tracked in {@link #unseenCount()} rather/* than throwing.
         *
         * @param category the category code.
         */
        public void addCategorical(int category) {
            if (category >= 0 && category < bins.length) {
                bins[category]++;
            } else {
                unseenCount++;
            }
            count++;
        }

        /**
         * Assimilates a batch of categorical observation codes into the counter.
         * @param categories the category codes.
         */
        public void addCategorical(int[] categories) {
            for (int c : categories) {
                addCategorical(c);
            }
        }

        /**
         * Returns the total number of valid observations assimilated.
         * @return the observation count.
         */
        public long count() {
            return count;
        }

        /**
         * Returns the number of out-of-vocabulary categorical observations seen.
         * @return the unseen category observation count.
         */
        public long unseenCount() {
            return unseenCount;
        }

        /**
         * Returns a copy of the accumulated bin counts.
         * @return the accumulated bin counts.
         */
        public long[] bins() {
            return bins.clone();
        }

        /**
         * Merges another counter for the same baseline into this counter in-place.
         *
         * @param other the other counter to merge.
         * @return this counter after merging.
         */
        public Counter merge(Counter other) {
            if (other == null || other.count == 0L) {
                return this;
            }
            if (other == this) {
                throw new IllegalArgumentException("Cannot merge a Counter with itself");
            }
            if (this.bins.length != other.bins.length) {
                throw new IllegalArgumentException("Cannot merge counters with different bin counts");
            }
            for (int i = 0; i < bins.length; i++) {
                this.bins[i] += other.bins[i];
            }
            this.count += other.count;
            this.unseenCount += other.unseenCount;
            return this;
        }

        /**
         * Resets all bin counts to zero for reuse in the next telemetry window.
         */
        public void reset() {
            Arrays.fill(bins, 0L);
            count = 0L;
            unseenCount = 0L;
        }

        /**
         * Computes the {@link PSI} drift result comparing the accumulated window
         * counts against the {@link Baseline}.
         *
         * @return the PSI result.
         */
        public PSI test() {
            if (count == 0L) {
                throw new IllegalStateException("Cannot compute PSI on an empty Counter");
            }
            if (unseenCount > 0L) {
                int k = bins.length + 1;
                double[] extProb = Arrays.copyOf(baseline.expectedProb, k);
                long[] extExpectedBins = baseline.expectedBins != null
                        ? Arrays.copyOf(baseline.expectedBins, k)
                        : null;
                long[] extActualBins = Arrays.copyOf(bins, k);
                extActualBins[k - 1] = unseenCount;
                return computeFromProbAndBins(
                        baseline.feature,
                        extProb,
                        extExpectedBins,
                        baseline.expectedCount,
                        extActualBins,
                        baseline.breaks);
            }
            return computeFromProbAndBins(
                    baseline.feature,
                    baseline.expectedProb,
                    baseline.expectedBins,
                    baseline.expectedCount,
                    bins,
                    baseline.breaks);
        }
    }

    // -----------------------------------------------------------------------
    // Internal Helpers
    // -----------------------------------------------------------------------

    private static PSI computeFromProbAndBins(
            String feature,
            double[] expectedProb,
            long[] expectedBins,
            long expectedTotal,
            long[] actualBins,
            double[] breaks
    ) {
        long actualTotal = sumNonNegative(actualBins);
        if (actualTotal == 0L) {
            throw new IllegalArgumentException("Actual bin counts sum to zero");
        }

        int k = expectedProb.length;
        double[] expProbCopy = expectedProb.clone();
        normalizeProbabilities(expProbCopy);

        double[] actualProb = new double[k];
        double[] binPsi = new double[k];
        double totalPsi = 0.0;
        double l1 = 0.0;

        for (int i = 0; i < k; i++) {
            actualProb[i] = (double) actualBins[i] / actualTotal;
            l1 += Math.abs(actualProb[i] - expProbCopy[i]);

            double pe;
            if (expectedBins != null && expectedTotal > 0L) {
                pe = Math.max(expectedBins[i], MIN_BIN_COUNT) / expectedTotal;
            } else {
                double floor = Math.min(MIN_PROB_FLOOR, MIN_BIN_COUNT / actualTotal);
                pe = Math.max(expProbCopy[i], floor);
            }
            double pa = Math.max(actualBins[i], MIN_BIN_COUNT) / actualTotal;

            double bPsi = (pa - pe) * Math.log(pa / pe);
            binPsi[i] = bPsi;
            totalPsi += bPsi;
        }

        double tvd = 0.5 * l1;
        ChiSqTest chisq = computeChiSq(expProbCopy, expectedBins, expectedTotal, actualBins, actualTotal);

        return new PSI(
                feature,
                totalPsi,
                binPsi,
                expProbCopy,
                actualProb,
                breaks == null ? null : breaks.clone(),
                expectedTotal,
                actualTotal,
                tvd,
                chisq);
    }

    private static ChiSqTest computeChiSq(
            double[] expectedProb,
            long[] expectedBins,
            long expectedTotal,
            long[] actualBins,
            long actualTotal
    ) {
        int k = expectedProb.length;
        int df = k - 1;
        double chisq = 0.0;

        if (expectedBins != null && expectedTotal > 0L) {
            double r = (expectedTotal == actualTotal) ? 1.0 : Math.sqrt((double) actualTotal / expectedTotal);
            double s = (expectedTotal == actualTotal) ? 1.0 : Math.sqrt((double) expectedTotal / actualTotal);
            for (int j = 0; j < k; j++) {
                long b1 = expectedBins[j];
                long b2 = actualBins[j];
                if (b1 == 0L && b2 == 0L) {
                    --df;
                } else {
                    double diff = r * b1 - s * b2;
                    chisq += (diff * diff) / (b1 + b2);
                }
            }
            double p = df > 0 ? Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq) : 1.0;
            return new ChiSqTest("Two Sample", chisq, Math.max(0, df), p);
        } else {
            for (int j = 0; j < k; j++) {
                double prob = expectedProb[j];
                long obs = actualBins[j];
                if (prob == 0.0 && obs == 0L) {
                    --df;
                } else {
                    double expected = actualTotal * Math.max(prob, MIN_PROB_FLOOR);
                    double diff = obs - expected;
                    chisq += (diff * diff) / expected;
                }
            }
            double p = df > 0 ? Gamma.regularizedUpperIncompleteGamma(0.5 * df, 0.5 * chisq) : 1.0;
            return new ChiSqTest("One Sample", chisq, Math.max(0, df), p);
        }
    }

    private static Baseline buildNumericBaselineFromSorted(String feature, double[] sorted, double[] breaks) {
        int n = sorted.length;
        long[] bins = new long[breaks.length + 1];
        for (double v : sorted) {
            bins[locateNumericBin(breaks, v)]++;
        }
        double[] prob = new double[bins.length];
        for (int i = 0; i < bins.length; i++) {
            prob[i] = (double) bins[i] / n;
        }
        return new Baseline(feature, prob, bins, breaks, n);
    }

    private static double[] extractUniqueQuantileBreaks(double[] sorted, int nbins) {
        int n = sorted.length;
        double[] raw = new double[nbins - 1];
        for (int j = 0; j < nbins - 1; j++) {
            int idx = (int) (((long) (j + 1) * n) / nbins);
            raw[j] = sorted[Math.min(idx, n - 1)];
        }
        double[] unique = deduplicateSorted(raw);
        if (unique.length == 0) {
            return new double[] { sorted[0] };
        }
        return unique;
    }

    private static double[] deduplicateSorted(double[] sorted) {
        if (sorted.length == 0) {
            return sorted;
        }
        int uniqueCount = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] > sorted[i - 1]) {
                uniqueCount++;
            }
        }
        if (uniqueCount == sorted.length) {
            return sorted.clone();
        }
        double[] result = new double[uniqueCount];
        result[0] = sorted[0];
        int k = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] > sorted[i - 1]) {
                result[k++] = sorted[i];
            }
        }
        return result;
    }

    private static void validateBreaks(double[] breaks) {
        if (breaks == null || breaks.length == 0) {
            throw new IllegalArgumentException("Breakpoints cannot be null or empty");
        }
        for (int i = 0; i < breaks.length; i++) {
            if (!Double.isFinite(breaks[i])) {
                throw new IllegalArgumentException("Non-finite breakpoint at index " + i);
            }
            if (i > 0 && breaks[i] <= breaks[i - 1]) {
                throw new IllegalArgumentException("Breakpoints must be strictly increasing");
            }
        }
    }

    /**
     * Locates the bin index for {@code x} given interior breakpoints {@code breaks}.
     * Intervals are {@code (-∞, breaks[0]), [breaks[0], breaks[1]), ..., [breaks[m-1], +∞)},
     * matching {@code InformationValue.toTransform}.
     */
    private static int locateNumericBin(double[] breaks, double x) {
        int i = Arrays.binarySearch(breaks, x);
        if (i < 0) {
            return -i - 1;
        } else {
            return i + 1;
        }
    }

    private static double[] filterFinite(double[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Empty sample array");
        }
        int finiteCount = 0;
        for (double v : data) {
            if (Double.isFinite(v)) {
                finiteCount++;
            }
        }
        if (finiteCount == data.length) {
            return data.clone();
        }
        double[] clean = new double[finiteCount];
        int k = 0;
        for (double v : data) {
            if (Double.isFinite(v)) {
                clean[k++] = v;
            }
        }
        return clean;
    }

    private static void normalizeProbabilities(double[] prob) {
        double sum = 0.0;
        for (double p : prob) {
            if (p < 0.0 || !Double.isFinite(p)) {
                throw new IllegalArgumentException("Invalid probability value: " + p);
            }
            sum += p;
        }
        if (sum <= 0.0) {
            throw new IllegalArgumentException("Probabilities sum to zero");
        }
        if (Math.abs(sum - 1.0) > 1e-12) {
            for (int i = 0; i < prob.length; i++) {
                prob[i] /= sum;
            }
        }
    }

    private static long sumNonNegative(long[] bins) {
        long sum = 0L;
        for (long b : bins) {
            if (b < 0L) {
                throw new IllegalArgumentException("Negative bin count: " + b);
            }
            sum += b;
        }
        return sum;
    }

    private static long[] toLongArray(int[] bins) {
        if (bins == null) {
            throw new IllegalArgumentException("Null bin array");
        }
        long[] result = new long[bins.length];
        for (int i = 0; i < bins.length; i++) {
            result[i] = bins[i];
        }
        return result;
    }
}
