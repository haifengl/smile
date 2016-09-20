/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.classification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.DifferentiableMultivariateFunction;
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
public class LogisticRegression implements SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(LogisticRegression.class);

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
     * Trainer for logistic regression.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {

        /**
         * Regularization factor. &lambda; > 0 gives a "regularized" estimate
         * of linear weights which often has superior generalization
         * performance, especially when the dimensionality is high.
         */
        private double lambda = 0.0;
        /**
         * The tolerance for BFGS stopping iterations.
         */
        private double tol = 1E-5;
        /**
         * The maximum number of BFGS iterations.
         */
        private int maxIter = 500;

        /**
         * Constructor.
         */
        public Trainer() {

        }
        
        /**
         * Sets the regularization factor. &lambda; &gt; 0 gives a "regularized"
         * estimate of linear weights which often has superior generalization
         * performance, especially when the dimensionality is high.
         * 
         * @param lambda regularization factor.
         */
        public Trainer setRegularizationFactor(double lambda) {
            this.lambda = lambda;
            return this;
        }
        
        /**
         * Sets the tolerance for BFGS stopping iterations.
         * 
         * @param tol the tolerance for stopping iterations.
         */
        public Trainer setTolerance(double tol) {
            if (tol <= 0.0) {
                throw new IllegalArgumentException("Invalid tolerance: " + tol);
            }

            this.tol = tol;
            return this;
        }

        /**
         * Sets the maximum number of iterations.
         * 
         * @param maxIter the maximum number of iterations.
         */
        public Trainer setMaxNumIteration(int maxIter) {
            if (maxIter <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of iterations: " + maxIter);
            }

            this.maxIter = maxIter;
            return this;
        }
        
        @Override
        public LogisticRegression train(double[][] x, int[] y) {
            return new LogisticRegression(x, y, lambda, tol, maxIter);
        }
    }
    
    /**
     * Constructor. No regularization.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public LogisticRegression(double[][] x, int[] y) {
        this(x, y, 0.0);
    }

    /**
     * Constructor.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     */
    public LogisticRegression(double[][] x, int[] y, double lambda) {
        this(x, y, lambda, 1E-5, 500);
    }

    /**
     * Constructor.
     * 
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     * @param tol the tolerance for stopping iterations.
     * @param maxIter the maximum number of iterations.
     */
    public LogisticRegression(double[][] x, int[] y, double lambda, double tol, int maxIter) {
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
        
        // class label set.
        int[] labels = Math.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + labels[i]+1);                 
            }
        }

        k = labels.length;
        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");            
        }

        p = x[0].length;
        if (k == 2) {
            BinaryObjectiveFunction func = new BinaryObjectiveFunction(x, y, lambda);

            w = new double[p + 1];

            L = 0.0;
            try {
                L = -Math.min(func, 5, w, tol, maxIter);
            } catch (Exception ex) {
                // If L-BFGS doesn't work, let's try BFGS.
                L = -Math.min(func, w, tol, maxIter);
            }
        } else {
            MultiClassObjectiveFunction func = new MultiClassObjectiveFunction(x, y, k, lambda);

            w = new double[k * (p + 1)];

            L = 0.0;
            try {
                L = -Math.min(func, 5, w, tol, maxIter);
            } catch (Exception ex) {
                // If L-BFGS doesn't work, let's try BFGS.
                L = -Math.min(func, w, tol, maxIter);
            }

            W = new double[k][p+1];
            for (int i = 0, m = 0; i < k; i++) {
                for (int j = 0; j <= p; j++, m++) {
                    W[i][j] = w[m];
                }
            }
            w = null;
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
         * Parallel computing of objective function.
         */
        List<FTask> ftasks = null;
        /**
         * Parallel computing of objective function and gradient.
         */
        List<GTask> gtasks = null;
        
        /**
         * Constructor.
         */
        BinaryObjectiveFunction(double[][] x, int[] y, double lambda) {
            this.x = x;
            this.y = y;
            this.lambda = lambda;

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n >= 1000 && m >= 2) {
                ftasks = new ArrayList<>(m + 1);
                gtasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) {
                    step = 100;
                }

                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    ftasks.add(new FTask(start, end));
                    gtasks.add(new GTask(start, end));
                    start += step;
                    end += step;
                }
                ftasks.add(new FTask(start, n));
                gtasks.add(new GTask(start, n));
            }
        }

        /**
         * Task to calculate the objective function.
         */
        class FTask implements Callable<Double> {

            /**
             * The parameter vector.
             */
            double[] w;
            /**
             * The start index of data portion for this task.
             */
            int start;
            /**
             * The end index of data portion for this task.
             */
            int end;

            FTask(int start, int end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public Double call() {
                double f = 0.0;
                for (int i = start; i < end; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;
                }
                return f;
            }
        }
        
        @Override
        public double f(double[] w) {
            double f = Double.NaN;
            int p = w.length - 1;

            if (ftasks != null) {
                for (FTask task : ftasks) {
                    task.w = w;
                }
                
                try {
                    f = 0.0;
                    for (double fi : MulticoreExecutor.run(ftasks)) {
                        f += fi;
                    }
                } catch (Exception ex) {
                    logger.error("Failed to train Logistic Regression on multi-core", ex);
                    f = Double.NaN;
                }
            }

            if (Double.isNaN(f)) {
                f = 0.0;
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;
                }
            }
            
            if (lambda != 0.0) {
                double w2 = 0.0;
                for (int i = 0; i < p; i++) {
                    w2 += w[i] * w[i];
                }

                f += 0.5 * lambda * w2;
            }

            return f;
        }

        /**
         * Task to calculate the objective function and gradient.
         */
        class GTask implements Callable<double[]> {

            /**
             * The parameter vector.
             */
            double[] w;
            /**
             * The start index of data portion for this task.
             */
            int start;
            /**
             * The end index of data portion for this task.
             */
            int end;

            GTask(int start, int end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public double[] call() {
                double f = 0.0;
                int p = w.length - 1;
                double[] g = new double[w.length + 1];
                
                for (int i = start; i < end; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;
                    
                    double yi = y[i] - Math.logistic(wx);
                    for (int j = 0; j < p; j++) {
                        g[j] -= yi * x[i][j];
                    }
                    g[p] -= yi;
                }
                
                g[w.length] = f;
                return g;
            }
        }
        
        @Override
        public double f(double[] w, double[] g) {
            double f = Double.NaN;
            int p = w.length - 1;
            Arrays.fill(g, 0.0);

            if (gtasks != null) {
                for (GTask task : gtasks) {
                    task.w = w;
                }
                
                try {
                    f = 0.0;
                    for (double[] gi : MulticoreExecutor.run(gtasks)) {
                        f += gi[w.length];
                        for (int i = 0; i < w.length; i++) {
                            g[i] += gi[i];
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Failed to train Logistic Regression on multi-core", ex);
                    f = Double.NaN;
                }
            }

            if (Double.isNaN(f)) {
                f = 0.0;
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;

                    double yi = y[i] - Math.logistic(wx);
                    for (int j = 0; j < p; j++) {
                        g[j] -= yi * x[i][j];
                    }
                    g[p] -= yi;
                }
            }

            if (lambda != 0.0) {
                double w2 = 0.0;
                for (int i = 0; i < p; i++) {
                    w2 += w[i] * w[i];
                }

                f += 0.5 * lambda * w2;

                for (int j = 0; j < p; j++) {
                    g[j] += lambda * w[j];
                }
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
         * Parallel computing of objective function.
         */
        List<FTask> ftasks = null;
        /**
         * Parallel computing of objective function and gradient.
         */
        List<GTask> gtasks = null;

        /**
         * Constructor.
         */
        MultiClassObjectiveFunction(double[][] x, int[] y, int k, double lambda) {
            this.x = x;
            this.y = y;
            this.k = k;
            this.lambda = lambda;

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n >= 1000 && m >= 2) {
                ftasks = new ArrayList<>(m + 1);
                gtasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) {
                    step = 100;
                }

                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    ftasks.add(new FTask(start, end));
                    gtasks.add(new GTask(start, end));
                    start += step;
                    end += step;
                }
                ftasks.add(new FTask(start, n));
                gtasks.add(new GTask(start, n));
            }
        }

        /**
         * Task to calculate the objective function.
         */
        class FTask implements Callable<Double> {

            /**
             * The parameter vector.
             */
            double[] w;
            /**
             * The start index of data portion for this task.
             */
            int start;
            /**
             * The end index of data portion for this task.
             */
            int end;

            FTask(int start, int end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public Double call() {
                double f = 0.0;

                int p = x[0].length;
                double[] prob = new double[k];

                for (int i = start; i < end; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j * (p + 1));
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);
                }
                return f;
            }
        }
        
        @Override
        public double f(double[] w) {
            double f = Double.NaN;
            int p = x[0].length;
            double[] prob = new double[k];

            if (ftasks != null) {
                for (FTask task : ftasks) {
                    task.w = w;
                }
                
                try {
                    f = 0.0;
                    for (double fi : MulticoreExecutor.run(ftasks)) {
                        f += fi;
                    }
                } catch (Exception ex) {
                    logger.error("Failed to train Logistic Regression on multi-core", ex);
                    f = Double.NaN;
                }
            }

            if (Double.isNaN(f)) {
                f = 0.0;
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j * (p + 1));
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);
                }
            }

            if (lambda != 0.0) {
                double w2 = 0.0;
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < p; j++) {
                        w2 += Math.sqr(w[i*(p+1) + j]);
                    }
                }

                f += 0.5 * lambda * w2;
            }

            return f;
        }

        /**
         * Task to calculate the objective function and gradient.
         */
        class GTask implements Callable<double[]> {

            /**
             * The parameter vector.
             */
            double[] w;
            /**
             * The start index of data portion for this task.
             */
            int start;
            /**
             * The end index of data portion for this task.
             */
            int end;

            GTask(int start, int end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public double[] call() {
                double f = 0.0;
                double[] g = new double[w.length+1];

                int p = x[0].length;
                double[] prob = new double[k];

                for (int i = start; i < end; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j * (p + 1));
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);

                    double yi = 0.0;
                    for (int j = 0; j < k; j++) {
                        yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                        for (int l = 0, pos = j * (p + 1); l < p; l++) {
                            g[pos + l] -= yi * x[i][l];
                        }
                        g[j * (p + 1) + p] -= yi;
                    }
                }
                
                g[w.length] = f;
                return g;
            }
        }
        
        @Override
        public double f(double[] w, double[] g) {
            double f = Double.NaN;
            int p = x[0].length;
            double[] prob = new double[k];            
            Arrays.fill(g, 0.0);

            if (gtasks != null) {
                for (GTask task : gtasks) {
                    task.w = w;
                }
                
                try {
                    f = 0.0;
                    for (double[] gi : MulticoreExecutor.run(gtasks)) {
                        f += gi[w.length];
                        for (int i = 0; i < w.length; i++) {
                            g[i] += gi[i];
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Failed to train Logistic Regression on multi-core", ex);
                    f = Double.NaN;
                }
            }

            if (Double.isNaN(f)) {
                f = 0.0;
                int n = x.length;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j * (p + 1));
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);

                    double yi = 0.0;
                    for (int j = 0; j < k; j++) {
                        yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                        for (int l = 0, pos = j * (p + 1); l < p; l++) {
                            g[pos + l] -= yi * x[i][l];
                        }
                        g[j * (p + 1) + p] -= yi;
                    }
                }
            }

            if (lambda != 0.0) {
                double w2 = 0.0;
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < p; j++) {
                        int pos = i * (p+1) + j;
                        w2 += w[pos] * w[pos];
                        g[pos] += lambda * w[pos];
                    }
                }

                f += 0.5 * lambda * w2;
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
    private static double dot(double[] x, double[] w) {
        int i = 0;
        double dot = 0.0;

        for (; i < x.length; i++) {
            dot += x[i] * w[i];
        }

        return dot + w[i];
    }

    /**
     * Returns the dot product between weight vector and x (augmented with 1).
     */
    private static double dot(double[] x, double[] w, int pos) {
        int i = 0;
        double dot = 0.0;
        
        for (; i < x.length; i++) {
            dot += x[i] * w[pos+i];
        }

        return dot + w[pos+i];
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
