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

import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.EVD;
import smile.util.IntSet;
import smile.util.Strings;

/**
 * Quadratic discriminant analysis. QDA is closely related to linear discriminant
 * analysis (LDA). Like LDA, QDA models the conditional probability density
 * functions as a Gaussian distribution, then uses the posterior distributions
 * to estimate the class for a given test data. Unlike LDA, however,
 * in QDA there is no assumption that the covariance of each of the classes
 * is identical. Therefore, the resulting separating surface between
 * the classes is quadratic.
 * <p>
 * The Gaussian parameters for each class can be estimated from training data
 * with maximum likelihood (ML) estimation. However, when the number of
 * training instances is small compared to the dimension of input space,
 * the ML covariance estimation can be ill-posed. One approach to resolve
 * the ill-posed estimation is to regularize the covariance estimation.
 * One of these regularization methods is {@link RDA regularized discriminant analysis}.
 * 
 * @see LDA
 * @see RDA
 * @see NaiveBayes
 * 
 * @author Haifeng Li
 */
public class QDA implements SoftClassifier<double[]> {
    private static final long serialVersionUID = 2L;

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
    private final double[] logppriori;
    /**
     * A priori probabilities of each class.
     */
    private final double[] priori;
    /**
     * Mean vectors of each class.
     */
    private final double[][] mu;
    /**
     * Eigen values of each covariance matrix.
     */
    private final double[][] eigen;
    /**
     * Eigen vectors of each covariance matrix, which transforms observations
     * to discriminant functions, normalized so that within groups covariance
     * matrix is spherical.
     */
    private final DenseMatrix[] scaling;
    /**
     * The class label encoder.
     */
    private final IntSet labels;

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of each variance matrix.
     * @param scaling the eigen vectors of each covariance matrix.
     */
    public QDA(double[] priori, double[][] mu, double[][] eigen, DenseMatrix[] scaling) {
        this(priori, mu, eigen, scaling, IntSet.of(priori.length));
    }

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of each variance matrix.
     * @param scaling the eigen vectors of each covariance matrix.
     * @param labels class labels
     */
    public QDA(double[] priori, double[][] mu, double[][] eigen, DenseMatrix[] scaling, IntSet labels) {
        this.k = priori.length;
        this.p = mu[0].length;
        this.priori = priori;
        this.mu = mu;
        this.eigen = eigen;
        this.scaling = scaling;
        this.labels = labels;

        logppriori = new double[k];
        for (int i = 0; i < k; i++) {
            double logev = 0.0;
            for (int j = 0; j < p; j++) {
                logev += Math.log(eigen[i][j]);
            }

            logppriori[i] = Math.log(priori[i]) - 0.5 * logev;
        }
    }

    /**
     * Learns quadratic discriminant analysis.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static QDA fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Learns quadratic discriminant analysis.
     *
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     */
    public static QDA fit(Formula formula, DataFrame data, Properties prop) {
        double[][] x = formula.x(data).toArray();
        int[] y = formula.y(data).toIntArray();
        return fit(x, y, prop);
    }

    /**
     * Learn quadratic discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     */
    public static QDA fit(double[][] x, int[] y) {
        return fit(x, y, null, 1E-4);
    }

    /**
     * Learns quadratic discriminant analysis.
     * @param x training samples.
     * @param y training labels.
     */
    public static QDA fit(double[][] x, int[] y, Properties prop) {
        double[] priori = Strings.parseDoubleArray(prop.getProperty("smile.qda.priori"));
        double tol = Double.valueOf(prop.getProperty("smile.qda.tolerance", "1E-4"));
        return fit(x, y, priori, tol);
    }

    /**
     * Learn quadratic discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @param priori the priori probability of each class. If null, it will be
     * estimated from the training data.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
     */
    public static QDA fit(double[][] x, int[] y, double[] priori, double tol) {
        DiscriminantAnalysis da = DiscriminantAnalysis.fit(x, y, priori, tol);

        DenseMatrix[] cov = DiscriminantAnalysis.cov(x, y, da.mu, da.ni);

        int k = cov.length;
        int p = cov[0].nrows();
        double[][] eigen = new double[k][];
        DenseMatrix[] scaling = new DenseMatrix[k];

        tol = tol * tol;
        for (int i = 0; i < k; i++) {
            // quick test of singularity
            for (int j = 0; j < p; j++) {
                if (cov[i].get(j, j) < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix (column %d) is close to singular.", i, j));
                }
            }

            EVD evd = cov[i].eigen();

            for (double s : evd.getEigenValues()) {
                if (s < tol) {
                    throw new IllegalArgumentException(String.format("Class %d covariance matrix is close to singular.", i));
                }
            }

            eigen[i] = evd.getEigenValues();
            scaling[i] = evd.getEigenVectors();
        }

        return new QDA(da.priori, da.mu, eigen, scaling, da.labels);
    }

    /**
     * Returns a priori probabilities.
     */
    public double[] priori() {
        return priori;
    }

    @Override
    public int predict(double[] x) {
        return predict(x, new double[k]);
    }

    @Override
    public int predict(double[] x, double[] posteriori) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double[] d = new double[p];
        double[] ux = new double[p];

        for (int i = 0; i < k; i++) {
            double[] mui = mu[i];
            for (int j = 0; j < p; j++) {
                d[j] = x[j] - mui[j];
            }

            scaling[i].atx(d, ux);

            double f = 0.0;
            double[] ev = eigen[i];
            for (int j = 0; j < p; j++) {
                f += ux[j] * ux[j] / ev[j];
            }

            posteriori[i] = logppriori[i] - 0.5 * f;
        }

        return labels.valueOf(MathEx.softmax(posteriori));
    }
}
