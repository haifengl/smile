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
package smile.feature.importance;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.AbstractTuple;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.formula.Term;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.linalg.UPLO;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.tensor.Cholesky;
import smile.tensor.DenseMatrix;
import smile.tensor.ScalarType;
import smile.tensor.Vector;
import smile.util.Index;

/**
 * Kernel SHAP (SHapley Additive exPlanations) is a model-agnostic method
 * to approximate Shapley values using a specially-weighted local linear
 * regression over feature coalitions.
 * <p>
 * Given an input instance {@code x} with {@code M} features and a background
 * reference dataset {@code B}, Kernel SHAP evaluates the expected model
 * prediction for sampled feature coalitions (where missing features are
 * marginalized over the background dataset {@code B}) and solves a constrained
 * weighted least squares problem that enforces the Shapley efficiency axiom:
 * <pre>{@code
 *     sum_{j=1}^M phi_j = f(x) - E[f(z)]
 * }</pre>
 *
 * <h2>References</h2>
 * <ol>
 * <li>Lundberg, Scott M., and Su-In Lee. A unified approach to interpreting model predictions. NIPS, 2017.</li>
 * </ol>
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class KernelSHAP<T> implements SHAP<T>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KernelSHAP.class);

    /** Default maximum number of background rows when summarizing a dataset. */
    public static final int DEFAULT_BACKGROUND_SAMPLES = 64;
    /** Default base number of coalitions when numSamples is 0 (auto). */
    private static final int DEFAULT_BASE_COALITIONS = 2048;
    /** Default ridge regularization for numerical stability. */
    public static final double DEFAULT_RIDGE = 1E-8;
    /** Threshold on (numCoalitions * backgroundSize) to parallelize coalition evaluation. */
    private static final int PARALLEL_WORK_THRESHOLD = 512;
    /** Deterministic seed for paired Monte Carlo coalition sampling. */
    private static final long DEFAULT_SAMPLING_SEED = 19650218L;

    /** The number of input features. */
    private final int numFeatures;
    /** The number of model outputs (1 for regression, k for k-class classification). */
    private final int numOutputs;
    /** The normalized weights of background instances (sum to 1). */
    private final double[] weights;
    /** The expected baseline prediction E[f(z)] over the background dataset. */
    private final double[] baseValues;
    /** Function that normalizes/projects an input instance before masking. */
    private final SerializableFunction<T, T> preprocessor;
    /** Function that evaluates the model on a single instance. */
    private final SerializableFunction<T, double[]> predictor;
    /** Function that evaluates the expected model output for a coalition mask over the background. */
    private final CoalitionEvaluator<T> evaluator;
    /** Optional formula for DataFrame binding. */
    private final Formula formula;
    /** Hyperparameters. */
    private final Options options;

    /**
     * Serializable function interface.
     */
    @FunctionalInterface
    private interface SerializableFunction<I, O> extends Function<I, O>, Serializable {
    }

    /**
     * Evaluates the marginal expectation E_B[f(h_x(z', B))] for a given instance x and binary mask z'.
     */
    @FunctionalInterface
    private interface CoalitionEvaluator<T> extends BiFunction<T, boolean[], double[]>, Serializable {
    }

    /**
     * KernelSHAP hyperparameters.
     *
     * @param numSamples maximum number of coalitions to evaluate per instance
     *                   ({@code 0} = auto: {@code 2 * M + 2048}).
     * @param ridge      L2 regularization parameter for numerical stability (default {@code 1e-8}).
     */
    public record Options(int numSamples, double ridge) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** Validates options. */
        public Options {
            if (numSamples < 0) {
                throw new IllegalArgumentException("Invalid numSamples: " + numSamples);
            }
            if (ridge < 0.0 || Double.isNaN(ridge) || Double.isInfinite(ridge)) {
                throw new IllegalArgumentException("Invalid ridge: " + ridge);
            }
        }

        /** Default constructor with auto sample budget and 1e-8 ridge penalty. */
        public Options() {
            this(0, DEFAULT_RIDGE);
        }

        /**
         * Constructor with custom sample budget and default ridge penalty.
         * @param numSamples maximum number of coalitions to evaluate per instance.
         */
        public Options(int numSamples) {
            this(numSamples, DEFAULT_RIDGE);
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li>{@code smile.shap.samples}
         * <li>{@code smile.shap.ridge}
         * </ul>
         * @return the persistent properties.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.shap.samples", Integer.toString(numSamples));
            props.setProperty("smile.shap.ridge", Double.toString(ridge));
            return props;
        }

        /**
         * Returns the options from properties.
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            if (props == null) {
                return new Options();
            }
            int numSamples = Integer.parseInt(props.getProperty("smile.shap.samples", "0"));
            double ridge = Double.parseDouble(props.getProperty("smile.shap.ridge", Double.toString(DEFAULT_RIDGE)));
            return new Options(numSamples, ridge);
        }
    }

    /**
     * Private constructor.
     */
    private KernelSHAP(int numFeatures,
                       int numOutputs,
                       double[] weights,
                       double[] baseValues,
                       SerializableFunction<T, T> preprocessor,
                       SerializableFunction<T, double[]> predictor,
                       CoalitionEvaluator<T> evaluator,
                       Formula formula,
                       Options options) {
        this.numFeatures = numFeatures;
        this.numOutputs = numOutputs;
        this.weights = weights;
        this.baseValues = baseValues;
        this.preprocessor = preprocessor;
        this.predictor = predictor;
        this.evaluator = evaluator;
        this.formula = formula;
        this.options = Objects.requireNonNull(options, "Options cannot be null");
    }

    /**
     * Returns the number of features.
     * @return the number of features.
     */
    public int numFeatures() {
        return numFeatures;
    }

    /**
     * Returns the number of model outputs per instance (1 for regression, k for classification).
     * @return the number of model outputs.
     */
    public int numOutputs() {
        return numOutputs;
    }

    /**
     * Returns the expected baseline prediction {@code E[f(z)]} over the background dataset.
     * @return a copy of the baseline prediction vector.
     */
    public double[] baseValues() {
        return baseValues.clone();
    }

    /**
     * Returns the KernelSHAP options.
     * @return the options.
     */
    public Options options() {
        return options;
    }

    /**
     * Returns the average of absolute SHAP values over a data frame.
     * @param data the data frame.
     * @return the average of absolute SHAP values.
     */
    @SuppressWarnings("unchecked")
    public double[] shap(DataFrame data) {
        return shap(data, null);
    }

    /**
     * Returns the average of absolute SHAP values over a data frame with custom options.
     * @param data the data frame.
     * @param options the KernelSHAP options, or {@code null} to use default options.
     * @return the average of absolute SHAP values.
     */
    @SuppressWarnings("unchecked")
    public double[] shap(DataFrame data, Options options) {
        if (formula != null) {
            formula.bind(data.schema());
        }
        return shap((java.util.stream.Stream<? extends T>) data.stream().parallel(), options);
    }

    @Override
    public double[] shap(T rawInput) {
        return shap(rawInput, this.options);
    }

    @Override
    public double[] shap(T rawInput, Options options) {
        Objects.requireNonNull(rawInput, "Input instance cannot be null");
        Options opts = options != null ? options : this.options;
        if (numFeatures == 0) {
            return new double[0];
        }

        T x = preprocessor.apply(rawInput);
        double[] fx = predictor.apply(x);
        if (fx.length != numOutputs) {
            throw new IllegalStateException(String.format(
                    "Predictor output dimension mismatch: expected %d, got %d", numOutputs, fx.length));
        }

        double[] delta = new double[numOutputs];
        for (int c = 0; c < numOutputs; c++) {
            delta[c] = fx[c] - baseValues[c];
        }

        if (numFeatures == 1) {
            return delta;
        }

        int maxSamples = opts.numSamples() > 0
                ? opts.numSamples()
                : 2 * numFeatures + DEFAULT_BASE_COALITIONS;

        List<Coalition> coalitions = generateCoalitions(numFeatures, maxSamples);
        int L = coalitions.size();

        // Evaluate expected model prediction v(z'_m) for each coalition
        double[][] values = new double[L][];
        if (L * weights.length >= PARALLEL_WORK_THRESHOLD) {
            IntStream.range(0, L).parallel().forEach(m -> {
                values[m] = evaluator.apply(x, coalitions.get(m).mask);
            });
        } else {
            for (int m = 0; m < L; m++) {
                values[m] = evaluator.apply(x, coalitions.get(m).mask);
            }
        }

        return solveWeightedLeastSquares(coalitions, values, delta, opts);
    }

    /**
     * Solves the efficiency-constrained weighted least squares problem by eliminating
     * the last feature phi_{M-1} = delta - sum_{j=0}^{M-2} phi_j.
     */
    private double[] solveWeightedLeastSquares(List<Coalition> coalitions, double[][] values, double[] delta, Options opts) {
        int M = numFeatures;
        int d = M - 1;
        int K = numOutputs;
        int L = coalitions.size();

        // Normalize coalition weights so sum(w_m) = 1
        double weightSum = 0.0;
        for (int m = 0; m < L; m++) {
            weightSum += coalitions.get(m).weight;
        }

        DenseMatrix G = DenseMatrix.zeros(ScalarType.Float64, d, d).withUplo(UPLO.LOWER);
        double[][] rhs = new double[d][K];

        for (int m = 0; m < L; m++) {
            Coalition coalition = coalitions.get(m);
            boolean[] mask = coalition.mask;
            double wm = coalition.weight / weightSum;
            boolean zLast = mask[d];
            double[] vm = values[m];

            // Adjusted target: y_m = v(z'_m) - base - z'_{m, M-1} * delta
            double[] ym = new double[K];
            for (int c = 0; c < K; c++) {
                ym[c] = vm[c] - baseValues[c] - (zLast ? delta[c] : 0.0);
            }

            // Update G = A^T W A and rhs = A^T W Y where A_{m, i} = z'_{m, i} - z'_{m, M-1} in {-1, 0, 1}
            for (int i = 0; i < d; i++) {
                int ai = (mask[i] ? 1 : 0) - (zLast ? 1 : 0);
                if (ai == 0) continue;

                double wAi = wm * ai;
                for (int c = 0; c < K; c++) {
                    rhs[i][c] += wAi * ym[c];
                }

                for (int j = 0; j <= i; j++) {
                    int aj = (mask[j] ? 1 : 0) - (zLast ? 1 : 0);
                    if (aj != 0) {
                        double val = G.get(i, j) + wAi * aj;
                        G.set(i, j, val);
                        if (i != j) {
                            G.set(j, i, val);
                        }
                    }
                }
            }
        }

        // Symmetric L2 regularization across all M features:
        // lambda * (sum_{j=0}^{M-2} phi_j^2 + (delta - sum_{j=0}^{M-2} phi_j)^2)
        double ridge = opts.ridge();
        if (ridge > 0.0) {
            for (int i = 0; i < d; i++) {
                for (int j = 0; j <= i; j++) {
                    double penalty = (i == j) ? 2.0 * ridge : ridge;
                    double val = G.get(i, j) + penalty;
                    G.set(i, j, val);
                    if (i != j) {
                        G.set(j, i, val);
                    }
                }
                for (int c = 0; c < K; c++) {
                    rhs[i][c] += ridge * delta[c];
                }
            }
        }

        double[][] phi = solveLinearSystem(G, rhs, d, K);

        // Reconstruct phi_{M-1} from the efficiency constraint and pack as M x K (raw[j * K + c])
        double[] result = new double[M * K];
        for (int c = 0; c < K; c++) {
            double sum = 0.0;
            for (int j = 0; j < d; j++) {
                double val = phi[j][c];
                result[j * K + c] = val;
                sum += val;
            }
            result[d * K + c] = delta[c] - sum;
        }
        return result;
    }

    /**
     * Solves G * X = rhs for K right-hand-side columns using Cholesky with SVD fallback.
     */
    private static double[][] solveLinearSystem(DenseMatrix G, double[][] rhs, int d, int K) {
        double[][] phi = new double[d][K];
        try {
            Cholesky cholesky = G.copy().cholesky();
            if (K == 1) {
                double[] b = new double[d];
                for (int i = 0; i < d; i++) {
                    b[i] = rhs[i][0];
                }
                Vector sol = cholesky.solve(b);
                for (int i = 0; i < d; i++) {
                    phi[i][0] = sol.get(i);
                }
            } else {
                DenseMatrix B = DenseMatrix.of(rhs);
                cholesky.solve(B);
                for (int i = 0; i < d; i++) {
                    for (int c = 0; c < K; c++) {
                        phi[i][c] = B.get(i, c);
                    }
                }
            }
        } catch (RuntimeException ex) {
            logger.debug("Cholesky factorization failed in KernelSHAP, falling back to SVD: {}", ex.getMessage());
            var svd = G.copy().withUplo(null).svd();
            for (int c = 0; c < K; c++) {
                double[] b = new double[d];
                for (int i = 0; i < d; i++) {
                    b[i] = rhs[i][c];
                }
                Vector sol = svd.solve(b);
                for (int i = 0; i < d; i++) {
                    phi[i][c] = sol.get(i);
                }
            }
        }
        return phi;
    }

    /**
     * Represents a binary feature coalition mask and its Shapley kernel weight.
     */
    private record Coalition(boolean[] mask, double weight) {
    }

    /**
     * Generates interior coalitions and their corresponding weights.
     * When 2^M - 2 <= maxSamples, all interior subsets are enumerated deterministically.
     * Otherwise, priority symmetric shells are enumerated followed by antithetic paired sampling.
     */
    static List<Coalition> generateCoalitions(int M, int maxSamples) {
        double[] binom = new double[M + 1];
        binom[0] = 1.0;
        for (int s = 1; s <= M; s++) {
            binom[s] = binom[s - 1] * (M - s + 1) / s;
        }

        // Check if full enumeration fits within maxSamples
        if (M <= 30 && ((1L << M) - 2L) <= maxSamples) {
            int total = (1 << M) - 2;
            List<Coalition> coalitions = new ArrayList<>(total);
            double[] kernelWeight = new double[M];
            for (int s = 1; s < M; s++) {
                kernelWeight[s] = (M - 1.0) / (binom[s] * s * (M - s));
            }
            for (int bits = 1; bits <= total; bits++) {
                boolean[] mask = new boolean[M];
                int s = Integer.bitCount(bits);
                for (int j = 0; j < M; j++) {
                    if ((bits & (1 << j)) != 0) {
                        mask[j] = true;
                    }
                }
                coalitions.add(new Coalition(mask, kernelWeight[s]));
            }
            return coalitions;
        }

        // Paired shell enumeration + random antithetic sampling
        int numShells = M / 2;
        double[] shellWeights = new double[numShells + 1];
        double[] shellCounts = new double[numShells + 1];
        double totalShellWeight = 0.0;

        for (int r = 1; r <= numShells; r++) {
            boolean paired = (r < M - r);
            shellCounts[r] = paired ? 2.0 * binom[r] : binom[r];
            shellWeights[r] = paired ? 2.0 / (r * (double) (M - r)) : 1.0 / (r * (double) (M - r));
            totalShellWeight += shellWeights[r];
        }

        List<Coalition> coalitions = new ArrayList<>(Math.min(maxSamples, 4096));
        int remainingBudget = Math.max(2, maxSamples);
        double remainingWeight = totalShellWeight;
        int firstSampledShell = numShells + 1;

        for (int r = 1; r <= numShells; r++) {
            double count = shellCounts[r];
            double allocatedSamples = remainingBudget * (shellWeights[r] / remainingWeight);
            if (count <= allocatedSamples && count <= remainingBudget) {
                double subsetWeight = (shellWeights[r] / totalShellWeight) / count;
                enumerateShell(M, r, subsetWeight, coalitions);
                remainingBudget -= (int) Math.round(count);
                remainingWeight -= shellWeights[r];
            } else {
                firstSampledShell = r;
                break;
            }
        }

        if (firstSampledShell <= numShells && remainingBudget > 0) {
            int numPairs = Math.max(1, remainingBudget / 2);
            double pairMemberWeight = (remainingWeight / totalShellWeight) / (2.0 * numPairs);

            // Build CDF over remaining shells [firstSampledShell .. numShells]
            int remShells = numShells - firstSampledShell + 1;
            double[] cdf = new double[remShells];
            double cum = 0.0;
            for (int i = 0; i < remShells; i++) {
                cum += shellWeights[firstSampledShell + i] / remainingWeight;
                cdf[i] = cum;
            }
            cdf[remShells - 1] = 1.0;

            Map<BitSet, Double> sampled = new LinkedHashMap<>(numPairs * 2);
            Random rng = new Random(DEFAULT_SAMPLING_SEED + M * 31L + maxSamples);
            int[] perm = IntStream.range(0, M).toArray();

            for (int p = 0; p < numPairs; p++) {
                double u = rng.nextDouble();
                int shellIdx = 0;
                while (shellIdx < remShells - 1 && u > cdf[shellIdx]) {
                    shellIdx++;
                }
                int r = firstSampledShell + shellIdx;

                // Partial Fisher-Yates shuffle to pick r distinct indices
                for (int i = 0; i < r; i++) {
                    int swapIdx = i + rng.nextInt(M - i);
                    int tmp = perm[i];
                    perm[i] = perm[swapIdx];
                    perm[swapIdx] = tmp;
                }

                BitSet pos = new BitSet(M);
                BitSet comp = new BitSet(M);
                comp.set(0, M);
                for (int i = 0; i < r; i++) {
                    pos.set(perm[i]);
                    comp.clear(perm[i]);
                }

                sampled.merge(pos, pairMemberWeight, Double::sum);
                sampled.merge(comp, pairMemberWeight, Double::sum);
            }

            for (var entry : sampled.entrySet()) {
                BitSet bs = entry.getKey();
                boolean[] mask = new boolean[M];
                for (int j = bs.nextSetBit(0); j >= 0; j = bs.nextSetBit(j + 1)) {
                    mask[j] = true;
                }
                coalitions.add(new Coalition(mask, entry.getValue()));
            }
        }

        return coalitions;
    }

    /**
     * Enumerates all coalitions of size r (and their complements of size M - r when r < M - r).
     */
    private static void enumerateShell(int M, int r, double weight, List<Coalition> out) {
        boolean includeComplement = (r < M - r);
        int[] comb = new int[r];
        for (int i = 0; i < r; i++) {
            comb[i] = i;
        }

        while (true) {
            boolean[] mask = new boolean[M];
            for (int idx : comb) {
                mask[idx] = true;
            }
            out.add(new Coalition(mask, weight));

            if (includeComplement) {
                boolean[] comp = new boolean[M];
                for (int j = 0; j < M; j++) {
                    comp[j] = !mask[j];
                }
                out.add(new Coalition(comp, weight));
            }

            // Advance to next lexicographic combination
            int i = r - 1;
            while (i >= 0 && comb[i] == M - r + i) {
                i--;
            }
            if (i < 0) {
                break;
            }
            comb[i]++;
            for (int j = i + 1; j < r; j++) {
                comb[j] = comb[j - 1] + 1;
            }
        }
    }

    /**
     * Validates and normalizes background weights so that they sum to 1.0.
     */
    private static double[] normalizeWeights(int n, double[] weights) {
        if (weights == null) {
            double[] uniform = new double[n];
            Arrays.fill(uniform, 1.0 / n);
            return uniform;
        }
        if (weights.length != n) {
            throw new IllegalArgumentException(String.format(
                    "Background weights length (%d) does not match background size (%d)", weights.length, n));
        }
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double w = weights[i];
            if (w < 0.0 || Double.isNaN(w) || Double.isInfinite(w)) {
                throw new IllegalArgumentException("Invalid background weight at index " + i + ": " + w);
            }
            sum += w;
        }
        if (sum <= 0.0) {
            throw new IllegalArgumentException("Sum of background weights must be positive");
        }
        double[] norm = new double[n];
        for (int i = 0; i < n; i++) {
            norm[i] = weights[i] / sum;
        }
        return norm;
    }

    // =========================================================================
    // Factory Methods for Vector Models (double[])
    // =========================================================================

    /**
     * Creates a KernelSHAP explainer for a scalar function on {@code double[]}.
     *
     * @param background the background reference dataset.
     * @param predictor  the scalar prediction function.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, ToDoubleFunction<double[]> predictor) {
        return of(background, null, predictor, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a scalar function on {@code double[]}.
     *
     * @param background the background reference dataset.
     * @param predictor  the scalar prediction function.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, ToDoubleFunction<double[]> predictor, Options options) {
        return of(background, null, predictor, options);
    }

    /**
     * Creates a KernelSHAP explainer for a scalar function on {@code double[]} with weighted background samples.
     *
     * @param background the background reference dataset.
     * @param weights    optional background sample weights (may be {@code null} for uniform weights).
     * @param predictor  the scalar prediction function.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, double[] weights, ToDoubleFunction<double[]> predictor, Options options) {
        Objects.requireNonNull(predictor, "Predictor function cannot be null");
        SerializableFunction<double[], double[]> vecPredictor = (predictor instanceof Serializable)
                ? (SerializableFunction<double[], double[]>) x -> new double[]{predictor.applyAsDouble(x)}
                : x -> new double[]{predictor.applyAsDouble(x)};
        return of(background, weights, vecPredictor, options);
    }

    /**
     * Creates a KernelSHAP explainer for a vector-valued function on {@code double[]}.
     *
     * @param background the background reference dataset.
     * @param predictor  the vector prediction function.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Function<double[], double[]> predictor) {
        return of(background, null, predictor, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a vector-valued function on {@code double[]}.
     *
     * @param background the background reference dataset.
     * @param predictor  the vector prediction function.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Function<double[], double[]> predictor, Options options) {
        return of(background, null, predictor, options);
    }

    /**
     * Creates a KernelSHAP explainer for a vector-valued function on {@code double[]} with weighted background samples.
     *
     * @param background the background reference dataset.
     * @param weights    optional background sample weights (may be {@code null} for uniform weights).
     * @param predictor  the vector prediction function.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, double[] weights, Function<double[], double[]> predictor, Options options) {
        Objects.requireNonNull(background, "Background dataset cannot be null");
        Objects.requireNonNull(predictor, "Predictor function cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");
        if (background.length == 0) {
            throw new IllegalArgumentException("Background dataset cannot be empty");
        }
        int n = background.length;
        int m = background[0].length;
        double[][] bgCopy = new double[n][];
        for (int i = 0; i < n; i++) {
            if (background[i] == null || background[i].length != m) {
                throw new IllegalArgumentException("Inconsistent feature length in background row " + i);
            }
            bgCopy[i] = background[i].clone();
        }

        double[] normWeights = normalizeWeights(n, weights);
        double[] firstOut = predictor.apply(bgCopy[0]);
        int k = firstOut.length;
        double[] base = new double[k];
        for (int c = 0; c < k; c++) {
            base[c] = normWeights[0] * firstOut[c];
        }
        for (int i = 1; i < n; i++) {
            double[] out = predictor.apply(bgCopy[i]);
            if (out.length != k) {
                throw new IllegalArgumentException("Inconsistent predictor output dimension at background row " + i);
            }
            double w = normWeights[i];
            for (int c = 0; c < k; c++) {
                base[c] += w * out[c];
            }
        }

        SerializableFunction<double[], double[]> serialPredictor = predictor::apply;
        SerializableFunction<double[], double[]> identity = x -> {
            if (x.length != m) {
                throw new IllegalArgumentException(String.format(
                        "Input feature length (%d) does not match background feature length (%d)", x.length, m));
            }
            return x;
        };

        CoalitionEvaluator<double[]> evaluator = (x, mask) -> {
            double[] expected = new double[k];
            double[] composite = new double[m];
            for (int i = 0; i < n; i++) {
                double[] b = bgCopy[i];
                for (int j = 0; j < m; j++) {
                    composite[j] = mask[j] ? x[j] : b[j];
                }
                double[] pred = serialPredictor.apply(composite);
                double w = normWeights[i];
                for (int c = 0; c < k; c++) {
                    expected[c] += w * pred[c];
                }
            }
            return expected;
        };

        return new KernelSHAP<>(m, k, normWeights, base, identity, serialPredictor, evaluator, null, options);
    }

    /**
     * Creates a KernelSHAP explainer for a {@code Regression<double[]>} model.
     *
     * @param background the background reference dataset.
     * @param model      the regression model.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Regression<double[]> model) {
        return of(background, model, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a {@code Regression<double[]>} model.
     *
     * @param background the background reference dataset.
     * @param model      the regression model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Regression<double[]> model, Options options) {
        Objects.requireNonNull(model, "Regression model cannot be null");
        SerializableFunction<double[], double[]> predictor = x -> new double[]{model.predict(x)};
        return of(background, null, predictor, options);
    }

    /**
     * Creates a KernelSHAP explainer for a {@code Classifier<double[]>} model.
     * If the classifier supports soft predictions ({@code model.isSoft()}),
     * posterior class probabilities are explained; otherwise one-hot class
     * indicators are explained.
     *
     * @param background the background reference dataset.
     * @param model      the classification model.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Classifier<double[]> model) {
        return of(background, model, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a {@code Classifier<double[]>} model.
     *
     * @param background the background reference dataset.
     * @param model      the classification model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<double[]> of(double[][] background, Classifier<double[]> model, Options options) {
        Objects.requireNonNull(model, "Classifier model cannot be null");
        int k = model.numClasses();
        boolean soft = model.isSoft();
        int[] classes = model.classes();

        SerializableFunction<double[], double[]> predictor = x -> {
            double[] out = new double[k];
            if (soft) {
                model.predict(x, out);
            } else {
                int y = model.predict(x);
                encodeClassIndicator(y, classes, out);
            }
            return out;
        };
        return of(background, null, predictor, options);
    }

    // =========================================================================
    // Factory Methods for DataFrame / Tuple Models
    // =========================================================================

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameRegression} model.
     *
     * @param background the background reference data frame.
     * @param model      the DataFrame regression model.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, DataFrameRegression model) {
        return of(background, null, model, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameRegression} model.
     *
     * @param background the background reference data frame.
     * @param model      the DataFrame regression model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, DataFrameRegression model, Options options) {
        return of(background, null, model, options);
    }

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameRegression} model with weighted background rows.
     *
     * @param background the background reference data frame.
     * @param weights    optional background sample weights (may be {@code null} for uniform weights).
     * @param model      the DataFrame regression model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, double[] weights, DataFrameRegression model, Options options) {
        Objects.requireNonNull(background, "Background DataFrame cannot be null");
        Objects.requireNonNull(model, "DataFrameRegression model cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        SerializableFunction<Tuple, double[]> predictor = t -> new double[]{model.predict(t)};
        return createTupleExplainer(background, weights, model.formula(), model.schema(), predictor, options);
    }

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameClassifier} model.
     *
     * @param background the background reference data frame.
     * @param model      the DataFrame classifier model.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, DataFrameClassifier model) {
        return of(background, null, model, new Options());
    }

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameClassifier} model.
     *
     * @param background the background reference data frame.
     * @param model      the DataFrame classifier model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, DataFrameClassifier model, Options options) {
        return of(background, null, model, options);
    }

    /**
     * Creates a KernelSHAP explainer for a {@code DataFrameClassifier} model with weighted background rows.
     *
     * @param background the background reference data frame.
     * @param weights    optional background sample weights (may be {@code null} for uniform weights).
     * @param model      the DataFrame classifier model.
     * @param options    the KernelSHAP options.
     * @return the KernelSHAP explainer.
     */
    public static KernelSHAP<Tuple> of(DataFrame background, double[] weights, DataFrameClassifier model, Options options) {
        Objects.requireNonNull(background, "Background DataFrame cannot be null");
        Objects.requireNonNull(model, "DataFrameClassifier model cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        int k = model.numClasses();
        boolean soft = model.isSoft();
        int[] classes = model.classes();

        SerializableFunction<Tuple, double[]> predictor = t -> {
            double[] out = new double[k];
            if (soft) {
                model.predict(t, out);
            } else {
                int y = model.predict(t);
                encodeClassIndicator(y, classes, out);
            }
            return out;
        };
        return createTupleExplainer(background, weights, model.formula(), model.schema(), predictor, options);
    }

    /**
     * Builds a KernelSHAP<Tuple> explainer over the predictor columns of a DataFrame model.
     */
    private static KernelSHAP<Tuple> createTupleExplainer(DataFrame background,
                                                          double[] weights,
                                                          Formula formula,
                                                          StructType modelSchema,
                                                          SerializableFunction<Tuple, double[]> predictor,
                                                          Options options) {
        if (background.isEmpty()) {
            throw new IllegalArgumentException("Background DataFrame cannot be empty");
        }

        DataFrame bgPredictors = extractPredictorDataFrame(background, formula, modelSchema);
        StructType predictorSchema = bgPredictors.schema();
        int n = bgPredictors.size();
        int m = predictorSchema.length();

        List<Tuple> bgTuples = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            bgTuples.add(bgPredictors.get(i));
        }

        double[] normWeights = normalizeWeights(n, weights);
        double[] firstOut = predictor.apply(bgTuples.getFirst());
        int k = firstOut.length;
        double[] base = new double[k];
        for (int c = 0; c < k; c++) {
            base[c] = normWeights[0] * firstOut[c];
        }
        for (int i = 1; i < n; i++) {
            double[] out = predictor.apply(bgTuples.get(i));
            double w = normWeights[i];
            for (int c = 0; c < k; c++) {
                base[c] += w * out[c];
            }
        }

        SerializableFunction<Tuple, Tuple> preprocessor = t -> projectTuple(t, predictorSchema, formula);

        CoalitionEvaluator<Tuple> evaluator = (x, mask) -> {
            double[] expected = new double[k];
            for (int i = 0; i < n; i++) {
                Tuple masked = new MaskedTuple(predictorSchema, x, bgTuples.get(i), mask);
                double[] pred = predictor.apply(masked);
                double w = normWeights[i];
                for (int c = 0; c < k; c++) {
                    expected[c] += w * pred[c];
                }
            }
            return expected;
        };

        return new KernelSHAP<>(m, k, normWeights, base, preprocessor, predictor, evaluator, formula, options);
    }

    /**
     * Extracts the predictor columns from a background DataFrame in the order required by the model.
     */
    private static DataFrame extractPredictorDataFrame(DataFrame background, Formula formula, StructType modelSchema) {
        StructType bgSchema = background.schema();
        if (modelSchema != null && bgSchema.equals(modelSchema)) {
            return background;
        }

        if (formula != null) {
            Formula expanded = formula.expand(bgSchema);
            Set<String> responseVars = expanded.response() != null
                    ? expanded.response().variables()
                    : Collections.emptySet();

            // Collect predictor input column names in formula order
            Set<String> predictorCols = new LinkedHashSet<>();
            for (Term term : expanded.predictors()) {
                for (String var : term.variables()) {
                    if (!responseVars.contains(var) && bgSchema.indexOf(var) >= 0) {
                        predictorCols.add(var);
                    }
                }
            }
            if (!predictorCols.isEmpty()) {
                return background.select(predictorCols.toArray(String[]::new));
            }
        }

        if (modelSchema != null) {
            List<String> names = new ArrayList<>(modelSchema.length());
            for (StructField field : modelSchema.fields()) {
                if (bgSchema.indexOf(field.name()) >= 0) {
                    names.add(field.name());
                }
            }
            if (!names.isEmpty()) {
                return background.select(names.toArray(String[]::new));
            }
        }

        return background;
    }

    /**
     * Projects an arbitrary input Tuple onto the target predictor schema.
     */
    private static Tuple projectTuple(Tuple x, StructType predictorSchema, Formula formula) {
        StructType xSchema = x.schema();
        if (xSchema == predictorSchema || xSchema.equals(predictorSchema)) {
            return x;
        }

        int m = predictorSchema.length();
        int[] indices = new int[m];
        boolean allFound = true;
        for (int j = 0; j < m; j++) {
            int idx = xSchema.indexOf(predictorSchema.field(j).name());
            if (idx < 0) {
                allFound = false;
                break;
            }
            indices[j] = idx;
        }

        if (allFound) {
            return new ProjectedTuple(predictorSchema, x, indices);
        }

        if (formula != null) {
            Tuple xt = formula.x(x);
            if (xt.schema().equals(predictorSchema)) {
                return xt;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Input tuple schema %s is incompatible with predictor schema %s", xSchema, predictorSchema));
    }

    /**
     * Encodes a predicted class label into a one-hot indicator vector.
     */
    private static void encodeClassIndicator(int y, int[] classes, double[] out) {
        if (classes != null) {
            for (int c = 0; c < classes.length; c++) {
                if (classes[c] == y) {
                    out[c] = 1.0;
                    return;
                }
            }
        }
        if (y >= 0 && y < out.length) {
            out[y] = 1.0;
        }
    }

    // =========================================================================
    // Background Subsampling Helpers
    // =========================================================================

    /**
     * Subsamples a background DataFrame to at most {@code maxSamples} representative rows
     * using deterministic uniform stride selection (preserving RNG state).
     *
     * @param data       the source data frame.
     * @param maxSamples the maximum number of background rows to retain.
     * @return the subsampled data frame.
     */
    public static DataFrame sample(DataFrame data, int maxSamples) {
        Objects.requireNonNull(data, "DataFrame cannot be null");
        if (maxSamples <= 0) {
            throw new IllegalArgumentException("maxSamples must be positive: " + maxSamples);
        }
        int n = data.size();
        if (n <= maxSamples) {
            return data;
        }
        int[] indices = new int[maxSamples];
        for (int i = 0; i < maxSamples; i++) {
            indices[i] = (int) ((long) i * n / maxSamples);
        }
        return data.get(Index.of(indices));
    }

    /**
     * Subsamples a background array to at most {@code maxSamples} representative rows
     * using deterministic uniform stride selection (preserving RNG state).
     *
     * @param data       the source array.
     * @param maxSamples the maximum number of background rows to retain.
     * @return the subsampled array.
     */
    public static double[][] sample(double[][] data, int maxSamples) {
        Objects.requireNonNull(data, "Data array cannot be null");
        if (maxSamples <= 0) {
            throw new IllegalArgumentException("maxSamples must be positive: " + maxSamples);
        }
        int n = data.length;
        if (n <= maxSamples) {
            return data;
        }
        double[][] sampled = new double[maxSamples][];
        for (int i = 0; i < maxSamples; i++) {
            int idx = (int) ((long) i * n / maxSamples);
            sampled[i] = data[idx];
        }
        return sampled;
    }

    /**
     * Lightweight tuple view that projects selected columns from a backing tuple.
     */
    private static final class ProjectedTuple extends AbstractTuple {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Tuple source;
        private final int[] indices;

        ProjectedTuple(StructType schema, Tuple source, int[] indices) {
            super(schema);
            this.source = source;
            this.indices = indices;
        }

        @Override
        public Object get(int i) {
            return source.get(indices[i]);
        }

        @Override
        public boolean isNullAt(int i) {
            return source.isNullAt(indices[i]);
        }

        @Override
        public boolean getBoolean(int i) {
            return source.getBoolean(indices[i]);
        }

        @Override
        public byte getByte(int i) {
            return source.getByte(indices[i]);
        }

        @Override
        public short getShort(int i) {
            return source.getShort(indices[i]);
        }

        @Override
        public int getInt(int i) {
            return source.getInt(indices[i]);
        }

        @Override
        public long getLong(int i) {
            return source.getLong(indices[i]);
        }

        @Override
        public float getFloat(int i) {
            return source.getFloat(indices[i]);
        }

        @Override
        public double getDouble(int i) {
            return source.getDouble(indices[i]);
        }
    }

    /**
     * Lightweight tuple view that masks between query tuple x (when mask[i] is true)
     * and background tuple b (when mask[i] is false).
     */
    private static final class MaskedTuple extends AbstractTuple {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Tuple x;
        private final Tuple b;
        private final boolean[] mask;

        MaskedTuple(StructType schema, Tuple x, Tuple b, boolean[] mask) {
            super(schema);
            this.x = x;
            this.b = b;
            this.mask = mask;
        }

        @Override
        public Object get(int i) {
            return mask[i] ? x.get(i) : b.get(i);
        }

        @Override
        public boolean isNullAt(int i) {
            return mask[i] ? x.isNullAt(i) : b.isNullAt(i);
        }

        @Override
        public boolean getBoolean(int i) {
            return mask[i] ? x.getBoolean(i) : b.getBoolean(i);
        }

        @Override
        public byte getByte(int i) {
            return mask[i] ? x.getByte(i) : b.getByte(i);
        }

        @Override
        public short getShort(int i) {
            return mask[i] ? x.getShort(i) : b.getShort(i);
        }

        @Override
        public int getInt(int i) {
            return mask[i] ? x.getInt(i) : b.getInt(i);
        }

        @Override
        public long getLong(int i) {
            return mask[i] ? x.getLong(i) : b.getLong(i);
        }

        @Override
        public float getFloat(int i) {
            return mask[i] ? x.getFloat(i) : b.getFloat(i);
        }

        @Override
        public double getDouble(int i) {
            return mask[i] ? x.getDouble(i) : b.getDouble(i);
        }
    }
}
