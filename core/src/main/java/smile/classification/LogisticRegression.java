/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.classification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import smile.math.MathEx;
import smile.math.DifferentiableMultivariateFunction;
import smile.math.BFGS;
import smile.util.MulticoreExecutor;

/**
 * Logistic regression. Logistic regression (logit model) is a generalized
 * linear model used for binomial regression. Logistic regression applies
 * maximum likelihood estimation after transforming the dependent into
 * a logit variable. A logit is the natural log of the odds of the dependent
 * equaling a certain value or not (usually 1 in binary logistic models,
 * the highest value in multinomial models). In this way, logistic regression
 * estimates the odds of a certain event (value) occurring. 
 * <p>
 * Goodness-of-fit tests such as the likelihood ratio test are available
 * as indicators of model appropriateness, as is the Wald statistic to test
 * the significance of individual independent variables. 
 * <p>
 * Logistic regression has many analogies to ordinary least squares (OLS)
 * regression. Unlike OLS regression, however, logistic regression does not
 * assume linearity of relationship between the raw values of the independent
 * variables and the dependent, does not require normally distributed variables,
 * does not assume homoscedasticity, and in general has less stringent
 * requirements.
 * <p>
 * Compared with linear discriminant analysis, logistic regression has several
 * advantages:
 * <ul>
 * <li> It is more robust: the independent variables don't have to be normally
 * distributed, or have equal variance in each group
 * <li> It does not assume a linear relationship between the independent
 * variables and dependent variable.
 * <li> It may handle nonlinear effects since one can add explicit interaction
 * and power terms.
 * </ul>
 * However, it requires much more data to achieve stable, meaningful results.
 * <p>
 * Logistic regression also has strong connections with neural network and
 * maximum entropy modeling. For example, binary logistic regression is
 * equivalent to a one-layer, single-output neural network with a logistic
 * activation function trained under log loss. Similarly, multinomial logistic
 * regression is equivalent to a one-layer, softmax-output neural network.
 * <p>
 * Logistic regression estimation also obeys the maximum entropy principle, and
 * thus logistic regression is sometimes called "maximum entropy modeling",
 * and the resulting classifier the "maximum entropy classifier".
 * 
 * @see NeuralNetwork
 * @see Maxent
 * @see LDA
 * 
 * @author Haifeng Li
 */
