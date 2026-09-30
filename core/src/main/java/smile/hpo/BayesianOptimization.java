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
package smile.hpo;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.MathEx;
import smile.math.kernel.MaternKernel;
import smile.math.kernel.MercerKernel;
import smile.regression.GaussianProcessRegression;
import smile.stat.distribution.GaussianDistribution;

/**
 * Bayesian Hyperparameter Optimization (HPO). Bayesian optimization is a sequential
 * design strategy for global optimization of black-box objective functions that does
 * not require derivative information.
 * <p>
 * It uses a Gaussian Process (GP) as a probabilistic surrogate model of the objective
 * function. After evaluating initial random configurations, an acquisition function
 * (such as Expected Improvement or Upper Confidence Bound) balances exploration of
 * uncertain regions with exploitation of known promising areas to select the next
 * most informative hyperparameter configuration.
 *
 * <h2>References</h2>
 * <ol>
 * <li>J. Snoek, H. Larochelle, and R. P. Adams. Practical Bayesian Optimization of Machine Learning Algorithms. NIPS 2012.</li>
 * <li>B. Shahriari, K. Swersky, Z. Wang, R. P. Adams, and N. de Freitas. Taking the Human Out of the Loop: A Review of Bayesian Optimization. Proc. IEEE 2016.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class BayesianOptimization {
    private static final Logger logger = LoggerFactory.getLogger(BayesianOptimization.class);

    /**
     * Acquisition function strategy.
     */
    public enum Acquisition {
        /**
         * Expected Improvement (EI). Measures the expected amount by which a candidate
         * configuration improves upon the current best observed value.
         */
        EXPECTED_IMPROVEMENT,
        /**
         * Upper Confidence Bound (UCB). Selects candidates based on an optimistic
         * confidence bound {@code mu + kappa * sigma}.
         */
        UPPER_CONFIDENCE_BOUND,
        /**
         * Probability of Improvement (PI). Computes the probability that a candidate
         * configuration will improve upon the current best observed value.
         */
        PROBABILITY_OF_IMPROVEMENT
    }

    /**
     * An individual trial evaluation in the optimization process.
     *
     * @param parameters the hyperparameter configuration evaluated.
     * @param value      the objective function value.
     * @param iteration  the iteration index (1-based).
     */
    public record Trial(Properties parameters, double value, int iteration) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            return String.format("Trial %d: value = %.6f, params = %s", iteration, value, parameters);
        }
    }

    /**
     * Result of Bayesian hyperparameter optimization.
     *
     * @param bestParameters the best hyperparameter configuration found.
     * @param bestValue      the best objective function value achieved.
     * @param trials         the full history of evaluated trials.
     */
    public record Result(Properties bestParameters, double bestValue, List<Trial> trials) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Returns the best hyperparameter configuration.
         * @return the best hyperparameter configuration.
         */
        public Properties best() {
            return bestParameters;
        }

        /**
         * Returns the best objective function value.
         * @return the best objective function value.
         */
        public double value() {
            return bestValue;
        }

        @Override
        public String toString() {
            return String.format("BayesianOptimization.Result(bestValue = %.6f, best = %s, trials = %d)",
                    bestValue, bestParameters, trials.size());
        }
    }

    /**
     * Configuration options for Bayesian Optimization.
     *
     * @param maxTrials     the total number of trials/evaluations to perform.
     * @param initialTrials the number of initial random trials before GP kicks in.
     *                      If &le; 0, automatically set to {@code Math.min(maxTrials, Math.max(5, 2 * d))}.
     * @param maximize      true to maximize the objective function, false to minimize.
     * @param acquisition   the acquisition function strategy.
     * @param exploration   exploration trade-off parameter (&xi; for EI/PI, &kappa; for UCB).
     * @param kernel        the Mercer covariance kernel for Gaussian Process regression.
     */
    public record Options(
            int maxTrials,
            int initialTrials,
            boolean maximize,
            Acquisition acquisition,
            double exploration,
            MercerKernel<double[]> kernel
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** Constructor. */
        public Options {
            if (maxTrials <= 0) {
                throw new IllegalArgumentException("maxTrials must be positive: " + maxTrials);
            }
            if (acquisition == null) {
                throw new IllegalArgumentException("acquisition must not be null");
            }
            if (kernel == null) {
                throw new IllegalArgumentException("kernel must not be null");
            }
        }

        /**
         * Creates default options for maximizing with a given trial budget.
         *
         * @param maxTrials the total number of evaluations.
         */
        public Options(int maxTrials) {
            this(maxTrials, true);
        }

        /**
         * Creates default options with specified maximization direction.
         *
         * @param maxTrials the total number of evaluations.
         * @param maximize  true to maximize, false to minimize.
         */
        public Options(int maxTrials, boolean maximize) {
            this(maxTrials, -1, maximize, Acquisition.EXPECTED_IMPROVEMENT, 0.01, new MaternKernel(1.0, 2.5));
        }

        /**
         * Returns an Options instance with the specified acquisition function and exploration parameter.
         *
         * @param acquisition the acquisition function.
         * @param exploration the exploration parameter.
         * @return a new Options instance.
         */
        public Options withAcquisition(Acquisition acquisition, double exploration) {
            return new Options(maxTrials, initialTrials, maximize, acquisition, exploration, kernel);
        }
    }

    private BayesianOptimization() {
    }

    /**
     * Fits/runs Bayesian optimization on the given hyperparameter search space.
     *
     * @param hp        the hyperparameter search space.
     * @param objective the objective function to evaluate each configuration.
     * @param maxTrials the total number of trials.
     * @return the optimization result.
     */
    public static Result fit(Hyperparameters hp, ToDoubleFunction<Properties> objective, int maxTrials) {
        return fit(hp, objective, new Options(maxTrials));
    }

    /**
     * Fits/runs Bayesian optimization on the given hyperparameter search space.
     *
     * @param hp        the hyperparameter search space.
     * @param objective the objective function to evaluate each configuration.
     * @param maxTrials the total number of trials.
     * @param maximize  true to maximize the objective function, false to minimize.
     * @return the optimization result.
     */
    public static Result fit(Hyperparameters hp, ToDoubleFunction<Properties> objective, int maxTrials, boolean maximize) {
        return fit(hp, objective, new Options(maxTrials, maximize));
    }

    /**
     * Fits/runs Bayesian optimization on the given hyperparameter search space with full options.
     *
     * @param hp        the hyperparameter search space.
     * @param objective the objective function to evaluate each configuration.
     * @param options   the optimization options.
     * @return the optimization result.
     */
    public static Result fit(Hyperparameters hp, ToDoubleFunction<Properties> objective, Options options) {
        Objects.requireNonNull(hp, "Hyperparameters must not be null");
        Objects.requireNonNull(objective, "Objective function must not be null");
        Objects.requireNonNull(options, "Options must not be null");

        if (hp.size() == 0) {
            throw new IllegalStateException("No hyperparameters have been registered");
        }

        ParameterSpace space = new ParameterSpace(hp);
        int d = space.dimension();

        // Edge case: all parameters are fixed (zero free dimensions)
        if (d == 0) {
            Properties p = space.toProperties(new double[0]);
            double val = objective.applyAsDouble(p);
            Trial t = new Trial(p, val, 1);
            return new Result(p, val, List.of(t));
        }

        int maxTrials = options.maxTrials();
        int initialTrials = options.initialTrials() > 0
                ? Math.min(maxTrials, options.initialTrials())
                : Math.min(maxTrials, Math.max(5, 2 * d));

        List<double[]> X = new ArrayList<>();
        List<Double> Y = new ArrayList<>(); // raw objective values
        List<Trial> trials = new ArrayList<>();

        Properties bestProps = null;
        double bestVal = options.maximize() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        // Phase 1: Warm-up random evaluations
        Iterator<Properties> randomIter = hp.random().iterator();
        for (int i = 0; i < initialTrials; i++) {
            Properties p = randomIter.next();
            double val = evaluate(objective, p);
            double[] u = space.toVector(p);
            X.add(u);
            Y.add(val);
            Trial trial = new Trial(p, val, i + 1);
            trials.add(trial);

            if (isBetter(val, bestVal, options.maximize())) {
                bestVal = val;
                bestProps = p;
            }
        }

        // Phase 2: Sequential Bayesian Optimization loop
        GaussianDistribution normal = GaussianDistribution.getInstance();
        int candidatePoolSize = 3000;

        for (int step = initialTrials; step < maxTrials; step++) {
            double[][] xTrain = X.toArray(new double[0][]);
            double[] yTrain = new double[Y.size()];
            for (int i = 0; i < Y.size(); i++) {
                // Internal GP always works on maximization
                yTrain[i] = options.maximize() ? Y.get(i) : -Y.get(i);
            }

            double yBest = MathEx.max(yTrain);

            // Fit Gaussian Process surrogate model
            GaussianProcessRegression<double[]> gp = fitSurrogate(xTrain, yTrain, options.kernel());

            // Generate candidate pool in [0, 1]^d
            List<double[]> candidates = generateCandidates(space, X, Y, candidatePoolSize, options.maximize());

            // Optimize acquisition function over candidate pool
            double bestAcq = Double.NEGATIVE_INFINITY;
            double[] bestCandidate = null;

            for (double[] cand : candidates) {
                // Avoid re-evaluating points extremely close to already evaluated points
                if (isEvaluated(cand, X)) {
                    continue;
                }

                double acq = computeAcquisition(gp, cand, yBest, options.acquisition(), options.exploration(), normal);
                if (acq > bestAcq) {
                    bestAcq = acq;
                    bestCandidate = cand;
                }
            }

            // Fallback if all candidates are filtered
            if (bestCandidate == null) {
                bestCandidate = space.sampleRandom();
            }

            Properties p = space.toProperties(bestCandidate);
            double val = evaluate(objective, p);

            X.add(bestCandidate);
            Y.add(val);
            Trial trial = new Trial(p, val, step + 1);
            trials.add(trial);

            if (isBetter(val, bestVal, options.maximize())) {
                bestVal = val;
                bestProps = p;
            }
        }

        return new Result(bestProps, bestVal, Collections.unmodifiableList(trials));
    }

    private static double evaluate(ToDoubleFunction<Properties> objective, Properties p) {
        try {
            double val = objective.applyAsDouble(p);
            return Double.isFinite(val) ? val : (Double.isNaN(val) ? -1E6 : val);
        } catch (Exception ex) {
            logger.warn("Objective evaluation threw exception for config {}", p, ex);
            return -1E6;
        }
    }

    private static boolean isBetter(double current, double best, boolean maximize) {
        return maximize ? (current > best) : (current < best);
    }

    private static boolean isEvaluated(double[] cand, List<double[]> history) {
        for (double[] pt : history) {
            double dist2 = MathEx.squaredDistance(cand, pt);
            if (dist2 < 1E-8) {
                return true;
            }
        }
        return false;
    }

    private static GaussianProcessRegression<double[]> fitSurrogate(double[][] xTrain, double[] yTrain, MercerKernel<double[]> kernel) {
        try {
            // First try with L-BFGS hyperparameter optimization (maxIter = 10)
            GaussianProcessRegression.Options opt = new GaussianProcessRegression.Options(1E-4, true, 1E-4, 10);
            return GaussianProcessRegression.fit(xTrain, yTrain, kernel, opt);
        } catch (Exception ex) {
            // Fallback without hyperparameter optimization (pure Cholesky solve)
            GaussianProcessRegression.Options opt = new GaussianProcessRegression.Options(1E-4, true, 1E-4, 0);
            return GaussianProcessRegression.fit(xTrain, yTrain, kernel, opt);
        }
    }

    private static List<double[]> generateCandidates(ParameterSpace space, List<double[]> X, List<Double> Y, int poolSize, boolean maximize) {
        List<double[]> pool = new ArrayList<>(poolSize);

        // Sort indices of observations from best to worst
        Integer[] order = new Integer[Y.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> maximize ? Double.compare(Y.get(b), Y.get(a)) : Double.compare(Y.get(a), Y.get(b)));

        int topCount = Math.min(3, order.length);
        int localPoolSize = poolSize / 3;

        // Local Gaussian perturbations around top performers
        for (int i = 0; i < localPoolSize; i++) {
            int eliteIdx = order[i % topCount];
            double[] elite = X.get(eliteIdx);
            double std = (i % 2 == 0) ? 0.05 : 0.15;
            pool.add(space.perturb(elite, std));
        }

        // Global uniform random samples
        while (pool.size() < poolSize) {
            pool.add(space.sampleRandom());
        }

        return pool;
    }

    private static double computeAcquisition(
            GaussianProcessRegression<double[]> gp,
            double[] cand,
            double yBest,
            Acquisition acq,
            double exploration,
            GaussianDistribution normal
    ) {
        double[] est = new double[2];
        gp.predict(cand, est);
        double mu = est[0];
        double sigma = Math.max(0.0, est[1]);

        return switch (acq) {
            case EXPECTED_IMPROVEMENT -> {
                double delta = mu - yBest - exploration;
                if (sigma > 1E-9) {
                    double z = delta / sigma;
                    yield delta * normal.cdf(z) + sigma * normal.p(z);
                } else {
                    yield Math.max(0.0, delta);
                }
            }
            case UPPER_CONFIDENCE_BOUND -> {
                double kappa = exploration > 0.0 ? exploration : 1.96;
                yield mu + kappa * sigma;
            }
            case PROBABILITY_OF_IMPROVEMENT -> {
                double delta = mu - yBest - exploration;
                if (sigma > 1E-9) {
                    double z = delta / sigma;
                    yield normal.cdf(z);
                } else {
                    yield delta > 0.0 ? 1.0 : 0.0;
                }
            }
        };
    }
}
