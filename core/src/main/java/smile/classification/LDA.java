/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.io.Serial;
import java.util.Properties;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import smile.tensor.EVD;
import smile.tensor.Vector;
import smile.util.IntSet;
import smile.util.Strings;

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
public class LDA extends AbstractClassifier<double[]> {
    @Serial
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
     * The constant term of discriminant function of each class.
     */
    private final double[] logppriori;
    /**
     * The a priori probabilities of each class.
     */
    private final double[] priori;
    /**
     * THe mean vectors of each class.
     */
    private final double[][] mu;
    /**
     * The eigen values of common variance matrix.
     */
    private final Vector eigen;
    /**
     * The eigen vectors of common covariance matrix, which transforms observations
     * to discriminant functions, normalized so that common covariance
     * matrix is spherical.
     */
    private final DenseMatrix scaling;

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of common variance matrix.
     * @param scaling the eigen vectors of common covariance matrix.
     */
    public LDA(double[] priori, double[][] mu, Vector eigen, DenseMatrix scaling) {
        this(priori, mu, eigen, scaling, IntSet.of(priori.length));
    }

    /**
     * Constructor.
     * @param priori a priori probabilities of each class.
     * @param mu the mean vectors of each class.
     * @param eigen the eigen values of common variance matrix.
     * @param scaling the eigen vectors of common covariance matrix.
     * @param labels the class label encoder.
     */
    public LDA(double[] priori, double[][] mu, Vector eigen, DenseMatrix scaling, IntSet labels) {
        super(labels);
        this.k = priori.length;
        this.p = mu[0].length;
        this.priori = priori;
        this.mu = mu;
        this.eigen = eigen;
        this.scaling = scaling;

        logppriori = new double[k];
        for (int i = 0; i < k; i++) {
            logppriori[i] = Math.log(priori[i]);
        }
    }

    /**
     * Fits linear discriminant analysis.
     * @param x training samples.
     * @param y training labels in [0, k), where k is the number of classes.
     * @return the model.
     */
    public static LDA fit(double[][] x, int[] y) {
        return fit(x, y, null, 1E-4);
    }

    /**
     * Fits linear discriminant analysis.
     * @param x training samples.
     * @param y training labels.
     * @param params the hyperparameters.
     * @return the model.
     */
    public static LDA fit(double[][] x, int[] y, Properties params) {
        double[] priori = Strings.parseDoubleArray(params.getProperty("smile.lda.priori"));
        double tol = Double.parseDouble(params.getProperty("smile.lda.tolerance", "1E-4"));
        return fit(x, y, priori, tol);
    }

    /**
     * Fits linear discriminant analysis.
     * @param x training samples.
     * @param y training labels.
     * @param priori the priori probability of each class. If null, it will be
     * estimated from the training data.
     * @param tol a tolerance to decide if a covariance matrix is singular; it
     * will reject variables whose variance is less than tol<sup>2</sup>.
     * @return the model.
     */
    public static LDA fit(double[][] x, int[] y, double[] priori, double tol) {
        DiscriminantAnalysis da = DiscriminantAnalysis.fit(x, y, priori, tol);
        DenseMatrix St = DiscriminantAnalysis.St(x, da.mean, da.k, tol);
        EVD eigen = St.eigen().sort();

        tol = tol * tol;
        for (int j = 0; j < eigen.wr().size(); j++) {
            if (eigen.wr().get(j) < tol) {
                throw new IllegalArgumentException("The covariance matrix is close to singular.");
            }
        }

        return new LDA(da.priori, da.mu, eigen.wr(), eigen.Vr(), da.labels);
    }

    /**
     * Returns a priori probabilities.
     * @return a priori probabilities.
     */
    public double[] priori() {
        return priori;
    }

    @Override
    public int predict(double[] x) {
        return predict(x, new double[k]);
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    @Override
    public int predict(double[] x, double[] posteriori) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        Vector d = scaling.vector(p);
        Vector ux = scaling.vector(p);

        for (int i = 0; i < k; i++) {
            double[] mean = mu[i];
            for (int j = 0; j < p; j++) {
                d.set(j, x[j] - mean[j]);
            }

            scaling.tv(d, ux);

            double f = 0.0;
            for (int j = 0; j < p; j++) {
                double uxj = ux.get(j);
                f += uxj * uxj / eigen.get(j);
            }

            posteriori[i] = logppriori[i] - 0.5 * f;
        }

        return classes.valueOf(MathEx.softmax(posteriori));
    }
}
