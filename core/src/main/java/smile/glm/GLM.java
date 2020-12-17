/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.glm;

import java.io.Serializable;
import java.util.Properties;
import java.util.stream.IntStream;

import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.glm.model.Model;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.special.Erf;
import smile.stat.Hypothesis;
import smile.validation.ModelSelection;

/**
 * Generalized linear models. The generalized linear model (GLM) is a flexible
 * generalization of ordinary linear regression that allows for response
 * variables that have error distribution models other than a normal
 * distribution. The GLM generalizes linear regression by allowing the
 * linear model to be related to the response variable via a link function
 * and by allowing the magnitude of the variance of each measurement to be
 * a function of its predicted value.
 * <p>
 * In GLM, each outcome <code>Y</code> of the dependent variables is assumed
 * to be generated from a particular distribution in an exponential family.
 * The mean, <code>&mu;</code>, of the distribution depends on the
 * independent variables, <code>X</code>, through:
 * <p>
 *     E(Y) = &mu; = g<sup>-1</sup>(X&beta;)
 * <p>
 * where <code>E(Y)</code> is the expected value of <code>Y</code>;
 * <code>X&beta;</code> is the linear combination of linear predictors
 * and unknown parameters &beta;; g is the link function that is a monotonic,
 * differentiable function. THe link function that transforms the mean to
 * the natural parameter is called the canonical link.
 * <p>
 * In this framework, the variance is typically a function, <code>V</code>,
 * of the mean:
 * <p>
 *     Var(Y) = V(&mu;) = V(g<sup>-1</sup>(X&beta;))
 * <p>
 * It is convenient if <code>V</code> follows from an exponential family
 * of distributions, but it may simply be that the variance is a function
 * of the predicted value, such as <code>V(&mu;<sub>i</sub>) = &mu;<sub>i</sub></code>
 * for the Poisson, <code>V(&mu;<sub>i</sub>) = &mu;<sub>i</sub>(1 - &mu;<sub>i</sub>)</code>
 * for the Bernoulli, and <code>V(&mu;<sub>i</sub>) = &sigma;<sup>2</sup></code>
 * (i.e., constant) for the normal.
 * <p>
 * The unknown parameters, <code>&beta;</code>, are typically estimated
 * with maximum likelihood, maximum quasi-likelihood, or Bayesian techniques.
 *
 * @author Haifeng Li
 */
