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
import smile.math.BFGS;
import smile.math.MathEx;
import smile.math.DifferentiableMultivariateFunction;
import smile.util.IntSet;

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
public class Maxent implements SoftClassifier<int[]>, OnlineClassifier<int[]> {
    private static final long serialVersionUID = 2L;

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
     * learning rate for stochastic gradient descent.
     */
    private double eta = 0.1;

    /**
     * The class label encoder.
     */
    private final IntSet labels;

    /**
     * Constructor of binary maximum entropy classifier.
     * @param L the log-likelihood of learned model.
     * @param w the weights.
     */
    public Maxent(double L, double[] w) {
        this(L, w, IntSet.of(2));
    }

    /**
     * Constructor of binary maximum entropy classifier.
     * @param L the log-likelihood of learned model.
     * @param w the weights.
     * @param labels class labels
     */
    public Maxent(double L, double[] w, IntSet labels) {
        this.p = w.length - 1;
        this.k = 2;
        this.L = L;
        this.w = w;
        this.labels = labels;
    }

    /**
     * Constructor of multi-class maximum entropy classifier.
     * @param L the log-likelihood of learned model.
     * @param W the weights of first k - 1 classes.
     */
    public Maxent(double L, double[][] W) {
        this(L, W, IntSet.of(W.length+1));
    }

    /**
     * Constructor of multi-class maximum entropy classifier.
     * @param L the log-likelihood of learned model.
     * @param W the weights of first k - 1 classes.
     * @param labels class labels
     */
    public Maxent(double L, double[][] W, IntSet labels) {
        this.p = W[0].length - 1;
        this.k = W.length + 1;
        this.L = L;
        this.W = W;
        this.labels = labels;
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
     * @param tol the tolerance for stopping iterations.
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

        ClassLabels codec = ClassLabels.fit(y);
        int k = codec.k;
        y = codec.y;

        Maxent model;
        BFGS bfgs = new BFGS(tol, maxIter);
        if (k == 2) {
            BinaryObjectiveFunction func = new BinaryObjectiveFunction(x, y, p, lambda);
            double[] w = new double[p + 1];
            double L = -bfgs.minimize(func, 5, w);
            model = new Maxent(L, w, codec.labels);
        } else {
            MultiClassObjectiveFunction func = new MultiClassObjectiveFunction(x, y, k, p, lambda);
            double[] w = new double[(k - 1) * (p + 1)];
            double L = -bfgs.minimize(func, 5, w);

            double[][] W = new double[k-1][p+1];
            for (int i = 0, l = 0; i < k-1; i++) {
                for (int j = 0; j <= p; j++, l++) {
                    W[i][j] = w[l];
                }
            }

            model = new Maxent(L, W, codec.labels);
        }

        model.setLearningRate(0.1 / x.length);
        return model;
    }

