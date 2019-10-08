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
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;

/**
 * Linear discriminant analysis. LDA is based on the Bayes decision theory
 * and assumes that the conditional probability density functions are normally
 * distributed. LDA also makes the simplifying homoscedastic assumption (i.e.
 * that the class covariances are identical) and that the covariances have full
 * rank. With these assumptions, the discriminant function of an input being
 * in a class is purely a function of this linear combination of independent
 * variables.
 * <p>
 * LDA is closely related to ANOVA (analysis of variance) and linear regression
 * analysis, which also attempt to express one dependent variable as a
 * linear combination of other features or measurements. In the other two
 * methods, however, the dependent variable is a numerical quantity, while
 * for LDA it is a categorical variable (i.e. the class label). Logistic
 * regression and probit regression are more similar to LDA, as they also
 * explain a categorical variable. These other methods are preferable in
 * applications where it is not reasonable to assume that the independent 
 * variables are normally distributed, which is a fundamental assumption
 * of the LDA method.
 * <p>
 * One complication in applying LDA (and Fisher's discriminant) to real data
 * occurs when the number of variables/features does not exceed
 * the number of samples. In this case, the covariance estimates do not have
 * full rank, and so cannot be inverted. This is known as small sample size
 * problem.
 * 
 * @see FLD
 * @see QDA
 * @see RDA
 * @see NaiveBayes
 * 
 * @author Haifeng Li
 */
public class LDA implements SoftClassifier<double[]> {
    private static final long serialVersionUID = 1L;

    /**
     * The dimensionality of data.
     */
    private final int p;
    /**
     * The number of classes.
     */
    private final int k;
    /**
     * Constant term of discriminant function of each class.
     */
    private final double[] ct;
    /**
     * A priori probabilities of each class.
     */
    private final double[] priori;
    /**
     * Mean vectors of each class.
     */
    private final double[][] mu;
    /**
     * Eigen vectors of common covariance matrix, which transforms observations
     * to discriminant functions, normalized so that common covariance
     * matrix is spherical.
     */
    private final DenseMatrix scaling;
    /**
     * Eigen values of common variance matrix.
     */
    private final double[] eigen;

    /**
     * Constructor.
     */
    public LDA(int k , int p, double[] priori, double[][] mu, double[] ct, double[] eigen, DenseMatrix scaling) {
        this.k = k;
        this.p = p;
        this.priori = priori;
        this.mu = mu;
        this.ct = ct;
        this.eigen = eigen;
        this.scaling = scaling;
    }

    /**
     * Learns linear discriminant analysis.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    /*
    public static LDA fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }
*/
    /**
     * Learns linear discriminant analysis.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    /*
    public static LDA fit(Formula formula, DataFrame data, Properties prop) {
        double tol = Double.valueOf(prop.getProperty("smile.lda.tolerance", "1E-4"));
        return fit(formula, data, null, tol);
    }
     */

    /**
     * Learns linear discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static LDA fit(double[][] x, int[] y) {
        return fit(x, y, null, 1E-4);
    }

    /**
     * Learns linear discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param priori the priori probability of each class. If null, it will be
     * estimated from the training data.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
     */
    public static LDA fit(double[][] x, int[] y, double[] priori, double tol) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
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
        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);
        
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] < 0) {
                throw new IllegalArgumentException("Negative class label: " + labels[i]); 
            }
            
            if (i > 0 && labels[i] - labels[i-1] > 1) {
                throw new IllegalArgumentException("Missing class: " + (labels[i-1]+1));
            }
        }

        int k = labels.length;
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

        int p = x[0].length;
        // The number of instances in each class.
        int[] ni = new int[k];
        // Common mean vector.
        double[] mean = MathEx.colMeans(x);
        // Common covariance.
        DenseMatrix C = Matrix.zeros(p, p);
        // Class mean vectors.
        double[][] mu = new double[k][p];

        for (int i = 0; i < n; i++) {
            int c = y[i];
            ni[c]++;
            for (int j = 0; j < p; j++) {
                mu[c][j] += x[i][j];
            }
        }

        for (int i = 0; i < k; i++) {
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
        
        double[] ct = new double[k];
        for (int i = 0; i < k; i++) {
            ct[i] = Math.log(priori[i]);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                for (int l = 0; l <= j; l++) {
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

        C.setSymmetric(true);
        EVD evd = C.eigen();

        for (double s : evd.getEigenValues()) {
            if (s < tol) {
                throw new IllegalArgumentException("The covariance matrix is close to singular.");
            }
        }

        return new LDA(k, p, priori, mu, ct, evd.getEigenValues(), evd.getEigenVectors());
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] priori() {
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

            scaling.atx(d, ux);

            double f = 0.0;
            for (int j = 0; j < p; j++) {
                f += ux[j] * ux[j] / eigen[j];
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
