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
package smile.gam;

import java.io.Serial;
import java.io.Serializable;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.glm.model.Model;
import smile.linalg.UPLO;
import smile.math.MathEx;
import smile.tensor.Cholesky;
import smile.tensor.DenseMatrix;
import smile.tensor.Vector;
import smile.validation.ModelSelection;

/**
 * Generalized Additive Models (GAM). A GAM is a flexible statistical model
 * that extends Generalized Linear Models (GLMs) by replacing the linear
 * predictor with a sum of smooth functions of the predictors:
 *
 * <pre>
 *     g(E[Y]) = alpha + f_1(x_1) + f_2(x_2) + ... + f_p(x_p)
 * </pre>
 *
 * where {@code g} is the link function (the same as in GLM), {@code alpha}
 * is the intercept, and {@code f_j} are smooth (non-parametric) functions
 * estimated from the data. Each {@code f_j} is represented as a penalized
 * B-spline (P-spline), which balances fidelity to the data against smoothness
 * of the fitted curve via a smoothing parameter {@code lambda_j}.
 *
 * <h2>Algorithm</h2>
 * GAMs are fitted by a combination of two iterative procedures:
 * <ol>
 *   <li><b>PIRLS (Penalized Iteratively Reweighted Least Squares)</b>:
 *       The outer loop is identical to the IWLS algorithm used for GLMs,
 *       but each weighted least squares step is replaced by a penalized
 *       weighted least squares step that incorporates the smoothing
 *       penalties.</li>
 *   <li><b>Backfitting</b>: Within each PIRLS iteration, the smooth
 *       functions {@code f_1, ..., f_p} are estimated by cycling through
 *       each predictor and fitting a weighted penalized spline to the
 *       partial residuals, keeping the other smooth functions fixed.</li>
 * </ol>
 *
 * <h2>Identifiability</h2>
 * Because the intercept is estimated separately, each smooth is constrained
 * to be centered: the mean of {@code f_j(x_{ij})} over the training data
 * is zero. This ensures that the intercept has a unique interpretation as
 * the grand mean of the linear predictor.
 *
 * <h2>Smoothing Parameter</h2>
 * Each smooth {@code f_j} has its own smoothing parameter {@code lambda_j >= 0}.
 * A larger {@code lambda_j} forces {@code f_j} to be smoother (closer to
 * linear), while {@code lambda_j = 0} gives an unpenalized spline.
 * By default, a single shared {@code lambda} is used for all smooths.
 * Users can override the per-predictor lambdas via {@link Options}.
 *
 * <h2>Degrees of Freedom</h2>
 * The basis dimension (number of B-spline basis functions) per predictor
 * is controlled by {@link Options#df()}. More basis functions allow a richer
 * class of smooth functions but increase the risk of overfitting without
 * sufficient penalization.
 *
 * <h2>References</h2>
 * <ol>
 *   <li>Hastie, T., &amp; Tibshirani, R. (1986). Generalized Additive Models.
 *       <i>Statistical Science</i>, 1(3), 297–310.</li>
 *   <li>Wood, S.N. (2017). <i>Generalized Additive Models: An Introduction
 *       with R</i> (2nd ed.). CRC Press.</li>
 *   <li>Eilers, P.H.C. &amp; Marx, B.D. (1996). Flexible smoothing with
 *       B-splines and penalties. <i>Statistical Science</i>, 11(2), 89–102.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class GAM implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GAM.class);

    /**
     * The symbolic description of the model to be fitted.
     */
    private final Formula formula;

    /**
     * The model specification (link function, deviance, variance function, etc.).
     * The same GLM family models are reused: Gaussian, Bernoulli, Poisson, etc.
     */
    private final Model model;

    /**
     * The intercept (grand mean of the linear predictor).
     */
    private final double intercept;

    /**
     * The smooth terms, one per predictor.
     */
    private final SmoothingSpline[] smooths;

    /**
     * The fitted mean values on the training data.
     */
    private final double[] mu;

    /**
     * The deviance residuals.
     */
    private final double[] devianceResiduals;

    /**
     * The null deviance = 2 * (LogLikelihood(Saturated Model) - LogLikelihood(Null Model)).
     */
    private final double nullDeviance;

    /**
     * The deviance = 2 * (LogLikelihood(Saturated Model) - LogLikelihood(Fitted Model)).
     */
    private final double deviance;

    /**
     * The log-likelihood of the fitted model.
     */
    private final double logLikelihood;

    /**
     * The total number of estimated parameters (for AIC/BIC purposes), computed
     * as {@code 1 + sum_j edf_j} (intercept + effective df of each smooth).
     */
    private final double totalEdf;

    /**
     * Constructor.
     *
     * @param formula          the model formula.
     * @param model            the GLM family specification.
     * @param intercept        the fitted intercept.
     * @param smooths          the fitted smooth terms.
     * @param mu               the fitted mean values.
     * @param devianceResiduals the deviance residuals.
     * @param nullDeviance     the null deviance.
     * @param deviance         the residual deviance.
     * @param logLikelihood    the log-likelihood.
     * @param totalEdf         the total effective degrees of freedom.
     */
    public GAM(Formula formula, Model model, double intercept, SmoothingSpline[] smooths,
               double[] mu, double[] devianceResiduals,
               double nullDeviance, double deviance, double logLikelihood, double totalEdf) {
        this.formula = formula;
        this.model = model;
        this.intercept = intercept;
        this.smooths = smooths;
        this.mu = mu;
        this.devianceResiduals = devianceResiduals;
        this.nullDeviance = nullDeviance;
        this.deviance = deviance;
        this.logLikelihood = logLikelihood;
        this.totalEdf = totalEdf;
    }

    /**
     * Returns the intercept of the model.
     * @return the intercept.
     */
    public double intercept() {
        return intercept;
    }

    /**
     * Returns the smooth terms of the model.
     * @return the smooth terms.
     */
    public SmoothingSpline[] smooths() {
        return smooths;
    }

    /**
     * Returns the fitted mean values on the training data.
     * @return the fitted mean values.
     */
    public double[] fittedValues() {
        return mu;
    }

    /**
     * Returns the deviance residuals.
     * @return the deviance residuals.
     */
    public double[] devianceResiduals() {
        return devianceResiduals;
    }

    /**
     * Returns the deviance of the model.
     * @return the deviance.
     */
    public double deviance() {
        return deviance;
    }

    /**
     * Returns the null deviance.
     * @return the null deviance.
     */
    public double nullDeviance() {
        return nullDeviance;
    }

    /**
     * Returns the log-likelihood of the model.
     * @return the log-likelihood.
     */
    public double logLikelihood() {
        return logLikelihood;
    }

    /**
     * Returns the total effective degrees of freedom (intercept + sum of smooth EDFs).
     * @return the total EDF.
     */
    public double totalEdf() {
        return totalEdf;
    }

    /**
     * Returns the AIC score.
     * @return the AIC score.
     */
    public double AIC() {
        return ModelSelection.AIC(logLikelihood, (int) Math.round(totalEdf));
    }

    /**
     * Returns the BIC score.
     * @return the BIC score.
     */
    public double BIC() {
        return ModelSelection.BIC(logLikelihood, (int) Math.round(totalEdf), mu.length);
    }

    /**
     * Predicts the mean response for a new observation.
     *
     * @param x the new observation tuple (must match the formula schema).
     * @return the predicted mean response.
     */
    public double predict(Tuple x) {
        double eta = intercept;
        for (SmoothingSpline smooth : smooths) {
            double xj = x.getDouble(x.schema().indexOf(smooth.name));
            eta += smooth.predict(xj);
        }
        return model.invlink(eta);
    }

    /**
     * Predicts the mean response for multiple observations.
     *
     * @param data the data frame of new observations.
     * @return the predicted mean responses.
     */
    public double[] predict(DataFrame data) {
        int n = data.nrow();
        double[] predictions = new double[n];
        for (int i = 0; i < n; i++) {
            predictions[i] = predict(data.get(i));
        }
        return predictions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Generalized Additive Model - %s:%n", model));
        sb.append(String.format("Formula: %s%n%n", formula));

        double[] r = devianceResiduals.clone();
        sb.append("Deviance Residuals:\n");
        sb.append("       Min          1Q      Median          3Q         Max\n");
        sb.append(String.format("%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n%n",
                MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        sb.append("Smooth Terms:\n");
        sb.append(String.format("%-20s %8s %10s%n", "Term", "Lambda", "EDF"));
        sb.append("-".repeat(42)).append("\n");
        for (SmoothingSpline smooth : smooths) {
            sb.append(String.format("s(%-18s %8.4g %10.2f%n",
                    smooth.name + ")", smooth.lambda, smooth.edf));
        }

        sb.append(String.format("%nIntercept: %.4f%n", intercept));

        int n = mu.length;
        int dfNull = n - 1;
        double dfResid = n - totalEdf;

        sb.append(String.format("%n    Null deviance: %.4f  on %d  degrees of freedom%n",
                nullDeviance, dfNull));
        sb.append(String.format("Residual deviance: %.4f  on %.1f degrees of freedom%n",
                deviance, dfResid));
        sb.append(String.format("AIC: %.4f     BIC: %.4f%n", AIC(), BIC()));

        return sb.toString();
    }

    // =========================================================================
    //  GAM hyperparameters
    // =========================================================================

    /**
     * GAM hyperparameters.
     *
     * @param lambda      the default smoothing parameter for all smooth terms
     *                    (used when {@code lambdas} is null or shorter than
     *                    the number of predictors).
     * @param lambdas     per-predictor smoothing parameters (may be null).
     * @param df          the number of basis functions per smooth.
     * @param degree      the degree of the B-spline basis (3 = cubic).
     * @param tol         the convergence tolerance for PIRLS iterations.
     * @param maxIter     the maximum number of outer PIRLS iterations.
     * @param backfitIter the maximum number of backfitting iterations per PIRLS step.
     * @param backfitTol  the convergence tolerance for backfitting.
     */
    public record Options(double lambda, double[] lambdas, int df, int degree,
                          double tol, int maxIter,
                          int backfitIter, double backfitTol) {

        /** Default options constructor. */
        public Options() {
            this(0.6, null, 10, 3, 1E-6, 50, 30, 1E-7);
        }

        /** Validates the options. */
        public Options {
            if (lambda < 0) throw new IllegalArgumentException("lambda must be >= 0, got " + lambda);
            if (df < 4)     throw new IllegalArgumentException("df must be >= 4, got " + df);
            if (degree < 1) throw new IllegalArgumentException("degree must be >= 1, got " + degree);
            if (tol <= 0)   throw new IllegalArgumentException("tol must be > 0, got " + tol);
            if (maxIter <= 0) throw new IllegalArgumentException("maxIter must be > 0, got " + maxIter);
            if (backfitIter <= 0) throw new IllegalArgumentException("backfitIter must be > 0, got " + backfitIter);
            if (backfitTol <= 0)  throw new IllegalArgumentException("backfitTol must be > 0, got " + backfitTol);
        }

        /**
         * Returns the smoothing parameter for the {@code j}-th predictor.
         * @param j the predictor index.
         * @return the smoothing parameter.
         */
        public double lambda(int j) {
            if (lambdas != null && j < lambdas.length) return lambdas[j];
            return lambda;
        }

        /**
         * Returns the options as a {@link Properties} map.
         * @return the properties.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.gam.lambda", Double.toString(lambda));
            props.setProperty("smile.gam.df", Integer.toString(df));
            props.setProperty("smile.gam.degree", Integer.toString(degree));
            props.setProperty("smile.gam.tolerance", Double.toString(tol));
            props.setProperty("smile.gam.iterations", Integer.toString(maxIter));
            props.setProperty("smile.gam.backfit.iterations", Integer.toString(backfitIter));
            props.setProperty("smile.gam.backfit.tolerance", Double.toString(backfitTol));
            return props;
        }

        /**
         * Creates options from a {@link Properties} map.
         * @param props the properties.
         * @return the options.
         */
        public static Options of(Properties props) {
            double lambda = Double.parseDouble(props.getProperty("smile.gam.lambda", "0.6"));
            int df = Integer.parseInt(props.getProperty("smile.gam.df", "10"));
            int degree = Integer.parseInt(props.getProperty("smile.gam.degree", "3"));
            double tol = Double.parseDouble(props.getProperty("smile.gam.tolerance", "1E-6"));
            int maxIter = Integer.parseInt(props.getProperty("smile.gam.iterations", "50"));
            int backfitIter = Integer.parseInt(props.getProperty("smile.gam.backfit.iterations", "30"));
            double backfitTol = Double.parseDouble(props.getProperty("smile.gam.backfit.tolerance", "1E-7"));
            return new Options(lambda, null, df, degree, tol, maxIter, backfitIter, backfitTol);
        }
    }

    // =========================================================================
    //  Model fitting
    // =========================================================================

    /**
     * Fits a GAM with default options.
     *
     * @param formula a symbolic description of the model to be fitted.
     *                The formula must have a response variable.
     * @param data    the data frame of predictor and response variables.
     * @param model   the GLM family specification (link function, variance, etc.).
     * @return the fitted GAM.
     */
    public static GAM fit(Formula formula, DataFrame data, Model model) {
        return fit(formula, data, model, new Options());
    }

    /**
     * Fits a GAM with PIRLS (Penalized Iteratively Reweighted Least Squares)
     * and backfitting.
     *
     * <p>The algorithm is:
     * <ol>
     *   <li>Initialize: set {@code mu_i = mustart(y_i)}, compute linear
     *       predictor {@code eta_i = link(mu_i)}.</li>
     *   <li>Outer PIRLS loop (until deviance converges):
     *     <ol>
     *       <li>Compute working weights {@code w_i} and adjusted dependent
     *           variable {@code z_i = eta_i + (y_i - mu_i) * g'(mu_i)}.</li>
     *       <li>Run backfitting on the working response to update the
     *           intercept and all smooth terms.</li>
     *       <li>Update {@code eta_i} and {@code mu_i}.</li>
     *     </ol>
     *   </li>
     * </ol>
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data    the data frame of predictor and response variables.
     * @param model   the GLM family specification.
     * @param options the hyperparameters.
     * @return the fitted GAM.
     */
    public static GAM fit(Formula formula, DataFrame data, Model model, Options options) {
        // -------------------------------------------------------------------
        // 1. Extract predictors and response
        // -------------------------------------------------------------------
        StructType schema = formula.bind(data.schema());
        double[] y = formula.y(data).toDoubleArray();
        int n = y.length;

        // Predictor names (without intercept; all numeric columns of predictor frame)
        DataFrame xFrame = formula.x(data);
        StructType xSchema = xFrame.schema();
        int p = xSchema.length();

        if (p == 0) {
            throw new IllegalArgumentException("The formula has no predictors.");
        }
        if (n <= p) {
            throw new IllegalArgumentException(String.format(
                "The input is not over-determined: %d rows, %d predictors.", n, p));
        }

        String[] names = new String[p];
        double[][] X = new double[p][n];
        for (int j = 0; j < p; j++) {
            StructField field = xSchema.field(j);
            names[j] = field.name();
            double[] col = xFrame.column(j).toDoubleArray();
            X[j] = col;
        }

        // -------------------------------------------------------------------
        // 2. Build B-spline bases and penalty matrices for each predictor
        // -------------------------------------------------------------------
        BSpline[] bases = new BSpline[p];
        double[][][] basisMatrices = new double[p][][]; // basisMatrices[j] is n x df_j
        double[][][] penalties = new double[p][][];    // penalties[j] is df_j x df_j
        double[] lambdas = new double[p];

        for (int j = 0; j < p; j++) {
            lambdas[j] = options.lambda(j);
            bases[j] = new BSpline(X[j], options.df(), options.degree());
            basisMatrices[j] = bases[j].basis(X[j]);
            penalties[j] = bases[j].penalty();
        }

        // -------------------------------------------------------------------
        // 3. Initialize
        // -------------------------------------------------------------------
        double[] eta = new double[n];
        double[] mu  = new double[n];
        double[] w   = new double[n]; // IRLS weights
        double[] z   = new double[n]; // adjusted dependent variable

        for (int i = 0; i < n; i++) {
            mu[i]  = model.mustart(y[i]);
            eta[i] = model.link(mu[i]);
        }

        // Smooth coefficients: coeffs[j] is the coefficient vector for smooth j
        double[][] coeffs = new double[p][];
        for (int j = 0; j < p; j++) {
            coeffs[j] = new double[options.df()];
        }
        double[] centers = new double[p]; // centering constants
        double[] edf = new double[p];     // effective degrees of freedom per smooth

        double intercept = 0.0;

        // -------------------------------------------------------------------
        // 4. Outer PIRLS loop
        // -------------------------------------------------------------------
        double[] residuals = new double[n];
        double dev = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < options.maxIter(); iter++) {
            // Compute IRLS weights and adjusted dependent variable z
            for (int i = 0; i < n; i++) {
                double g  = model.dlink(mu[i]);   // g'(mu) = d(eta)/d(mu)
                double v  = model.variance(mu[i]);
                w[i] = 1.0 / (g * g * v);         // W_ii = 1 / (g'^2 * V(mu))
                z[i] = eta[i] + (y[i] - mu[i]) * g; // adjusted dependent variable
            }

            // ------------------------------------------------------------------
            // 5. Backfitting: estimate intercept + all f_j given weights w and z
            // ------------------------------------------------------------------
            intercept = weightedMean(z, w);

            for (int bfIter = 0; bfIter < options.backfitIter(); bfIter++) {
                double maxChange = 0.0;

                for (int j = 0; j < p; j++) {
                    // Compute partial residuals for predictor j:
                    // r_j = z - alpha - sum_{k != j} f_k(x_k)
                    double[] rj = new double[n];
                    for (int i = 0; i < n; i++) {
                        rj[i] = z[i] - intercept;
                        for (int k = 0; k < p; k++) {
                            if (k != j) {
                                rj[i] -= evaluateSmooth(basisMatrices[k], coeffs[k], i) - centers[k];
                            }
                        }
                    }

                    // Fit penalized weighted least squares for smooth j
                    double[] newCoeffs = fitPenalizedWLS(
                            basisMatrices[j], penalties[j], rj, w, lambdas[j]);

                    // Compute new center and center the smooth
                    double newCenter = 0.0;
                    double totalW = 0.0;
                    for (int i = 0; i < n; i++) {
                        double fi = evaluateSmooth(basisMatrices[j], newCoeffs, i);
                        newCenter += w[i] * fi;
                        totalW += w[i];
                    }
                    newCenter /= totalW;
                    centers[j] = newCenter;

                    // Track convergence: max change in smooth values
                    for (int i = 0; i < n; i++) {
                        double oldVal = evaluateSmooth(basisMatrices[j], coeffs[j], i) - centers[j];
                        double newVal = evaluateSmooth(basisMatrices[j], newCoeffs, i) - newCenter;
                        maxChange = Math.max(maxChange, Math.abs(newVal - oldVal));
                    }

                    coeffs[j] = newCoeffs;
                }

                // Update intercept given current smooths
                double[] fitted = computeLinearPredictor(z, intercept, basisMatrices, coeffs, centers, n, p);
                intercept = weightedMean(z, w) - weightedMean(fitted, w) + intercept;

                if (maxChange < options.backfitTol()) break;
            }

            // Compute EDF for each smooth after backfitting
            for (int j = 0; j < p; j++) {
                edf[j] = computeEdf(basisMatrices[j], penalties[j], w, lambdas[j]);
            }

            // -------------------------------------------------------------------
            // 6. Update eta and mu
            // -------------------------------------------------------------------
            for (int i = 0; i < n; i++) {
                eta[i] = intercept;
                for (int j = 0; j < p; j++) {
                    eta[i] += evaluateSmooth(basisMatrices[j], coeffs[j], i) - centers[j];
                }
                mu[i] = model.invlink(eta[i]);
            }

            // -------------------------------------------------------------------
            // 7. Check convergence
            // -------------------------------------------------------------------
            double newDev = model.deviance(y, mu, residuals);
            logger.info("GAM PIRLS iteration {}: deviance = {}", iter + 1, newDev);

            if (iter > 0 && Math.abs(dev - newDev) < options.tol() * (0.1 + Math.abs(dev))) {
                dev = newDev;
                break;
            }
            dev = newDev;
        }

        // -------------------------------------------------------------------
        // 8. Compute final statistics
        // -------------------------------------------------------------------
        double totalEdf = 1.0; // intercept
        for (double e : edf) totalEdf += e;

        double nullDev = model.nullDeviance(y, MathEx.mean(y));
        double logLik = model.logLikelihood(y, mu);

        // Build SmoothingSpline objects for the return value
        SmoothingSpline[] smoothFunctions = new SmoothingSpline[p];
        for (int j = 0; j < p; j++) {
            SmoothingSpline s = new SmoothingSpline(names[j], bases[j], lambdas[j]);
            s.coefficients = coeffs[j];
            s.center = centers[j];
            s.edf = edf[j];
            smoothFunctions[j] = s;
        }

        return new GAM(formula, model, intercept, smoothFunctions, mu, residuals,
                nullDev, dev, logLik, totalEdf);
    }

    // =========================================================================
    //  Private helper methods
    // =========================================================================

    /**
     * Evaluates the smooth for observation {@code i} using the given basis
     * matrix and coefficient vector.
     *
     * @param B      the basis matrix (n × df).
     * @param beta   the coefficient vector (length df).
     * @param i      the observation index.
     * @return {@code B[i, :] · beta}.
     */
    private static double evaluateSmooth(double[][] B, double[] beta, int i) {
        double val = 0.0;
        for (int k = 0; k < beta.length; k++) {
            val += B[i][k] * beta[k];
        }
        return val;
    }

    /**
     * Computes the weighted mean of an array.
     *
     * @param x the values.
     * @param w the weights.
     * @return the weighted mean.
     */
    private static double weightedMean(double[] x, double[] w) {
        double sumWx = 0.0, sumW = 0.0;
        for (int i = 0; i < x.length; i++) {
            sumWx += w[i] * x[i];
            sumW  += w[i];
        }
        return sumWx / sumW;
    }

    /**
     * Computes the fitted linear predictor values from intercept + smooths.
     * Used internally to re-center the intercept after backfitting.
     */
    private static double[] computeLinearPredictor(double[] z, double intercept,
                                                    double[][][] B, double[][] coeffs,
                                                    double[] centers, int n, int p) {
        double[] fitted = new double[n];
        for (int i = 0; i < n; i++) {
            fitted[i] = intercept;
            for (int j = 0; j < p; j++) {
                fitted[i] += evaluateSmooth(B[j], coeffs[j], i) - centers[j];
            }
        }
        return fitted;
    }

    /**
     * Fits a single penalized weighted least-squares problem for one smooth term.
     *
     * <p>Solves:
     * <pre>
     *     min_{beta} (r - B*beta)' W (r - B*beta) + lambda * beta' P beta
     * </pre>
     * which gives the normal equations:
     * <pre>
     *     (B'WB + lambda * P) beta = B'W r
     * </pre>
     *
     * @param B      the basis matrix (n × df).
     * @param P      the penalty matrix (df × df).
     * @param r      the partial residuals (length n).
     * @param w      the observation weights (length n).
     * @param lambda the smoothing parameter.
     * @return the estimated coefficient vector (length df).
     */
    private static double[] fitPenalizedWLS(double[][] B, double[][] P,
                                             double[] r, double[] w, double lambda) {
        int n = B.length;
        int df = B[0].length;

        // Build B'WB + lambda*P  (df × df)
        double[][] A = new double[df][df];
        for (int j = 0; j < df; j++) {
            for (int k = j; k < df; k++) {
                double s = 0.0;
                for (int i = 0; i < n; i++) {
                    s += w[i] * B[i][j] * B[i][k];
                }
                s += lambda * P[j][k];
                A[j][k] = s;
                A[k][j] = s;
            }
        }

        // Build B'W r  (length df)
        double[] rhs = new double[df];
        for (int j = 0; j < df; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) {
                s += w[i] * B[i][j] * r[i];
            }
            rhs[j] = s;
        }

        // Solve (B'WB + lambda*P) beta = B'W r using Cholesky
        DenseMatrix mat = DenseMatrix.of(A).withUplo(UPLO.UPPER);
        Cholesky chol = mat.cholesky();
        Vector sol = chol.solve(rhs);
        return sol.toArray(new double[0]);
    }

    /**
     * Computes the effective degrees of freedom for a single smooth, defined as
     * {@code trace(H_j)} where {@code H_j = B(B'WB + lambda*P)^{-1} B'W} is
     * the smoother/hat matrix for predictor {@code j}.
     *
     * <p>For computational efficiency, this is computed as:
     * <pre>
     *     edf_j = trace((B'WB + lambda*P)^{-1} B'WB)
     * </pre>
     *
     * @param B      the basis matrix (n × df).
     * @param P      the penalty matrix (df × df).
     * @param w      the observation weights (length n).
     * @param lambda the smoothing parameter.
     * @return the effective degrees of freedom.
     */
    private static double computeEdf(double[][] B, double[][] P, double[] w, double lambda) {
        int n = B.length;
        int df = B[0].length;

        // Build B'WB
        double[][] BtWB = new double[df][df];
        for (int j = 0; j < df; j++) {
            for (int k = j; k < df; k++) {
                double s = 0.0;
                for (int i = 0; i < n; i++) {
                    s += w[i] * B[i][j] * B[i][k];
                }
                BtWB[j][k] = s;
                BtWB[k][j] = s;
            }
        }

        // Build A = B'WB + lambda*P
        double[][] A = new double[df][df];
        for (int j = 0; j < df; j++) {
            for (int k = 0; k < df; k++) {
                A[j][k] = BtWB[j][k] + lambda * P[j][k];
            }
        }

        // Compute A^{-1} B'WB via Cholesky inverse of A
        DenseMatrix matA = DenseMatrix.of(A).withUplo(UPLO.UPPER);
        Cholesky chol = matA.cholesky();
        DenseMatrix Ainv = chol.inverse();

        // edf = trace(Ainv * BtWB)
        double edf = 0.0;
        for (int j = 0; j < df; j++) {
            for (int k = 0; k < df; k++) {
                edf += Ainv.get(j, k) * BtWB[k][j];
            }
        }
        return edf;
    }
}

