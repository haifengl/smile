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

package smile.classification;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.IntStream;
import smile.math.BFGS;
import smile.math.MathEx;
import smile.math.DifferentiableMultivariateFunction;
import smile.util.IntSet;
import smile.validation.ModelSelection;

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
 * @see smile.glm.GLM
 * @see LogisticRegression
 * 
 * <h2>References</h2>
 * <ol>
 * <li> A. L. Berger, S. D. Pietra, and V. J. D. Pietra. A maximum entropy approach to natural language processing. Computational Linguistics 22(1):39-71, 1996.</li>
 * </ol>
 * 
 * @author Haifeng Li
 */
public abstract class Maxent extends AbstractClassifier<int[]> {
    private static final long serialVersionUID = 2L;

    /**
     * The dimension of input space.
     */
    int p;

    /**
     * The number of classes.
     */
    int k;

    /**
     * The log-likelihood of learned model.
     */
    double L;

    /**
     * Regularization factor.
     */
    double lambda;

    /**
     * learning rate for stochastic gradient descent.
     */
    double eta = 0.1;

    /**
     * Constructor.
     * @param p the dimension of input data.
     * @param L the log-likelihood of learned model.
     * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
     *               weights which often has superior generalization performance,
     *               especially when the dimensionality is high.
     * @param labels the class label encoder.
     */
    public Maxent(int p, double L, double lambda, IntSet labels) {
        super(labels);
        this.k = labels.size();
        this.p = p;
        this.L = L;
        this.lambda = lambda;
    }

    /** Binomial maximum entropy classifier. The dependent variable is nominal of two levels. */
    public static class Binomial extends Maxent {
        /**
         * The linear weights.
         */
        private final double[] w;

        /**
         * Constructor.
         * @param w the weights.
         * @param L the log-likelihood of learned model.
         * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
         *               weights which often has superior generalization performance,
         *               especially when the dimensionality is high.
         * @param labels the class label encoder.
         */
        public Binomial(double[] w, double L, double lambda, IntSet labels) {
            super(w.length - 1, L, lambda, labels);
            this.w = w;
        }

        /**
         * Returns an array of size (p+1) containing the linear weights
         * of binary logistic regression, where p is the dimension of
         * feature vectors. The last element is the weight of bias.
         * @return the linear weights.
         */
        public double[] coefficients() {
            return w;
        }

        @Override
        public double score(int[] x) {
            return 1.0 / (1.0 + Math.exp(-dot(x, w)));
        }

        @Override
        public int predict(int[] x) {
            double f = 1.0 / (1.0 + Math.exp(-dot(x, w)));
            return classes.valueOf(f < 0.5 ? 0 : 1);
        }

        @Override
        public int predict(int[] x, double[] posteriori) {
            if (posteriori.length != k) {
                throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
            }

            double f = 1.0 / (1.0 + Math.exp(-dot(x, w)));
            posteriori[0] = 1.0 - f;
            posteriori[1] = f;
            return classes.valueOf(f < 0.5 ? 0 : 1);
        }

        @Override
        public void update(int[] x, int y) {
            y = classes.indexOf(y);
            // calculate gradient for incoming data
            double wx = dot(x, w);
            double err = y - MathEx.sigmoid(wx);

            // update the weights
            w[p] += eta * err;
            for (int j : x) {
                w[j] += eta * err;
            }
        }
    }

    /** Multinomial maximum entropy classifier. The dependent variable is nominal with more than two levels. */
    public static class Multinomial extends Maxent {
        /**
         * The linear weights.
         */
        private final double[][] w;

        /**
         * Constructor.
         * @param w the weights.
         * @param L the log-likelihood of learned model.
         * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
         *               weights which often has superior generalization performance,
         *               especially when the dimensionality is high.
         * @param labels the class label encoder.
         */
        public Multinomial(double[][] w, double L, double lambda, IntSet labels) {
            super(w[0].length - 1, L, lambda, labels);
            this.w = w;
        }

        /**
         * Returns a 2d-array of size (k-1) x (p+1), containing the linear weights
         * of multi-class logistic regression, where k is the number of classes
         * and p is the dimension of feature vectors. The last element of each
         * row is the weight of bias.
         * @return the linear weights.
         */
        public double[][] coefficients() {
            return w;
        }

        @Override
        public int predict(int[] x) {
            return predict(x, new double[k]);
        }

        @Override
        public int predict(int[] x, double[] posteriori) {
            if (posteriori.length != k) {
                throw new IllegalArgumentException(String.format("Invalid posteriori vector size: %d, expected: %d", posteriori.length, k));
            }

            posteriori[k-1] = 0.0;
            for (int i = 0; i < k-1; i++) {
                posteriori[i] = dot(x, w[i]);
            }

            MathEx.softmax(posteriori);
            return classes.valueOf(MathEx.whichMax(posteriori));
        }

