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
import java.util.Arrays;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

/**
 * Regularized discriminant analysis. RDA is a compromise between LDA and QDA,
 * which allows one to shrink the separate covariances of QDA toward a common
 * variance as in LDA. This method is very similar in flavor to ridge regression.
 * The regularized covariance matrices of each class is
 * &Sigma;<sub>k</sub>(&alpha;) = &alpha; &Sigma;<sub>k</sub> + (1 - &alpha;) &Sigma;.
 * The quadratic discriminant function is defined using the shrunken covariance
 * matrices &Sigma;<sub>k</sub>(&alpha;). The parameter &alpha; in [0, 1]
 * controls the complexity of the model. When &alpha; is one, RDA becomes QDA.
 * While &alpha; is zero, RDA is equivalent to LDA. Therefore, the
 * regularization factor &alpha; allows a continuum of models between LDA and QDA.
 * 
 * @see LDA
 * @see QDA
 * 
 * @author Haifeng Li
 */
public class RDA implements SoftClassifier<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The dimensionality of data.
     */
    private int p;
    /**
     * The number of classes.
     */
    private int k;
    /**
     * Constant term of discriminant function of each class.
     */
    private final double[] ct;
    /**
     * A priori probabilities of each class.
     */
    private double[] priori;
    /**
     * Mean vectors of each class.
     */
    private double[][] mu;
    /**
     * Eigen vectors of each covariance matrix, which transforms observations
     * to discriminant functions, normalized so that within groups covariance
     * matrix is spherical.
     */
    private DenseMatrix[] scaling;
    /**
     * Eigen values of each covariance matrix.
     */
    private double[][] ev;

    /**
     * Trainer for regularized discriminant analysis.
     */
    public static class Trainer extends ClassifierTrainer<double[]> {
        /**
         * Regularization factor in [0, 1] allows a continuum of models
         * between LDA and QDA.
         */
        private double alpha;
        /**
         * A priori probabilities of each class.
         */
        private double[] priori;
        /**
         * A tolerance to decide if a covariance matrix is singular. The trainer
         * will reject variables whose variance is less than tol<sup>2</sup>.
         */
        private double tol = 1E-4;

        /**
         * Constructor. The default tolerance to covariance matrix singularity
         * is 1E-4.
         * 
         * @param alpha regularization factor in [0, 1] allows a continuum of
         * models between LDA and QDA.
         */
        public Trainer(double alpha) {
            if (alpha < 0.0 || alpha > 1.0) {
                throw new IllegalArgumentException("Invalid regularization factor: " + alpha);
            }

            this.alpha = alpha;
        }
        
        /**
         * Sets a priori probabilities of each class.
         * @param priori a priori probabilities of each class.
         */
        public Trainer setPriori(double[] priori) {
            this.priori = priori;
            return this;
        }
        
        /**
         * Sets covariance matrix singular tolerance.
         * 
         * @param tol a tolerance to decide if a covariance matrix is singular.
         * The trainer will reject variables whose variance is less than tol<sup>2</sup>.
         */
        public Trainer setTolerance(double tol) {
            if (tol < 0.0) {
                throw new IllegalArgumentException("Invalid tol: " + tol);
            }

            this.tol = tol;
            return this;
        }
        
        @Override
        public RDA train(double[][] x, int[] y) {
            return new RDA(x, y, priori, alpha, tol);
        }
    }
    
    /**
     * Constructor. Learn regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param alpha regularization factor in [0, 1] allows a continuum of models
     * between LDA and QDA.
     */
    public RDA(double[][] x, int[] y, double alpha) {
        this(x, y, null, alpha);
    }

    /**
     * Constructor. Learn regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param alpha regularization factor in [0, 1] allows a continuum of models
     * between LDA and QDA.
     * @param priori the priori probability of each class.
     */
    public RDA(double[][] x, int[] y, double[] priori, double alpha) {
        this(x, y, priori, alpha, 1E-4);
    }

    /**
     * Constructor. Learn regularized discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param alpha regularization factor in [0, 1] allows a continuum of models
     * between LDA and QDA.
     * @param priori the priori probability of each class.
     * @param tol tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
     */
    public RDA(double[][] x, int[] y, double[] priori, double alpha, double tol) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Invalid regularization factor: " + alpha);
        }

        if (priori != null) {
            if (priori.length < 2) {
                throw new IllegalArgumentException("Invalid number of priori probabilities: " + priori.length);
            }

            double sum = 0.0;
            for (double pr : priori) {
                if (pr <= 0.0 || pr >= 1.0) {
                    throw new IllegalArgumentException("Invalid priori probability: " + pr);
                }
                sum += pr;
            }

            if (Math.abs(sum - 1.0) > 1E-10) {
                throw new IllegalArgumentException("The sum of priori probabilities is not one: " + sum);
            }
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
        
        if (priori != null && k != priori.length) {
            throw new IllegalArgumentException("The number of classes and the number of priori probabilities don't match.");                        
        }
        
        if (tol < 0.0) {
            throw new IllegalArgumentException("Invalid tol: " + tol);
        }
        
        final int n = x.length;

        if (n <= k) {
            throw new IllegalArgumentException(String.format("Sample size is too small: %d <= %d", n, k));
        }

        p = x[0].length;

        // The number of instances in each class.
        int[] ni = new int[k];
        // Common mean vector.
        double[] mean = Math.colMeans(x);
        // Common covariance.
        DenseMatrix C = Matrix.zeros(p, p);
        // Class mean vectors.
        mu = new double[k][p];
        // Class covarainces.
        DenseMatrix[] cov = new DenseMatrix[k];

        for (int i = 0; i < n; i++) {
            int c = y[i];
            ni[c]++;
            for (int j = 0; j < p; j++) {
                mu[c][j] += x[i][j];
            }
        }

        for (int i = 0; i < k; i++) {
            if (ni[i] <= 1) {
                throw new IllegalArgumentException(String.format("Class %d has only one sample.", i));
            }

            cov[i] = Matrix.zeros(p, p);

            for (int j = 0; j < p; j++) {
                mu[i][j] /= ni[i];
            }
        }

        if (priori == null) {
            priori = new double[k];
            for (int i = 0; i < k; i++) {
                priori[i] = (double) ni[i] / n;
            }
        }
        this.priori = priori;

        for (int i = 0; i < n; i++) {
            int c = y[i];
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
                    cov[c].add(j, l, (x[i][j] - mu[c][j]) * (x[i][l] - mu[c][l]));
                    C.add(j, l, (x[i][j] - mean[j]) * (x[i][l] - mean[l]));
                }
            }
        }

        tol = tol * tol;
        for (int j = 0; j < p; j++) {
            for (int l = 0; l <= j; l++) {
                C.div(j, l, (n - k));
                C.set(l, j, C.get(j, l));
            }

            if (C.get(j, j) < tol) {
                throw new IllegalArgumentException(String.format("Covariance matrix (variable %d) is close to singular.", j));
            }
        }

        ev = new double[k][];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
                    cov[i].div(j, l, (ni[i] - 1));
                    cov[i].set(j, l, alpha * cov[i].get(j, l) + (1 - alpha) * C.get(j, l));
                    cov[i].set(l, j, cov[i].get(j, l));
                }

                if (cov[i].get(j, j) < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix (variable %d) is close to singular.", i, j));
                }
            }

            cov[i].setSymmetric(true);
            EVD eigen = cov[i].eigen();

            for (double s : eigen.getEigenValues()) {
                if (s < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix is close to singular.", i));
                }
            }

            ev[i] = eigen.getEigenValues();
            cov[i] = eigen.getEigenVectors();
        }

        scaling = cov;
        ct = new double[k];
        for (int i = 0; i < k; i++) {
            double logev = 0.0;
            for (int j = 0; j < p; j++) {
                logev += Math.log(ev[i][j]);
            }

            ct[i] = Math.log(priori[i]) - 0.5 * logev;
        }
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] getPriori() {
        return priori;
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

        int y = 0;
        double max = Double.NEGATIVE_INFINITY;

        double[] d = new double[p];
        double[] ux = new double[p];

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < p; j++) {
                d[j] = x[j] - mu[i][j];
            }

            scaling[i].atx(d, ux);

            double f = 0.0;
            for (int j = 0; j < p; j++) {
                f += ux[j] * ux[j] / ev[i][j];
            }

            f = ct[i] - 0.5 * f;
            if (max < f) {
                max = f;
                y = i;
            }

            if (posteriori != null) {
                posteriori[i] = f;
            }
        }

        if (posteriori != null) {
            double sum = 0.0;
            for (int i = 0; i < k; i++) {
                posteriori[i] = Math.exp(posteriori[i] - max);
                sum += posteriori[i];
            }
            
            for (int i = 0; i < k; i++) {
                posteriori[i] /= sum;
            }
        }

        return y;
    }
}
