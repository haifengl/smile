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

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.BFGS;
import smile.math.MathEx;
import smile.math.DifferentiableMultivariateFunction;

/**
 * Maximum Entropy Classifier. Maximum entropy is a technique for learning
 * probability distributions from data. In maximum entropy models, the
 * observed data itself is assumed to be the testable information. Maximum
 * entropy models don't assume anything about the probability distribution
 * other than what have been observed and always choose the most uniform
 * distribution subject to the observed constraints.
 * <p>
 * Basically, maximum entropy classifier is another name of multinomial logistic
 * regression applied to categorical independent variables, which are
 * converted to binary dummy variables. Maximum entropy models are widely
 * used in natural language processing.  Here, we provide an implementation
 * which assumes that binary features are stored in a sparse array, of which
 * entries are the indices of nonzero features.
 * 
 * @see LogisticRegression
 * 
 * <h2>References</h2>
 * <ol>
 * <li> A. L. Berger, S. D. Pietra, and V. J. D. Pietra. A maximum entropy approach to natural language processing. Computational Linguistics 22(1):39-71, 1996.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public class Maxent implements SoftClassifier<int[]> {
    private static final long serialVersionUID = 2L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Maxent.class);

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
     * Constructor of binary logistic regression.
     * @param L the log-likelihood of learned model.
     * @param w the weights.
     */
    public Maxent(double L, double[] w) {
        this.p = w.length - 1;
        this.k = 2;
        this.L = L;
        this.w = w;
    }

    /**
     * Constructor of multi-class logistic regression.
     * @param L the log-likelihood of learned model.
     * @param W the weights.
     */
    public Maxent(double L, double[][] W) {
        this.p = W[0].length - 1;
        this.k = W.length;
        this.L = L;
        this.W = W;
    }
    /**
     * Learn maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static Maxent fit(int p, int[][] x, int[] y) {
        return fit(p, x, y, new Properties());
    }

    /**
     * Learn maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static Maxent fit(int p, int[][] x, int[] y, Properties prop) {
        double lambda = Double.valueOf(prop.getProperty("smile.maxent.lambda", "0.1"));
        double tol = Double.valueOf(prop.getProperty("smile.maxent.tolerance", "1E-5"));
        int maxIter = Integer.valueOf(prop.getProperty("smile.maxent.max.iterations", "500"));
        return fit(p, x, y, lambda, tol, maxIter);
    }

    /**
     * Learn maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     * @param tol tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     */
    public static Maxent fit(int p, int[][] x, int[] y, double lambda, double tol, int maxIter) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (p < 0) {
            throw new IllegalArgumentException("Invalid dimension: " + p);            
        }
        
        if (lambda < 0) {
            throw new IllegalArgumentException("Invalid regularization factor: " + lambda);
        }

        if (tol <= 0.0) {
            throw new IllegalArgumentException("Invalid tolerance: " + tol);            
        }
        
        if (maxIter <= 0) {
            throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);            
        }
        
        int k = Classifier.classes(y).length;

        BFGS bfgs = new BFGS(tol, maxIter);
        if (k == 2) {
            BinaryObjectiveFunction func = new BinaryObjectiveFunction(x, y, lambda);
            double[] w = new double[p + 1];
            double L = -bfgs.minimize(func, 5, w);
            return new Maxent(L, w);
        } else {
            MultiClassObjectiveFunction func = new MultiClassObjectiveFunction(x, y, k, p, lambda);
            double[] w = new double[k * (p + 1)];
            double L = -bfgs.minimize(func, 5, w);

            double[][] W = new double[k][p+1];
            for (int i = 0, m = 0; i < k; i++) {
                for (int j = 0; j <= p; j++, m++) {
                    W[i][j] = w[m];
                }
            }

            return new Maxent(L, W);
        }
    }

    /**
     * Returns the dimension of input space.
     * @return the dimension of input space.
     */
    public int dimension() {
        return p;
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
        int[][] x;
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
        BinaryObjectiveFunction(int[][] x, int[] y, double lambda) {
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
                for (int j : x[i]) {
                    g[j] -= yi * j;
                }
                g[p] -= yi;

                return log1pe(wx) - y[i] * wx;
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) {
                    wnorm += w[i] * w[i];
                    g[i] += lambda * w[i];
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
        int[][] x;
        /**
         * Training labels.
         */
        int[] y;
        /**
         * The number of classes.
         */
        int k;
        /**
         * The dimension of feature space.
         */
        int p;
        /**
         * Regularization factor.
         */
        double lambda;

        /**
         * Constructor.
         */
        MultiClassObjectiveFunction(int[][] x, int[] y, int k, int p, double lambda) {
            this.x = x;
            this.y = y;
            this.k = k;
            this.p = p;
            this.lambda = lambda;
        }
        
        @Override
        public double f(double[] w) {
            double f = IntStream.range(0, x.length).sequential().mapToDouble(i -> {
                double[] prob = new double[k];
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
            Arrays.fill(g, 0.0);

            double f = IntStream.range(0, x.length).sequential().mapToDouble(i -> {
                double[] prob = new double[k];
                for (int j = 0; j < k; j++) {
                    prob[j] = dot(x[i], w, j, p);
                }

                softmax(prob);

                double yi = 0.0;
                for (int j = 0; j < k; j++) {
                    yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                    int pos = j * (p + 1);
                    for (int l : x[i]) {
                        g[pos + l] -= yi;
                    }
                    g[pos + p] -= yi;
                }

                return -log(prob[y[i]]);
            }).sum();

            if (lambda != 0.0) {
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
    private static double dot(int[] x, double[] w) {
        double dot = w[w.length - 1];

        for (int i : x) {
            dot += w[i];
        }

        return dot;
    }

    /**
     * Returns the dot product between weight vector and x (augmented with 1).
     */
    private static double dot(int[] x, double[] w, int j, int p) {
        int pos = j * (p + 1);
        double dot = w[pos + p];

        for (int i : x) {
            dot += w[pos+i];
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
    public int predict(int[] x) {
        return predict(x, null);
    }

    @Override
    public int predict(int[] x, double[] posteriori) {
        if (posteriori != null && posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        if (w != null) {
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