        @Override
        public void update(int[] x, int y) {
            y = classes.indexOf(y);
            double[] prob = new double[k];
            for (int j = 0; j < k-1; j++) {
                prob[j] = dot(x, w[j]);
            }

            MathEx.softmax(prob);

            // update the weights
            for (int i = 0; i < k-1; i++) {
                double[] wi = w[i];
                double err = (y == i ? 1.0 : 0.0) - prob[i];
                wi[p] += eta * err;
                for (int j : x) {
                    wi[j] += eta * err;
                }
            }
        }
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @return the model.
     */
    public static Maxent fit(int p, int[][] x, int[] y) {
        return fit(p, x, y, new Properties());
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static Maxent fit(int p, int[][] x, int[] y, Properties params) {
        double lambda = Double.parseDouble(params.getProperty("smile.maxent.lambda", "0.1"));
        double tol = Double.parseDouble(params.getProperty("smile.maxent.tolerance", "1E-5"));
        int maxIter = Integer.parseInt(params.getProperty("smile.maxent.iterations", "500"));
        return fit(p, x, y, lambda, tol, maxIter);
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
     *               weights which often has superior generalization performance,
     *               especially when the dimensionality is high.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     * @return the model.
     */
    public static Maxent fit(int p, int[][] x, int[] y, double lambda, double tol, int maxIter) {
        ClassLabels codec = ClassLabels.fit(y);
        if (codec.k == 2)
            return binomial(p, x, y, lambda, tol, maxIter);
        else
            return multinomial(p, x, y, lambda, tol, maxIter);
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @return the model.
     */
    public static Binomial binomial(int p, int[][] x, int[] y) {
        return binomial(p, x, y, new Properties());
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static Binomial binomial(int p, int[][] x, int[] y, Properties params) {
        double lambda = Double.parseDouble(params.getProperty("smile.maxent.lambda", "0.1"));
        double tol = Double.parseDouble(params.getProperty("smile.maxent.tolerance", "1E-5"));
        int maxIter = Integer.parseInt(params.getProperty("smile.maxent.iterations", "500"));
        return binomial(p, x, y, lambda, tol, maxIter);
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     * @return the model.
     */
    public static Binomial binomial(int p, int[][] x, int[] y, double lambda, double tol, int maxIter) {
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
        if (k != 2) {
            throw new IllegalArgumentException("Fits binomial model on multi-class data.");
        }

        BinomialObjective objective = new BinomialObjective(x, codec.y, p, lambda);
        double[] w = new double[p + 1];
        double L = -BFGS.minimize(objective, 5, w, tol, maxIter);
        Binomial model = new Binomial(w, L, lambda, codec.classes);
        model.setLearningRate(0.1 / x.length);
        return model;
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @return the model.
     */
    public static Multinomial multinomial(int p, int[][] x, int[] y) {
        return multinomial(p, x, y, new Properties());
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param params the hyper-parameters.
     * @return the model.
     */
    public static Multinomial multinomial(int p, int[][] x, int[] y, Properties params) {
        double lambda = Double.parseDouble(params.getProperty("smile.maxent.lambda", "0.1"));
        double tol = Double.parseDouble(params.getProperty("smile.maxent.tolerance", "1E-5"));
        int maxIter = Integer.parseInt(params.getProperty("smile.maxent.iterations", "500"));
        return multinomial(p, x, y, lambda, tol, maxIter);
    }

    /**
     * Fits maximum entropy classifier.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda {@code lambda > 0} gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter maximum number of iterations.
     * @return the model.
     */
    public static Multinomial multinomial(int p, int[][] x, int[] y, double lambda, double tol, int maxIter) {
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
        if (k <= 2) {
            throw new IllegalArgumentException("Fits multinomial model on binary class data.");
        }

        MultinomialObjective objective = new MultinomialObjective(x, codec.y, k, p, lambda);
        double[] w = new double[(k - 1) * (p + 1)];
        double L = -BFGS.minimize(objective, 5, w, tol, maxIter);

        double[][] W = new double[k-1][p+1];
        for (int i = 0, l = 0; i < k-1; i++) {
            for (int j = 0; j <= p; j++, l++) {
                W[i][j] = w[l];
            }
        }

        Multinomial model = new Multinomial(W, L, lambda, codec.classes);
        model.setLearningRate(0.1 / x.length);
        return model;
    }

    /**
     * Binary-class logistic regression objective function.
     */
    static class BinomialObjective implements DifferentiableMultivariateFunction {

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
        BinomialObjective(int[][] x, int[] y, int p, double lambda) {
            this.x = x;
            this.y = y;
            this.p = p;
            this.lambda = lambda;

            partitionSize = Integer.parseInt(System.getProperty("smile.data.partition.size", "1000"));
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
                    double err = y[i] - MathEx.sigmoid(wx);
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
    static class MultinomialObjective implements DifferentiableMultivariateFunction {

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
        MultinomialObjective(int[][] x, int[] y, int k, int p, double lambda) {
            this.x = x;
            this.y = y;
            this.k = k;
            this.p = p;
            this.lambda = lambda;

            partitionSize = Integer.parseInt(System.getProperty("smile.data.partition.size", "1000"));
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

    /**
     * Returns the dimension of input space.
     * @return the dimension of input space.
     */
    public int dimension() {
        return p;
    }

    @Override
    public boolean soft() {
        return true;
    }

    @Override
    public boolean online() {
        return true;
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
     * @return the learning rate of stochastic gradient descent.
     */
    public double getLearningRate() {
        return eta;
    }

    /**
     * Returns the log-likelihood of model.
     * @return the log-likelihood of model.
     */
    public double loglikelihood() {
        return L;
    }

    /**
     * Returns the AIC score.
     * @return the AIC score.
     */
    public double AIC() {
        return ModelSelection.AIC(L, (k-1)*(p+1));
    }
}