public class LogisticRegression implements SoftClassifier<double[]>, OnlineClassifier<double[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LogisticRegression.class);

    /**
     * The dimension of input space.
     */
    private int p;

    /**
     * The number of classes.
     */
    private int k;

    /**
     * The log-likelihood of learned model.
     */
    private double L;

    /**
     * The linear weights for binary logistic regression.
     */
    private double[] w;

    /**
     * The linear weights for multi-class logistic regression.
     */
    private double[][] W;

    /**
     * Regularization factor.
     */
    private double lambda;
    
    /**
     * learning rate for stochastic gradient descent.
     */
    private double eta = 5E-5;
    /**
     * Constructor of binary logistic regression.
     * @param L the log-likelihood of learned model.
     * @param w the weights.
     */
    public LogisticRegression(double[] w, double L, double lambda) {
        this.p = w.length - 1;
        this.k = 2;
        this.L = L;
        this.w = w;
        this.lambda = lambda;
    }

    /**
     * Constructor of multi-class logistic regression.
     * @param L the log-likelihood of learned model.
     * @param W the weights.
     */
    public LogisticRegression(double[][] W, double L, double lambda) {
        this.p = W[0].length - 1;
        this.k = W.length;
        this.L = L;
        this.W = W;
        this.lambda = lambda;
    }

    /**
     * Learn logistic regression.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static LogisticRegression fit(double[][] x, int[] y) {
        return fit(x, y, new Properties());
    }

    /**
     * Learn logistic regression.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static LogisticRegression fit(double[][] x, int[] y, Properties prop) {
        double lambda = Double.valueOf(prop.getProperty("smile.logistic.lambda", "0.1"));
        double tol = Double.valueOf(prop.getProperty("smile.logistic.tolerance", "1E-5"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.logistic.max.iterations", "500"));
        return fit(x, y, lambda, tol, maxIter);
    }

    /**
     * Learn logistic regression.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter the maximum number of iterations.
     */
    public static LogisticRegression fit(double[][] x, int[] y, double lambda, double tol, int maxIter) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid regularization factor: " + lambda);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        int k = Classifier.classes(y).length;

        int p = x[0].length;
        BFGS bfgs = new BFGS(tol, maxIter);
        if (k == 2) {
            BinaryObjectiveFunction func = new BinaryObjectiveFunction(x, y, lambda);

            double[] w = new double[p + 1];

            double L = -bfgs.minimize(func, 5, w);
            return new LogisticRegression(w, L, lambda);
        } else {
            MultiClassObjectiveFunction func = new MultiClassObjectiveFunction(x, y, k, lambda);
            double[] w = new double[k * (p + 1)];
            double L = -bfgs.minimize(func, 5, w);

            double[][] W = new double[k][p+1];
            for (int i = 0, m = 0; i < k; i++) {
                for (int j = 0; j <= p; j++, m++) {
                    W[i][j] = w[m];
                }
            }
            return new LogisticRegression(W, L, lambda);
        }
    }

    /**
     * Returns natural log(1+exp(x)) without overflow.
     */
    private static double log1pe(double x) {
        double y = 0.0;
        if (x > 15) {
            y = x;
        } else {
            y += Math.log1p(Math.exp(x));
        }

        return y;
    }

    /**
     * Binary-class logistic regression objective function.
     */
    static class BinaryObjectiveFunction implements DifferentiableMultivariateFunction {

        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training labels.
         */
        int[] y;
        /**
         * Regularization factor.
         */
        double lambda;
        
        /**
         * Constructor.
         */
        BinaryObjectiveFunction(double[][] x, int[] y, double lambda) {
            this.x = x;
            this.y = y;
            this.lambda = lambda;
        }

        @Override
        public double f(double[] w) {
            int n = x.length;
            int p = x[0].length;
            double f = IntStream.range(0, n).parallel().mapToDouble(i -> {
                double wx = dot(x[i], w);
                return log1pe(wx) - y[i] * wx;
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) wnorm += w[i] * w[i];
                f += 0.5 * lambda * wnorm;
            }

            return f;
        }

        @Override
        public double g(double[] w, double[] g) {
            final int p = w.length - 1;
            Arrays.fill(g, 0.0);
            double f = IntStream.range(0, x.length).sequential().mapToDouble(i -> {
                double wx = dot(x[i], w);

                double yi = y[i] - MathEx.logistic(wx);
                for (int j = 0; j < p; j++) {
                    g[j] -= yi * x[i][j];
                }
                g[p] -= yi;

                return log1pe(wx) - y[i] * wx;
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) {
                    g[i] += lambda * w[i];
                    wnorm += w[i] * w[i];
                }
                f += 0.5 * lambda * wnorm;
            }

            return f;
        }
    }

    /**
     * Returns natural log without underflow.
     */
    private static double log(double x) {
        double y = 0.0;
        if (x < 1E-300) {
            y = -690.7755;
        } else {
            y = Math.log(x);
        }
        return y;
    }

    /**
     * Multi-class logistic regression objective function.
     */
    static class MultiClassObjectiveFunction implements DifferentiableMultivariateFunction {

        /**
         * Training instances.
         */
        double[][] x;
        /**
         * Training labels.
         */
        int[] y;
        /**
         * The number of classes.
         */
        int k;
        /**
         * Regularization factor.
         */
        double lambda;

        /**
         * Constructor.
         */
        MultiClassObjectiveFunction(double[][] x, int[] y, int k, double lambda) {
            this.x = x;
            this.y = y;
            this.k = k;
            this.lambda = lambda;
        }

        @Override
        public double f(double[] w) {
            int p = x[0].length;
            double[] prob = new double[k];

            double f = IntStream.range(0, x.length).sequential().mapToDouble(i -> {
                for (int j = 0; j < k; j++) {
                    prob[j] = dot(x[i], w, j, p);
                }

                softmax(prob);

                return -log(prob[y[i]]);
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) wnorm += w[i] * w[i];
                f += 0.5 * lambda * wnorm;
            }

            return f;
        }

        @Override
        public double g(double[] w, double[] g) {
            int p = x[0].length;
            double[] prob = new double[k];
            Arrays.fill(g, 0.0);

            double f = IntStream.range(0, x.length).sequential().mapToDouble(i -> {
                for (int j = 0; j < k; j++) {
                    prob[j] = dot(x[i], w, j, p);
                }

                softmax(prob);

                double yi = 0.0;
                for (int j = 0; j < k; j++) {
                    yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                    for (int l = 0, pos = j * (p + 1); l < p; l++) {
                        g[pos + l] -= yi * x[i][l];
                    }
                    g[j * (p + 1) + p] -= yi;
                }

                return -log(prob[y[i]]);
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < p; j++) {
                        int pos = i * (p+1) + j;
                        wnorm += w[pos] * w[pos];
                        g[pos] += lambda * w[pos];
                    }
                }

                f += 0.5 * lambda * wnorm;
            }

            return f;
        }
    }

    @Override
    public void update(double[] x, int y) {
        if (y < 0 || y >= k) {
            throw new IllegalArgumentException("Invalid label");
        }

        if (x.length != p) {
            throw new IllegalArgumentException("Invalid input vector size: " + x.length);
        }

        if (k == 2) {
            // calculate gradient for incoming data
            double wx = dot(x, w);
            double res = y - MathEx.logistic(wx);

            // update the weights
            for (int j = 0; j <= p; j++) {
                double gj = j < p ? eta * res * x[j] : eta * res;
                w[j] += gj;

                // add regularization part
                if (lambda != 0.0) {
                    w[j] -= 2 * lambda * eta * w[j];
                }
            }
        } else {
            double[] prob = new double[k];
            for (int j = 0; j < k; j++) {
                prob[j] = dot(x, W[j]);
            }

            softmax(prob);

            // update the weights
            for (int j = 0; j < k; j++) {
                for (int l = 0; l <= p; l++) {
                    double yi = (y == j ? 1.0 : 0.0) - prob[j];
                    double gjl = l < p ? eta * yi * x[l] : eta * yi;
                    W[j][l] += gjl;

                    // add regularization part
                    if (lambda != 0.0) {
                        W[j][l] -= 2 * lambda * eta * W[j][l];
                    }
                }
            }
        }
    }

    /**
     * Sets the learning rate of stochastic gradient descent.
     * @param eta the learning rate, typically quite small to avoid oscillation.
     */
    public void setLearningRate(double eta) {
        this.eta = eta;
    }

    /**
     * Returns the learning rate of stochastic gradient descent.
     */
    public double getLearningRate() {
        return eta;
    }

    /**
     * Calculate softmax function without overflow.
     */
    private static void softmax(double[] prob) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < prob.length; i++) {
            if (prob[i] > max) {
                max = prob[i];
            }
        }

        double Z = 0.0;
        for (int i = 0; i < prob.length; i++) {
            double p = Math.exp(prob[i] - max);
            prob[i] = p;
            Z += p;
        }

        for (int i = 0; i < prob.length; i++) {
            prob[i] /= Z;
        }
    }

    /**
     * Returns the dot product between weight vector and x (augmented with 1).
     */
    private static double dot(double[] x, double[] w) {
        double dot = w[x.length];

        for (int i = 0; i < x.length; i++) {
            dot += x[i] * w[i];
        }

        return dot;
    }

    /**
     * Returns the dot product between weight vector and x (augmented with 1).
     */
    private static double dot(double[] x, double[] w, int j, int p) {
        int pos = j * (p + 1);
        double dot = w[pos + p];
        
        for (int i = 0; i < p; i++) {
            dot += x[i] * w[pos+i];
        }

        return dot;
    }

    /**
     * Returns the log-likelihood of model.
     */
    public double loglikelihood() {
        return L;
    }

    @Override
    public int predict(double[] x) {
        return predict(x, null);
    }

    @Override
    public int predict(double[] x, double[] posteriori) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        if (posteriori != null && posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        if (k == 2) {
            double f = 1.0 / (1.0 + Math.exp(-dot(x, w)));

            if (posteriori != null) {
                posteriori[0] = 1.0 - f;
                posteriori[1] = f;
            }

            if (f < 0.5) {
                return 0;
            } else {
                return 1;
            }
        } else {
            int label = -1;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < k; i++) {
                double prob = dot(x, W[i]);
                if (prob > max) {
                    max = prob;
                    label = i;
                }

                if (posteriori != null) {
                    posteriori[i] = prob;
                }
            }

            if (posteriori != null) {
                double Z = 0.0;
                for (int i = 0; i < k; i++) {
                    posteriori[i] = Math.exp(posteriori[i] - max);
                    Z += posteriori[i];
                }

                for (int i = 0; i < k; i++) {
                    posteriori[i] /= Z;
                }
            }
            
            return label;
        }
    }
}