    /**
     * Returns the dimension of input space.
     * @return the dimension of input space.
     */
    public int dimension() {
        return p;
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
         * The dimension of feature space.
         */
        int p;
        /**
         * Regularization factor.
         */
        double lambda;
        /**
         * The number of samples in a partition.
         */
        int partitionSize;
        /**
         * The number of partitions.
         */
        int partitions;
        /**
         * The workspace to store gradient for each data partition.
         */
        double[][] gradients;

        /**
         * Constructor.
         */
        BinaryObjectiveFunction(int[][] x, int[] y, int p, double lambda) {
            this.x = x;
            this.y = y;
            this.p = p;
            this.lambda = lambda;

            partitionSize = Integer.valueOf(System.getProperty("smile.data.partition.size", "1000"));
            partitions = x.length / partitionSize + (x.length % partitionSize == 0 ? 0 : 1);
            gradients = new double[partitions][p+1];
        }
        
        @Override
        public double f(double[] w) {
            // Since BFGS try to minimize the objective function
            // and we try to maximize the log-likelihood, we really
            // return the negative log-likelihood here.
            double f = IntStream.range(0, x.length).parallel().mapToDouble(i -> {
                double wx = dot(x[i], w);
                return MathEx.log1pe(wx) - y[i] * wx;
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
            double f = IntStream.range(0, partitions).parallel().mapToDouble(r -> {
                double[] gradient = gradients[r];
                Arrays.fill(gradient, 0.0);

                int begin = r * partitionSize;
                int end = (r + 1) * partitionSize;
                if (end > x.length) end = x.length;

                return IntStream.range(begin, end).sequential().mapToDouble(i -> {
                    double wx = dot(x[i], w);
                    double err = y[i] - MathEx.logistic(wx);
                    for (int j : x[i]) {
                        gradient[j] -= err;
                    }
                    gradient[p] -= err;

                    return MathEx.log1pe(wx) - y[i] * wx;
                }).sum();
            }).sum();

            Arrays.fill(g, 0.0);
            for (double[] gradient : gradients) {
                for (int i = 0; i < g.length; i++) {
                    g[i] += gradient[i];
                }
            }

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
         * The number of samples in a partition.
         */
        int partitionSize;
        /**
         * The number of partitions.
         */
        int partitions;
        /**
         * The workspace to store gradient for each data partition.
         */
        double[][] gradients;
        /**
         * The workspace to store posteriori probability for each data partition.
         */
        double[][] posterioris;

        /**
         * Constructor.
         */
        MultiClassObjectiveFunction(int[][] x, int[] y, int k, int p, double lambda) {
            this.x = x;
            this.y = y;
            this.k = k;
            this.p = p;
            this.lambda = lambda;

            partitionSize = Integer.valueOf(System.getProperty("smile.data.partition.size", "1000"));
            partitions = x.length / partitionSize + (x.length % partitionSize == 0 ? 0 : 1);
            gradients = new double[partitions][(k-1)*(p+1)];
            posterioris = new double[partitions][k];
        }
        
        @Override
        public double f(double[] w) {
            double f = IntStream.range(0, partitions).parallel().mapToDouble(r -> {
                double[] posteriori = posterioris[r];

                int begin = r * partitionSize;
                int end = (r + 1) * partitionSize;
                if (end > x.length) end = x.length;

                return IntStream.range(begin, end).sequential().mapToDouble(i -> {
                    posteriori[k - 1] = 0.0;
                    for (int j = 0; j < k - 1; j++) {
                        posteriori[j] = dot(x[i], w, j, p);
                    }

                    MathEx.softmax(posteriori);

                    return -MathEx.log(posteriori[y[i]]);
                }).sum();
            }).sum();

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < k-1; i++) {
                    for (int j = 0, pos = i * (p+1); j < p; j++) {
                        double wi = w[pos + j];
                        wnorm += wi * wi;
                    }
                }
                f += 0.5 * lambda * wnorm;
            }

            return f;
        }

        @Override
        public double g(double[] w, double[] g) {
            double f = IntStream.range(0, partitions).parallel().mapToDouble(r -> {
                double[] posteriori = posterioris[r];
                double[] gradient = gradients[r];
                Arrays.fill(gradient, 0.0);

                int begin = r * partitionSize;
                int end = (r + 1) * partitionSize;
                if (end > x.length) end = x.length;

                return IntStream.range(begin, end).sequential().mapToDouble(i -> {
                    posteriori[k - 1] = 0.0;
                    for (int j = 0; j < k - 1; j++) {
                        posteriori[j] = dot(x[i], w, j, p);
                    }

                    MathEx.softmax(posteriori);

                    for (int j = 0; j < k - 1; j++) {
                        double err = (y[i] == j ? 1.0 : 0.0) - posteriori[j];

                        int pos = j * (p + 1);
                        for (int l : x[i]) {
                            gradient[pos + l] -= err;
                        }
                        gradient[pos + p] -= err;
                    }

                    return -MathEx.log(posteriori[y[i]]);
                }).sum();
            }).sum();

            Arrays.fill(g, 0.0);
            for (double[] gradient : gradients) {
                for (int i = 0; i < g.length; i++) {
                    g[i] += gradient[i];
                }
            }

            if (lambda > 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < k-1; i++) {
                    for (int j = 0, pos = i * (p+1); j < p; j++) {
                        double wi = w[pos + j];
                        wnorm += wi * wi;
                        g[pos + j] += lambda * wi;
                    }
                }

                f += 0.5 * lambda * wnorm;
            }

            return f;
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

    @Override
    public void update(int[] x, int y) {
        y = labels.indexOf(y);
        if (k == 2) {
            // calculate gradient for incoming data
            double wx = dot(x, w);
            double err = y - MathEx.logistic(wx);

            // update the weights
            w[p] += eta * err;
            for (int j : x) {
                w[j] += eta * err;
            }
        } else {
            double[] prob = new double[k];
            for (int j = 0; j < k-1; j++) {
                prob[j] = dot(x, W[j]);
            }

            MathEx.softmax(prob);

            // update the weights
            for (int i = 0; i < k-1; i++) {
                double[] w = W[i];
                double err = (y == i ? 1.0 : 0.0) - prob[i];
                w[p] += eta * err;
                for (int j : x) {
                    w[j] += eta * err;
                }
            }
        }
    }

    /**
     * Sets the learning rate of stochastic gradient descent.
     * It is a good practice to adapt the learning rate for
     * different data sizes. For example, it is typical to
     * set the learning rate to eta/n, where eta is in [0.1, 0.3]
     * and n is the size of the training data.
     *
     * @param rate the learning rate.
     */
    public void setLearningRate(double rate) {
        if (rate <= 0.0) {
            throw new IllegalArgumentException("Invalid learning rate: " + rate);
        }
        this.eta = rate;
    }

    /**
     * Returns the learning rate of stochastic gradient descent.
     */
    public double getLearningRate() {
        return eta;
    }

    /**
     * Returns the log-likelihood of model.
     */
    public double loglikelihood() {
        return L;
    }

    @Override
    public int predict(int[] x) {
        if (k == 2) {
            double f = 1.0 / (1.0 + Math.exp(-dot(x, w)));
            return labels.valueOf(f < 0.5 ? 0 : 1);
        } else {
            return predict(x, new double[k]);
        }
    }

    @Override
    public int predict(int[] x, double[] posteriori) {
        if (posteriori != null && posteriori.length != k) {
            throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
        }

        if (k == 2) {
            double f = 1.0 / (1.0 + Math.exp(-dot(x, w)));
            posteriori[0] = 1.0 - f;
            posteriori[1] = f;
            return labels.valueOf(f < 0.5 ? 0 : 1);
        } else {
            posteriori[k-1] = 0.0;
            for (int i = 0; i < k-1; i++) {
                posteriori[i] = dot(x, W[i]);
            }

            MathEx.softmax(posteriori);
            return labels.valueOf(MathEx.whichMax(posteriori));
        }
    }
}
