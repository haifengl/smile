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
public class Maxent implements SoftClassifier<int[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(Maxent.class);

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
     * Trainer for maximum entropy classifier.
     */
    public static class Trainer extends ClassifierTrainer<int[]> {

        /**
         * The dimension of feature space.
         */
        private int p;
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
         * @param p the dimension of feature space.
         */
        public Trainer(int p) {
            if (p < 0) {
                throw new IllegalArgumentException("Invalid dimension: " + p);
            }

            this.p = p;
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
         * @param tol tolerance for stopping iterations.
         */
        public Trainer setTolerance(double tol) {
            this.tol = tol;
            return this;
        }
        
        /**
         * Sets the maximum number of BFGS stopping iterations.
         * 
         * @param maxIter maximum number of iterations.
         */
        public Trainer setMaxNumIteration(int maxIter) {
            this.maxIter = maxIter;
            return this;
        }
        
        @Override
        public Maxent train(int[][] x, int[] y) {
            return new Maxent(p, x, y, lambda, tol, maxIter);
        }
    }
    
    /**
     * Learn maximum entropy classifier from samples of binary sparse features.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public Maxent(int p, int[][] x, int[] y) {
        this(p, x, y, 0.1);
    }

    /**
     * Learn maximum entropy classifier from samples of binary sparse features.
     * @param p the dimension of feature space.
     * @param x training samples. Each sample is represented by a set of sparse
     * binary features. The features are stored in an integer array, of which
     * are the indices of nonzero features.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
     * weights which often has superior generalization performance, especially
     * when the dimensionality is high.
     */
    public Maxent(int p, int[][] x, int[] y, double lambda) {
        this(p, x, y, lambda, 1E-5, 500);
    }

    /**
     * Learn maximum entropy classifier from samples of binary sparse features.
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
    public Maxent(int p, int[][] x, int[] y, double lambda, double tol, int maxIter) {
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
        
        this.p = p;
        
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

        if (k == 2) {
            BinaryObjectiveFunction func = new BinaryObjectiveFunction(x, y, lambda);

            w = new double[p + 1];

            L = 0.0;
            try {
                L = -Math.min(func, 5, w, tol, maxIter);
            } catch (Exception ex) {
                logger.error("Failed to minimize binary objective function of Maximum Entropy Classifier", ex);
            }
        } else {
            MultiClassObjectiveFunction func = new MultiClassObjectiveFunction(x, y, k, p, lambda);

            w = new double[k * (p + 1)];

            L = 0.0;
            try {
                L = -Math.min(func, 5, w, tol, maxIter);
            } catch (Exception ex) {
                logger.error("Failed to minimize multi-class objective function of Maximum Entropy Classifier", ex);
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
     * Returns the dimension of input space.
     * @return the dimension of input space.
     */
    public int getDimension() {
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

            FTask(double[] w, int start, int end) {
                this.w = w;
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
            double f = 0.0;
            int p = w.length - 1;

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n < 1000 || m < 2) {
                for (int i = 0; i < n; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;
                }
            } else {
                List<FTask> tasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) step = 100;
                
                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    tasks.add(new FTask(w, start, end));
                    start += step;
                    end += step;
                }
                tasks.add(new FTask(w, start, n));
                
                try {
                    for (double fi : MulticoreExecutor.run(tasks)) {
                        f += fi;
                    }
                } catch (Exception ex) {
                    for (int i = 0; i < n; i++) {
                        double wx = dot(x[i], w);
                        f += log1pe(wx) - y[i] * wx;
                    }
                }
            }

            if (lambda != 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) {
                    wnorm += w[i] * w[i];
                }

                f += 0.5 * lambda * wnorm;
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

            GTask(double[] w, int start, int end) {
                this.w = w;
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
                    for (int j : x[i]) {
                        g[j] -= yi * j;
                    }
                    g[p] -= yi;
                }
                
                g[w.length] = f;
                return g;
            }
        }
        
        @Override
        public double f(double[] w, double[] g) {
            double f = 0.0;
            int p = w.length - 1;
            Arrays.fill(g, 0.0);

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n < 1000 || m < 2) {
                for (int i = 0; i < n; i++) {
                    double wx = dot(x[i], w);
                    f += log1pe(wx) - y[i] * wx;

                    double yi = y[i] - Math.logistic(wx);
                    for (int j : x[i]) {
                        g[j] -= yi * j;
                    }
                    g[p] -= yi;
                }
            } else {
                List<GTask> tasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) step = 100;
                
                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    tasks.add(new GTask(w, start, end));
                    start += step;
                    end += step;
                }
                tasks.add(new GTask(w, start, n));

                try {
                    for (double[] gi : MulticoreExecutor.run(tasks)) {
                        f += gi[w.length];
                        for (int i = 0; i < w.length; i++) {
                            g[i] += gi[i];
                        }
                    }
                } catch (Exception ex) {
                    for (int i = 0; i < n; i++) {
                        double wx = dot(x[i], w);
                        f += log1pe(wx) - y[i] * wx;

                        double yi = y[i] - Math.logistic(wx);
                        for (int j : x[i]) {
                            g[j] -= yi * j;
                        }
                        g[p] -= yi;
                    }
                }
            }

            if (lambda != 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < p; i++) {
                    wnorm += w[i] * w[i];
                }

                f += 0.5 * lambda * wnorm;

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

            FTask(double[] w, int start, int end) {
                this.w = w;
                this.start = start;
                this.end = end;
            }

            @Override
            public Double call() {
                double f = 0.0;
                double[] prob = new double[k];

                for (int i = start; i < end; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j, p);
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);
                }
                return f;
            }
        }
        
        @Override
        public double f(double[] w) {
            double f = 0.0;
            double[] prob = new double[k];

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n < 1000 || m < 2) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j, p);
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);
                }
            } else {
                List<FTask> tasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) step = 100;

                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    tasks.add(new FTask(w, start, end));
                    start += step;
                    end += step;
                }
                tasks.add(new FTask(w, start, n));
                
                try {
                    for (double fi : MulticoreExecutor.run(tasks)) {
                        f += fi;
                    }
                } catch (Exception ex) {
                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < k; j++) {
                            prob[j] = dot(x[i], w, j, p);
                        }

                        softmax(prob);

                        f -= log(prob[y[i]]);
                    }
                }
            }

            if (lambda != 0.0) {
                double wnorm = 0.0;
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < p; j++) {
                        wnorm += Math.sqr(w[i*(p+1) + j]);
                    }
                }

                f += 0.5 * lambda * wnorm;
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

            GTask(double[] w, int start, int end) {
                this.w = w;
                this.start = start;
                this.end = end;
            }

            @Override
            public double[] call() {
                double f = 0.0;
                double[] prob = new double[k];
                double[] g = new double[w.length+1];

                for (int i = start; i < end; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j, p);
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);

                    double yi = 0.0;
                    for (int j = 0; j < k; j++) {
                        yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                        int pos = j * (p + 1);
                        for (int l : x[i]) {
                            g[pos + l] -= yi;
                        }
                        g[pos + p] -= yi;
                    }
                }
                
                g[w.length] = f;
                return g;
            }
        }
        
        @Override
        public double f(double[] w, double[] g) {
            double f = 0.0;
            double[] prob = new double[k];
            Arrays.fill(g, 0.0);

            int n = x.length;
            int m = MulticoreExecutor.getThreadPoolSize();
            if (n < 1000 || m < 2) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < k; j++) {
                        prob[j] = dot(x[i], w, j, p);
                    }

                    softmax(prob);

                    f -= log(prob[y[i]]);

                    double yi = 0.0;
                    for (int j = 0; j < k; j++) {
                        yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                        int pos = j * (p + 1);
                        for (int l : x[i]) {
                            g[pos + l] -= yi;
                        }
                        g[pos + p] -= yi;
                    }
                }
            } else {
                List<GTask> tasks = new ArrayList<>(m + 1);
                int step = n / m;
                if (step < 100) {
                    step = 100;
                }
                
                int start = 0;
                int end = step;
                for (int i = 0; i < m - 1; i++) {
                    tasks.add(new GTask(w, start, end));
                    start += step;
                    end += step;
                }
                tasks.add(new GTask(w, start, n));

                try {
                    for (double[] gi : MulticoreExecutor.run(tasks)) {
                        f += gi[w.length];
                        for (int i = 0; i < w.length; i++) {
                            g[i] += gi[i];
                        }
                    }
                } catch (Exception ex) {
                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < k; j++) {
                            prob[j] = dot(x[i], w, j, p);
                        }

                        softmax(prob);

                        f -= log(prob[y[i]]);

                        double yi = 0.0;
                        for (int j = 0; j < k; j++) {
                            yi = (y[i] == j ? 1.0 : 0.0) - prob[j];

                            int pos = j * (p + 1);
                            for (int l : x[i]) {
                                g[pos + l] -= yi;
                            }
                            g[pos + p] -= yi;
                        }
                    }
                }
            }


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