public class GLM implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GLM.class);

    /**
     * The symbolic description of the model to be fitted.
     */
    protected Formula formula;
    /**
     * The predictors of design matrix.
     */
    String[] predictors;
    /**
     * The model specifications (link function, deviance, etc.).
     */
    protected Model model;
    /**
     * The linear weights.
     */
    protected double[] beta;
    /**
     * The coefficients, their standard errors, z-scores, and p-values.
     */
    protected double[][] ztest;
    /**
     * The fitted mean values.
     */
    protected double[] mu;
    /**
     * The null deviance = 2 * (LogLikelihood(Saturated Model) - LogLikelihood(Null Model)).
     * <p>
     * The saturated model, also referred to as the full model or maximal model,
     * allows a different mean response for each group of replicates.
     * One can think of the saturated model as having the most general
     * possible mean structure for the data since the means are unconstrained.
     * <p>
     * The null model assumes that all observations have the same distribution
     * with common parameter. Like the saturated model, the null model does not
     * depend on predictor variables. While the saturated most is the most
     * general model, the null model is the most restricted model.
     */
    protected double nullDeviance;
    /**
     * The deviance = 2 * (LogLikelihood(Saturated Model) - LogLikelihood(Proposed Model)).
     */
    protected double deviance;
    /**
     * The deviance residuals.
     */
    protected double[] devianceResiduals;
    /**
     * The degrees of freedom of the residual deviance.
     */
    protected int df;
    /**
     * Log-likelihood.
     */
    protected double loglikelihood;

    /**
     * Constructor.
     * @param formula the model formula.
     * @param predictors the predictors of design matrix.
     * @param model the generalized linear model specification.
     * @param beta the linear weights.
     * @param loglikelihood the log-likelihood.
     * @param deviance the deviance.
     * @param nullDeviance the null deviance.
     * @param mu the fitted mean values.
     * @param residuals the residuals of fitted values of training data.
     * @param ztest the z-test of the coefficients.
     */
    public GLM(Formula formula, String[] predictors, Model model, double[] beta, double loglikelihood, double deviance, double nullDeviance, double[] mu, double[] residuals, double[][] ztest) {
        this.formula = formula;
        this.model = model;
        this.predictors = predictors;
        this.beta = beta;
        this.loglikelihood = loglikelihood;
        this.deviance = deviance;
        this.nullDeviance = nullDeviance;
        this.mu = mu;
        this.devianceResiduals = residuals;
        this.ztest = ztest;
        df = mu.length - beta.length;
    }

    /**
     * Returns an array of size (p+1) containing the linear weights
     * of binary logistic regression, where p is the dimension of
     * feature vectors. The last element is the weight of bias.
     *
     * @return the linear weights.
     */
    public double[] coefficients() {
        return beta;
    }

    /**
     * Returns the z-test of the coefficients (including intercept).
     * The first column is the coefficients, the second column is the standard
     * error of coefficients, the third column is the z-score of the hypothesis
     * test if the coefficient is zero, the fourth column is the p-values of
     * test. The last row is of intercept.
     *
     * @return the z-test of the coefficients.
     */
    public double[][] ztest() {
        return ztest;
    }

    /**
     * Returns the deviance residuals.
     * @return the deviance residuals.
     */
    public double[] devianceResiduals() {
        return devianceResiduals;
    }

    /**
     * Returns the fitted mean values.
     * @return the fitted mean values.
     */
    public double[] fittedValues() {
        return mu;
    }

    /**
     * Returns the deviance of model.
     * @return the deviance of model.
     */
    public double deviance() {
        return deviance;
    }

    /**
     * Returns the log-likelihood of model.
     * @return the log-likelihood of model.
     */
    public double loglikelihood() {
        return loglikelihood;
    }

    /**
     * Returns the AIC score.
     * @return the AIC score.
     */
    public double AIC() {
        return ModelSelection.AIC(loglikelihood, beta.length);
    }

    /**
     * Returns the BIC score.
     * @return the BIC score.
     */
    public double BIC() {
        return ModelSelection.BIC(loglikelihood, beta.length, mu.length);
    }

    /**
     * Predicts the mean response.
     * @param x the instance.
     * @return the mean response.
     */
    public double predict(Tuple x) {
        double[] a = formula.x(x).toArray(true, CategoricalEncoder.DUMMY);
        int p = beta.length;
        double dot = 0.0;
        for (int i = 0; i < p; i++) {
            dot += a[i] * beta[i];
        }

        return model.invlink(dot);
    }

    /**
     * Predicts the mean response.
     * @param data the data frame.
     * @return the mean response.
     */
    public double[] predict(DataFrame data) {
        Matrix X = formula.matrix(data, true);
        double[] y = X.mv(beta);
        int n = y.length;
        for (int i = 0; i < n; i++) {
            y[i] = model.invlink(y[i]);
        }
        return y;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Generalized Linear Model - %s:\n", model));

        double[] r = devianceResiduals.clone();
        builder.append("\nDeviance Residuals:\n");
        builder.append("       Min          1Q      Median          3Q         Max\n");
        builder.append(String.format("%10.4f  %10.4f  %10.4f  %10.4f  %10.4f%n", MathEx.min(r), MathEx.q1(r), MathEx.median(r), MathEx.q3(r), MathEx.max(r)));

        int p = beta.length - 1;
        builder.append("\nCoefficients:\n");
        if (ztest != null) {
            builder.append("                  Estimate Std. Error    z value   Pr(>|z|)\n");
            for (int i = 0; i < p; i++) {
                builder.append(String.format("%-15s %10.3e %10.3e %10.4f %10.5f %s%n", predictors[i], ztest[i][0], ztest[i][1], ztest[i][2], ztest[i][3], Hypothesis.significance(ztest[i][3])));
            }

            builder.append("---------------------------------------------------------------------\n");
            builder.append("Significance codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1\n");
        } else {
            builder.append(String.format("Intercept       %10.4f%n", beta[p]));
            for (int i = 0; i < p; i++) {
                builder.append(String.format("%-15s %10.4f%n", predictors[i], beta[i]));
            }
        }

        builder.append(String.format("%n    Null deviance: %.1f on %d degrees of freedom", nullDeviance, df+p));
        builder.append(String.format("%nResidual deviance: %.1f on %d degrees of freedom", deviance, df));
        builder.append(String.format("%nAIC: %.4f     BIC: %.4f%n", AIC(), BIC()));

        return builder.toString();
    }

    /**
     * Fits the generalized linear model with IWLS (iteratively reweighted least squares).
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param model the generalized linear model specification.
     * @return the model.
     */
    public static GLM fit(Formula formula, DataFrame data, Model model) {
        return fit(formula, data, model, new Properties());
    }

    /**
     * Fits the generalized linear model with IWLS (iteratively reweighted least squares).
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param model the generalized linear model specification.
     * @param prop the hyper-parameters.
     * @return the model.
     */
    public static GLM fit(Formula formula, DataFrame data, Model model, Properties prop) {
        double tol = Double.parseDouble(prop.getProperty("smile.glm.tolerance", "1E-5"));
        int maxIter = Integer.parseInt(prop.getProperty("smile.glm.max.iterations", "50"));
        return fit(formula, data, model, tol, maxIter);
    }

    /**
     * Fits the generalized linear model with IWLS (iteratively reweighted least squares).
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     * @param model the generalized linear model specification.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter the maximum number of iterations.
     * @return the model.
     */
    public static GLM fit(Formula formula, DataFrame data, Model model, double tol, int maxIter) {
        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);
        }

        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
        }

        Matrix X = formula.matrix(data, true);
        Matrix XW = new Matrix(X.nrow(), X.ncol());
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();

        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        double[] eta = new double[n];
        double[] mu = new double[n];
        double[] w = new double[n]; // sqrt of diagonal of W
        double[] z = new double[n];
        double[] residuals = new double[n];

        // Initialization
        IntStream.range(0, n).parallel().forEach(i -> {
            mu[i] = model.mustart(y[i]);
            eta[i] = model.link(mu[i]);
            double g = model.dlink(mu[i]); //
            z[i] = eta[i] + (y[i] - mu[i]) * g;
            double v = model.variance(mu[i]);
            w[i] = 1.0 / (g * Math.sqrt(v));
            z[i] *= w[i];
        });

        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                XW.set(i, j, X.get(i, j) * w[i]);
            }
        }

        Matrix.QR qr = XW.qr(true);
        double[] beta = qr.solve(z);

        double dev = Double.POSITIVE_INFINITY;
        for (int iter = 0; iter < maxIter; iter++) {
            X.mv(beta, eta);
            IntStream.range(0, n).parallel().forEach(i -> {
                mu[i] = model.invlink(eta[i]);
                double g = model.dlink(mu[i]);
                z[i] = eta[i] + (y[i] - mu[i]) * g;
                double v = model.variance(mu[i]);
                w[i] = 1.0 / (g * Math.sqrt(v));
                z[i] *= w[i];
            });

            double newDev = model.deviance(y, mu, residuals);
            if (iter > 0) {
                logger.info(String.format("Deviance after %3d iterations: %.5f", iter, dev));
            }

            if (dev - newDev < tol) {
                break;
            }

            dev = newDev;
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    XW.set(i, j, X.get(i, j) * w[i]);
                }
            }

            qr = XW.qr(true);
            beta = qr.solve(z);
        }

        Matrix.Cholesky cholesky = qr.CholeskyOfAtA();
        Matrix inv = cholesky.inverse();
        double[][] ztest = new double[p][4];
        for (int i = 0; i < p; i++) {
            ztest[i][0] = beta[i];
            ztest[i][1] = Math.sqrt(inv.get(i, i));
            ztest[i][2] = ztest[i][0] / ztest[i][1];
            ztest[i][3] = 2.0 - Erf.erfc(-0.707106781186547524 * Math.abs(ztest[i][2]));
        }

        return new GLM(formula, X.colNames(), model, beta, model.loglikelihood(y, mu), dev, model.nullDeviance(y, MathEx.mean(y)), mu, residuals, ztest);
    }
}
